package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class MasteryUpdateEngineMySQLIntegrationTest {

    @Autowired private MasteryUpdateEngine engine;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private StudentLearningEventRepository eventRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private User owner;
    private Notebook notebook;
    private Concept concept;
    private Document document;
    private DocumentChunk chunk;
    private ConceptSource conceptSource;
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            owner = new User();
            owner.setUsername("u_" + suffix);
            owner.setEmail("u_" + suffix + "@ex.com");
            owner.setPassword("hash");
            owner.setFullName("User");
            userRepository.save(owner);

            notebook = new Notebook();
            notebook.setUser(owner);
            notebook.setTitle("NB");
            notebookRepository.save(notebook);

            concept = new Concept();
            concept.setNotebook(notebook);
            concept.setCanonicalName("C");
            concept.setNormalizedName("c");
            concept.setConceptType("CONCEPT");
            conceptRepository.save(concept);

            document = new Document();
            document.setUser(owner);
            document.setNotebook(notebook);
            document.setFileName("f.pdf");
            document.setFilePath("/f.pdf");
            document.setStatus("READY");
            document.setActiveIndexVersion("v1");
            documentRepository.save(document);

            chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setContent("content");
            chunk.setTokenCount(10);
            chunk.setContentHash("hash");
            chunk.setChunkIndex(1);
            chunk.setIndexVersion("v1");
            chunkRepository.save(chunk);

            conceptSource = new ConceptSource();
            conceptSource.setConcept(concept);
            conceptSource.setDocumentChunk(chunk);
            conceptSource.setEvidenceText("evidence");
            conceptSourceRepository.save(conceptSource);
            
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        txTemplate.execute(status -> {
            // Delete engine-created events and mastery first
            eventRepository.deleteAll(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(owner.getId(), concept.getId()));
            masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId()).ifPresent(masteryRepository::delete);
            
            // Delete ConceptSource (child of DocumentChunk) and FLUSH to prevent deferred cascades issues
            conceptSourceRepository.delete(conceptSource);
            conceptSourceRepository.flush();
            
            // Delete DocumentChunk and Document exactly by ID
            chunkRepository.delete(chunk);
            chunkRepository.flush();
            documentRepository.delete(document);
            documentRepository.flush();
            
            // Delete Concept exactly by ID
            conceptRepository.delete(concept);
            conceptRepository.flush();
            
            // Delete Notebook exactly by ID
            notebookRepository.delete(notebook);
            notebookRepository.flush();
            
            // Delete User exactly by ID
            userRepository.delete(owner);
            userRepository.flush();
            
            return null;
        });
    }

    @Test
    void testU_ConcurrentDifferentEvidence() throws Exception {
        MasteryEvidenceCommand cmd1 = new MasteryEvidenceCommand(owner.getId(), notebook.getId(), concept.getId(), 
                "QUIZ", 1.0, "APP", "s1", "key1", LocalDateTime.now());
        MasteryEvidenceCommand cmd2 = new MasteryEvidenceCommand(owner.getId(), notebook.getId(), concept.getId(), 
                "QUIZ", 0.0, "APP", "s2", "key2", LocalDateTime.now());

        CompletableFuture<MasteryUpdateResult> f1 = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmd1));
        CompletableFuture<MasteryUpdateResult> f2 = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmd2));

        CompletableFuture.allOf(f1, f2).join();

        MasteryUpdateResult r1 = f1.get();
        MasteryUpdateResult r2 = f2.get();

        assertThat(r1.applied()).isTrue();
        assertThat(r2.applied()).isTrue();

        StudentConceptMastery mastery = masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId()).orElseThrow();
        assertThat(mastery.getEvidenceCount()).isEqualTo(2);

        // One of them is 1.0 -> 0.70 (0.7), the other is 0.0 -> 0.30 (0.3). The final is either 0.7 or 0.3 depending on lock order!
        assertThat(mastery.getMasteryEstimate()).isIn(0.3, 0.7);
    }

    @Test
    void testV_ConcurrentDuplicateIdempotency() throws Exception {
        MasteryEvidenceCommand cmd = new MasteryEvidenceCommand(owner.getId(), notebook.getId(), concept.getId(), 
                "QUIZ", 1.0, "APP", "s1", "key_dup", LocalDateTime.now());

        CompletableFuture<MasteryUpdateResult> f1 = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmd));
        CompletableFuture<MasteryUpdateResult> f2 = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmd));

        CompletableFuture.allOf(f1, f2).join();

        MasteryUpdateResult r1 = f1.get();
        MasteryUpdateResult r2 = f2.get();

        assertThat(r1.applied() ^ r2.applied()).isTrue(); // Exactly one applied
        
        StudentConceptMastery mastery = masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId()).orElseThrow();
        assertThat(mastery.getEvidenceCount()).isEqualTo(1);
        assertThat(mastery.getMasteryEstimate()).isEqualTo(1.0);
        assertThat(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(owner.getId(), "key_dup", concept.getId())).isPresent();
    }

    /**
     * W — Concurrent cold-start mastery from two DISTINCT evidence events.
     *
     * Scenario: no StudentConceptMastery row exists when both workers begin.
     * Both transactions serialize through the Concept PESSIMISTIC_WRITE lock.
     * The second transaction must observe the mastery row committed by the first
     * via the new PESSIMISTIC_WRITE current-read lookup and take the UPDATE path
     * instead of attempting a duplicate INSERT.
     *
     * Expected invariants:
     *   - both workers commit without exception
     *   - exactly ONE StudentConceptMastery row (no duplicate key violation)
     *   - evidence_count == 2
     *   - mastery_estimate is one of the two valid cross-transaction serializations:
     *       A first then B: 1.0 → EMA(1.0, 0.0) = 0.70
     *       B first then A: 0.0 → EMA(0.0, 1.0) = 0.30
     */
    @Test
    void testW_ConcurrentColdStartDifferentEvidence() throws Exception {
        // Pre-condition: no mastery row exists
        assertThat(masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId())).isEmpty();

        MasteryEvidenceCommand cmdA = new MasteryEvidenceCommand(
                owner.getId(), notebook.getId(), concept.getId(),
                "QUIZ", 1.0, "APP", "srcA", "keyW_A", LocalDateTime.now());

        MasteryEvidenceCommand cmdB = new MasteryEvidenceCommand(
                owner.getId(), notebook.getId(), concept.getId(),
                "QUIZ", 0.0, "APP", "srcB", "keyW_B", LocalDateTime.now());

        CompletableFuture<MasteryUpdateResult> fA = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmdA));
        CompletableFuture<MasteryUpdateResult> fB = CompletableFuture.supplyAsync(() -> engine.applyEvidence(cmdB));

        // Bounded wait — fail the test if concurrency hangs instead of hanging forever
        try {
            CompletableFuture.allOf(fA, fB).get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            org.junit.jupiter.api.Assertions.fail("W: concurrent cold-start workers timed out after 20s");
        } catch (ExecutionException e) {
            org.junit.jupiter.api.Assertions.fail("W: concurrent cold-start worker threw unexpected exception: " + e.getCause());
        }

        // Both workers must have applied successfully
        MasteryUpdateResult rA = fA.get();
        MasteryUpdateResult rB = fB.get();
        assertThat(rA.applied()).isTrue();
        assertThat(rB.applied()).isTrue();

        // Exactly one mastery row
        StudentConceptMastery mastery = masteryRepository
                .findByUserIdAndConceptId(owner.getId(), concept.getId())
                .orElseThrow(() -> new AssertionError("W: StudentConceptMastery row must exist after both workers commit"));
        assertThat(mastery.getEvidenceCount()).isEqualTo(2);

        // Cross-transaction serialization order is non-deterministic.
        // Worker A (score 1.0) first → 1.0 → EMA = 1.0*0.70 + 0.0*0.30 = 0.70
        // Worker B (score 0.0) first → 0.0 → EMA = 0.0*0.70 + 1.0*0.30 = 0.30
        assertThat(mastery.getMasteryEstimate())
                .as("Final EMA must be one of the two valid cross-transaction serializations: 0.30 or 0.70")
                .isIn(0.30, 0.70);

        // Two distinct learning events must exist
        assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(owner.getId(), concept.getId()))
                .hasSize(2);
        assertThat(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(owner.getId(), "keyW_A", concept.getId())).isPresent();
        assertThat(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(owner.getId(), "keyW_B", concept.getId())).isPresent();
    }
}
