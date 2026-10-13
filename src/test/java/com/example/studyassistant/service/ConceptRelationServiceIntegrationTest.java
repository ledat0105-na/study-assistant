package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("Integration")
public class ConceptRelationServiceIntegrationTest {

    @Autowired
    private ConceptRelationService conceptRelationService;

    @Autowired
    private ConceptRelationRepository conceptRelationRepository;

    @Autowired
    private ConceptRelationSourceRepository conceptRelationSourceRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private Notebook testNotebook;
    private Document testDoc;
    private DocumentChunk testChunk1;
    private DocumentChunk testChunk2;
    private Concept conceptA;
    private Concept conceptB;
    private Concept conceptC;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            
            testUser = new User();
            testUser.setUsername("user_" + suffix);
            testUser.setEmail("user_" + suffix + "@example.com");
            testUser.setPassword("hash");
            userRepository.saveAndFlush(testUser);

            testNotebook = new Notebook();
            testNotebook.setTitle("Notebook " + suffix);
            testNotebook.setUser(testUser);
            notebookRepository.saveAndFlush(testNotebook);

            testDoc = new Document();
            testDoc.setUser(testUser);
            testDoc.setNotebook(testNotebook);
            testDoc.setFileName("doc_" + suffix + ".txt");
            testDoc.setFilePath("/tmp/" + suffix);
            documentRepository.saveAndFlush(testDoc);

            testChunk1 = new DocumentChunk();
            testChunk1.setDocument(testDoc);
            testChunk1.setChunkIndex(0);
            testChunk1.setContent("Chunk 1");
            testChunk1.setTokenCount(10);
            testChunk1.setIndexVersion("v1");
            documentChunkRepository.saveAndFlush(testChunk1);

            testChunk2 = new DocumentChunk();
            testChunk2.setDocument(testDoc);
            testChunk2.setChunkIndex(1);
            testChunk2.setContent("Chunk 2");
            testChunk2.setTokenCount(10);
            testChunk2.setIndexVersion("v1");
            documentChunkRepository.saveAndFlush(testChunk2);

            conceptA = new Concept();
            conceptA.setNotebook(testNotebook);
            conceptA.setCanonicalName("ConceptA_" + suffix);
            conceptA.setNormalizedName("concepta_" + suffix);
            conceptRepository.saveAndFlush(conceptA);

            conceptB = new Concept();
            conceptB.setNotebook(testNotebook);
            conceptB.setCanonicalName("ConceptB_" + suffix);
            conceptB.setNormalizedName("conceptb_" + suffix);
            conceptRepository.saveAndFlush(conceptB);

            conceptC = new Concept();
            conceptC.setNotebook(testNotebook);
            conceptC.setCanonicalName("ConceptC_" + suffix);
            conceptC.setNormalizedName("conceptc_" + suffix);
            conceptRepository.saveAndFlush(conceptC);

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        Set<Long> relationIds = new HashSet<>();
        
        if (conceptA != null && conceptA.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptA.getId()).forEach(r -> relationIds.add(r.getId()));
        }
        if (conceptB != null && conceptB.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptB.getId()).forEach(r -> relationIds.add(r.getId()));
        }
        if (conceptC != null && conceptC.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptC.getId()).forEach(r -> relationIds.add(r.getId()));
        }

        if (!relationIds.isEmpty()) {
            List<ConceptRelationSource> sourcesToDelete = new ArrayList<>();
            for (Long relId : relationIds) {
                sourcesToDelete.addAll(conceptRelationSourceRepository.findByConceptRelationId(relId));
            }
            if (!sourcesToDelete.isEmpty()) {
                conceptRelationSourceRepository.deleteAllInBatch(sourcesToDelete);
            }
            
            List<ConceptRelation> relationsToDelete = new ArrayList<>();
            for (Long relId : relationIds) {
                conceptRelationRepository.findById(relId).ifPresent(relationsToDelete::add);
            }
            if (!relationsToDelete.isEmpty()) {
                conceptRelationRepository.deleteAllInBatch(relationsToDelete);
            }
        }

        if (conceptC != null && conceptC.getId() != null) conceptRepository.delete(conceptC);
        if (conceptB != null && conceptB.getId() != null) conceptRepository.delete(conceptB);
        if (conceptA != null && conceptA.getId() != null) conceptRepository.delete(conceptA);
        if (testChunk2 != null && testChunk2.getId() != null) documentChunkRepository.delete(testChunk2);
        if (testChunk1 != null && testChunk1.getId() != null) documentChunkRepository.delete(testChunk1);
        if (testDoc != null && testDoc.getId() != null) documentRepository.delete(testDoc);
        if (testNotebook != null && testNotebook.getId() != null) notebookRepository.delete(testNotebook);
        if (testUser != null && testUser.getId() != null) userRepository.delete(testUser);
    }

    @Test
    void test1_newRelationAndSource() {
        ConceptRelationSource src = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence 1", 0.9);

        assertThat(src).isNotNull();
        assertThat(src.getEvidenceText()).isEqualTo("evidence 1");
        assertThat(src.getConfidence()).isEqualTo(0.9);

        ConceptRelation rel = src.getConceptRelation();
        assertThat(rel).isNotNull();
        assertThat(rel.getSourceConcept().getId()).isEqualTo(conceptA.getId());
        assertThat(rel.getTargetConcept().getId()).isEqualTo(conceptB.getId());
        assertThat(rel.getRelationType()).isEqualTo(RelationType.PREREQUISITE_OF);
        assertThat(rel.getConfidence()).isEqualTo(0.8);
    }

    @Test
    void test2_forcedSourceFailureRollsBackRelation() {
        assertThatThrownBy(() -> 
            conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, null, 0.9)
        ).isInstanceOf(DataIntegrityViolationException.class);

        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptA.getId());
        assertThat(rels).isEmpty();
    }

    @Test
    void test3_existingRelationUnchangedAndNewSourceCreated() {
        ConceptRelationSource src1 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence 1", 0.9);

        ConceptRelationSource src2 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.5, testChunk2, "evidence 2", 0.7);

        assertThat(src1.getConceptRelation().getId()).isEqualTo(src2.getConceptRelation().getId());
        
        ConceptRelation rel = conceptRelationRepository.findById(src1.getConceptRelation().getId()).orElseThrow(RuntimeException::new);
        assertThat(rel.getConfidence()).isEqualTo(0.8); // Unchanged

        assertThat(src2.getConfidence()).isEqualTo(0.7);
    }

    @Test
    void test4_idempotentSameRelationAndChunk() {
        ConceptRelationSource src1 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence 1", 0.9);

        ConceptRelationSource src2 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence 1", 0.9);

        assertThat(src1.getId()).isEqualTo(src2.getId());
        assertThat(src1.getConceptRelation().getId()).isEqualTo(src2.getConceptRelation().getId());

        assertThat(conceptRelationRepository.findBySourceConceptId(conceptA.getId())).hasSize(1);
    }

    @Test
    void test5_concurrentSameRelationSameChunk() throws Exception {
        int workers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Callable<ConceptRelationSource>> tasks = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            tasks.add(() -> {
                barrier.await();
                return conceptRelationService.getOrCreateWithSource(
                        conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence", 0.9);
            });
        }

        List<Future<ConceptRelationSource>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Long firstId = null;
        Long firstRelId = null;
        for (Future<ConceptRelationSource> f : futures) {
            ConceptRelationSource src = f.get(); // Should not throw
            if (firstId == null) {
                firstId = src.getId();
                firstRelId = src.getConceptRelation().getId();
            } else {
                assertThat(src.getId()).isEqualTo(firstId);
                assertThat(src.getConceptRelation().getId()).isEqualTo(firstRelId);
            }
        }

        assertThat(conceptRelationRepository.findBySourceConceptId(conceptA.getId())).hasSize(1);
    }

    @Test
    void test6_concurrentSameRelationDifferentChunks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<ConceptRelationSource> task1 = () -> {
            barrier.await();
            return conceptRelationService.getOrCreateWithSource(
                    conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "evidence 1", 0.9);
        };

        Callable<ConceptRelationSource> task2 = () -> {
            barrier.await();
            return conceptRelationService.getOrCreateWithSource(
                    conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk2, "evidence 2", 0.9);
        };

        Future<ConceptRelationSource> f1 = executor.submit(task1);
        Future<ConceptRelationSource> f2 = executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        ConceptRelationSource s1 = f1.get();
        ConceptRelationSource s2 = f2.get();

        assertThat(s1.getId()).isNotEqualTo(s2.getId());
        assertThat(s1.getConceptRelation().getId()).isEqualTo(s2.getConceptRelation().getId());
    }

    @Test
    void test7_sameEndpointsDifferentRelationTypes() {
        ConceptRelationSource s1 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.9);

        ConceptRelationSource s2 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.RELATED_TO, 0.8, testChunk1, "ev2", 0.9);

        assertThat(s1.getConceptRelation().getId()).isNotEqualTo(s2.getConceptRelation().getId());
        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptA.getId());
        assertThat(rels).hasSize(2);
    }

    @Test
    void test8_reverseDirectionDistinct() {
        ConceptRelationSource s1 = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.9);

        ConceptRelationSource s2 = conceptRelationService.getOrCreateWithSource(
                conceptB, conceptA, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev2", 0.9);

        assertThat(s1.getConceptRelation().getId()).isNotEqualTo(s2.getConceptRelation().getId());
        assertThat(conceptRelationRepository.findBySourceConceptId(conceptA.getId())).hasSize(1);
        assertThat(conceptRelationRepository.findBySourceConceptId(conceptB.getId())).hasSize(1);
    }

    @Test
    void test9_unrelatedIntegrityExceptionNotSwallowed() {
        assertThatThrownBy(() -> 
            conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, null, 0.9)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void test10_outerTransactionRemainsUsable() {
        transactionTemplate.execute(status -> {
            conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.9);
            conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.9); 
            
            conceptC.setCanonicalName("UpdatedConceptC");
            conceptRepository.saveAndFlush(conceptC);
            return null;
        });

        Concept c = conceptRepository.findById(conceptC.getId()).orElseThrow(RuntimeException::new);
        assertThat(c.getCanonicalName()).isEqualTo("UpdatedConceptC");
    }

    @Test
    void test11_existingRelationConfidenceUnchanged() {
        conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.9);
        conceptRelationService.getOrCreateWithSource(conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.3, testChunk2, "ev2", 0.9);
        
        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptA.getId());
        assertThat(rels).hasSize(1);
        assertThat(rels.get(0).getConfidence()).isEqualTo(0.8);
    }

    @Test
    void test12_sourceConfidenceIndependent() {
        ConceptRelationSource src = conceptRelationService.getOrCreateWithSource(
                conceptA, conceptB, RelationType.PREREQUISITE_OF, 0.8, testChunk1, "ev1", 0.95);
        
        assertThat(src.getConfidence()).isEqualTo(0.95);
        assertThat(src.getConceptRelation().getConfidence()).isEqualTo(0.8);
    }
}
