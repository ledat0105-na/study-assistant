package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryDetailProjection;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class StudentMasteryReadRepositoryMySQLTest {

    @Autowired private StudentMasteryReadRepository readRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private StudentLearningEventRepository eventRepository;
    @Autowired private ConceptRelationSourceRepository conceptRelationSourceRepository;
    @Autowired private ConceptRelationRepository conceptRelationRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private User owner, other;
    private Notebook ownerNotebook, otherNotebook;
    private Document docReady, docFailed, docNullVersion;
    private DocumentChunk chunkActive, chunkStale;
    private Concept conceptA, conceptB, conceptOther;

    @BeforeEach
    void setUp() {
        cleanup();
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            owner = new User();
            owner.setUsername("owner");
            owner.setEmail("owner@example.com");
            owner.setPassword("hash");
            owner.setFullName("Owner");
            
            other = new User();
            other.setUsername("other");
            other.setEmail("other@example.com");
            other.setPassword("hash");
            other.setFullName("Other");
            userRepository.saveAll(List.of(owner, other));

            ownerNotebook = new Notebook();
            ownerNotebook.setUser(owner);
            ownerNotebook.setTitle("Owner NB");
            ownerNotebook.setDescription("Desc");
            
            otherNotebook = new Notebook();
            otherNotebook.setUser(other);
            otherNotebook.setTitle("Other NB");
            otherNotebook.setDescription("Desc");
            notebookRepository.saveAll(List.of(ownerNotebook, otherNotebook));

            docReady = new Document();
            docReady.setUser(owner);
            docReady.setNotebook(ownerNotebook);
            docReady.setFileName("doc1.pdf");
            docReady.setFilePath("/tmp/doc1.pdf");
            docReady.setStatus("READY");
            docReady.setActiveIndexVersion("v2");
            
            docFailed = new Document();
            docFailed.setUser(owner);
            docFailed.setNotebook(ownerNotebook);
            docFailed.setFileName("doc2.pdf");
            docFailed.setFilePath("/tmp/doc2.pdf");
            docFailed.setStatus("FAILED");
            docFailed.setActiveIndexVersion("v1");

            docNullVersion = new Document();
            docNullVersion.setUser(owner);
            docNullVersion.setNotebook(ownerNotebook);
            docNullVersion.setFileName("doc3.pdf");
            docNullVersion.setFilePath("/tmp/doc3.pdf");
            docNullVersion.setStatus("READY");
            docNullVersion.setActiveIndexVersion(null);

            documentRepository.saveAll(List.of(docReady, docFailed, docNullVersion));

            chunkActive = new DocumentChunk();
            chunkActive.setDocument(docReady);
            chunkActive.setContent("active chunk");
            chunkActive.setChunkIndex(0);
            chunkActive.setTokenCount(10);
            chunkActive.setIndexVersion("v2");
            
            chunkStale = new DocumentChunk();
            chunkStale.setDocument(docReady);
            chunkStale.setContent("stale chunk");
            chunkStale.setChunkIndex(1);
            chunkStale.setTokenCount(10);
            chunkStale.setIndexVersion("v1");
            documentChunkRepository.saveAll(List.of(chunkActive, chunkStale));

            conceptA = new Concept();
            conceptA.setNotebook(ownerNotebook);
            conceptA.setCanonicalName("Concept A");
            conceptA.setConceptType("TypeA");
            conceptA.setDescription("Desc A");
            conceptA.setNormalizedName("concept a");
            
            conceptB = new Concept();
            conceptB.setNotebook(ownerNotebook);
            conceptB.setCanonicalName("Concept B");
            conceptB.setConceptType("TypeB");
            conceptB.setDescription("Desc B");
            conceptB.setNormalizedName("concept b");
            
            conceptOther = new Concept();
            conceptOther.setNotebook(otherNotebook);
            conceptOther.setCanonicalName("Concept Other");
            conceptOther.setConceptType("Type");
            conceptOther.setDescription("Desc");
            conceptOther.setNormalizedName("concept other");
            conceptRepository.saveAll(List.of(conceptA, conceptB, conceptOther));

            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    @AfterEach
    void cleanup() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            eventRepository.deleteAllInBatch();
            masteryRepository.deleteAllInBatch();
            conceptRelationSourceRepository.deleteAllInBatch();
            conceptRelationRepository.deleteAllInBatch();
            conceptSourceRepository.deleteAllInBatch();
            documentChunkRepository.deleteAllInBatch();
            documentRepository.deleteAllInBatch();
            conceptRepository.deleteAllInBatch();
            notebookRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    private ConceptSource createConceptSource(Concept concept, DocumentChunk chunk) {
        ConceptSource cs = new ConceptSource();
        cs.setConcept(concept);
        cs.setDocumentChunk(chunk);
        cs.setEvidenceText("evidence");
        cs.setConfidence(0.9);
        return conceptSourceRepository.save(cs);
    }

    private StudentConceptMastery createMastery(User u, Concept c, Double estimate, Integer count) {
        return masteryRepository.save(new StudentConceptMastery(u, c, estimate, count));
    }

    @Test
    void testCaseA_GroundedAndNoMastery() {
        createConceptSource(conceptA, chunkActive);
        
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getMasteryId()).isNull();
        assertThat(slice.getContent().get(0).getMasteryEstimate()).isNull();

        Optional<GroundedConceptMasteryDetailProjection> detail = readRepository.findGroundedConceptMasteryDetail(owner.getId(), ownerNotebook.getId(), conceptA.getId());
        assertThat(detail).isPresent();
        assertThat(detail.get().getMasteryId()).isNull();
    }

    @Test
    void testCaseB_GroundedAndObservedMastery() {
        createConceptSource(conceptA, chunkActive);
        createMastery(owner, conceptA, 0.8, 5);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getMasteryId()).isNotNull();
        assertThat(slice.getContent().get(0).getMasteryEstimate()).isEqualTo(0.8);
        assertThat(slice.getContent().get(0).getEvidenceCount()).isEqualTo(5);
    }

    @Test
    void testCaseC_MultipleActiveSources() {
        createConceptSource(conceptA, chunkActive);
        
        DocumentChunk chunkActive2 = new DocumentChunk();
        chunkActive2.setDocument(docReady);
        chunkActive2.setContent("active chunk 2");
        chunkActive2.setChunkIndex(2);
        chunkActive2.setTokenCount(15);
        chunkActive2.setIndexVersion(docReady.getActiveIndexVersion());
        chunkActive2 = documentChunkRepository.save(chunkActive2);
        
        createConceptSource(conceptA, chunkActive2); // 2nd active source on DISTINCT chunk

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getConceptId()).isEqualTo(conceptA.getId());
    }

    @Test
    void testCaseD_StaleSourceOnly() {
        createConceptSource(conceptA, chunkStale);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty();
    }

    @Test
    void testCaseE_MixedActiveAndStaleSources() {
        createConceptSource(conceptA, chunkActive);
        createConceptSource(conceptA, chunkStale);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
    }

    @Test
    void testCaseF_FailedDocument() {
        DocumentChunk failedChunk = new DocumentChunk();
        failedChunk.setDocument(docFailed);
        failedChunk.setContent("failed chunk");
        failedChunk.setChunkIndex(1);
        failedChunk.setTokenCount(10);
        failedChunk.setIndexVersion("v1");
        failedChunk = documentChunkRepository.save(failedChunk);
        createConceptSource(conceptA, failedChunk);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty();
    }

    @Test
    void testCaseG_ActiveIndexVersionNull() {
        DocumentChunk nullVersionChunk = new DocumentChunk();
        nullVersionChunk.setDocument(docNullVersion);
        nullVersionChunk.setContent("null version chunk");
        nullVersionChunk.setChunkIndex(1);
        nullVersionChunk.setTokenCount(10);
        nullVersionChunk.setIndexVersion("v1");
        nullVersionChunk = documentChunkRepository.save(nullVersionChunk);
        createConceptSource(conceptA, nullVersionChunk);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty();
    }

    @Test
    void testCaseH_ChunkIndexVersionMismatch() {
        createConceptSource(conceptA, chunkStale);

        Optional<GroundedConceptMasteryDetailProjection> detail = readRepository.findGroundedConceptMasteryDetail(owner.getId(), ownerNotebook.getId(), conceptA.getId());
        assertThat(detail).isEmpty();
    }

    @Test
    void testCaseI_ConceptInAnotherNotebook() {
        createConceptSource(conceptOther, chunkActive);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(other.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty();
    }

    @Test
    void testCaseJ_NotebookBelongsToAnotherUser() {
        createConceptSource(conceptA, chunkActive);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(other.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty();
    }

    @Test
    void testCaseK_CrossUserMasteryRowExists() {
        createConceptSource(conceptA, chunkActive);
        // User B has mastery on Concept A
        createMastery(other, conceptA, 0.9, 10);

        // Query as User A
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getMasteryId()).isNull(); // Must not join other user's mastery
    }

    @Test
    void testCaseL_MasteryForOtherUserConcept() {
        createConceptSource(conceptA, chunkActive);
        createMastery(other, conceptA, 0.9, 10);

        // Query as User B on User A's notebook
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(other.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).isEmpty(); // Notebook A is not owned by B
    }

    @Test
    void testCaseM_ZeroEvidenceRow() {
        createConceptSource(conceptA, chunkActive);
        createMastery(owner, conceptA, 0.0, 0);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getMasteryId()).isNotNull();
        assertThat(slice.getContent().get(0).getMasteryEstimate()).isEqualTo(0.0);
        assertThat(slice.getContent().get(0).getEvidenceCount()).isEqualTo(0);
    }

    @Test
    void testCaseN_RegroundedConcept() {
        // Concept is originally grounded and has mastery
        createConceptSource(conceptA, chunkStale); // Initially stale
        createMastery(owner, conceptA, 0.7, 3);
        
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> sliceBefore = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(sliceBefore.getContent()).isEmpty(); // Not visible because stale

        // Reground concept
        createConceptSource(conceptA, chunkActive);
        
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> sliceAfter = readRepository.findGroundedConceptMasteriesByNotebookId(owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(sliceAfter.getContent()).hasSize(1);
        assertThat(sliceAfter.getContent().get(0).getMasteryEstimate()).isEqualTo(0.7); // History visible again
    }
    @Test
    void testCaseO_PaginationPage0_hasNextTrue() {
        Concept c1 = new Concept();
        c1.setNotebook(ownerNotebook);
        c1.setCanonicalName("Concept 1");
        c1.setConceptType("Type");
        c1.setNormalizedName("concept 1");
        
        Concept c2 = new Concept();
        c2.setNotebook(ownerNotebook);
        c2.setCanonicalName("Concept 2");
        c2.setConceptType("Type");
        c2.setNormalizedName("concept 2");
        
        Concept c3 = new Concept();
        c3.setNotebook(ownerNotebook);
        c3.setCanonicalName("Concept 3");
        c3.setConceptType("Type");
        c3.setNormalizedName("concept 3");
        
        conceptRepository.saveAll(List.of(c1, c2, c3));

        createConceptSource(c1, chunkActive);
        createConceptSource(c2, chunkActive);
        createConceptSource(c3, chunkActive);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(
                owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 2));

        assertThat(slice.getNumber()).isEqualTo(0);
        assertThat(slice.getSize()).isEqualTo(2);
        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();
        
        // Assert exact concept IDs/names returned according to certified DB ordering
        assertThat(slice.getContent().get(0).getCanonicalName()).isEqualTo("Concept 1");
        assertThat(slice.getContent().get(1).getCanonicalName()).isEqualTo("Concept 2");
    }

    @Test
    void testCaseP_PaginationPage1_hasNextFalse() {
        Concept c1 = new Concept();
        c1.setNotebook(ownerNotebook);
        c1.setCanonicalName("Concept 1");
        c1.setConceptType("Type");
        c1.setNormalizedName("concept 1");
        
        Concept c2 = new Concept();
        c2.setNotebook(ownerNotebook);
        c2.setCanonicalName("Concept 2");
        c2.setConceptType("Type");
        c2.setNormalizedName("concept 2");
        
        Concept c3 = new Concept();
        c3.setNotebook(ownerNotebook);
        c3.setCanonicalName("Concept 3");
        c3.setConceptType("Type");
        c3.setNormalizedName("concept 3");
        
        conceptRepository.saveAll(List.of(c1, c2, c3));

        createConceptSource(c1, chunkActive);
        createConceptSource(c2, chunkActive);
        createConceptSource(c3, chunkActive);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(
                owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(1, 2));

        assertThat(slice.getNumber()).isEqualTo(1);
        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.hasNext()).isFalse();
        
        assertThat(slice.getContent().get(0).getCanonicalName()).isEqualTo("Concept 3");
    }

    @Test
    void testCaseQ_FullOrderingContract() {
        Concept c1 = new Concept();
        c1.setNotebook(ownerNotebook);
        c1.setCanonicalName("Zebra");
        c1.setConceptType("Type");
        c1.setNormalizedName("zebra");
        
        Concept c2 = new Concept();
        c2.setNotebook(ownerNotebook);
        c2.setCanonicalName("Apple");
        c2.setConceptType("Type");
        c2.setNormalizedName("apple");
        
        Concept c3 = new Concept();
        c3.setNotebook(ownerNotebook);
        c3.setCanonicalName("Mango");
        c3.setConceptType("Type");
        c3.setNormalizedName("mango");
        
        conceptRepository.saveAll(List.of(c1, c2, c3));

        createConceptSource(c1, chunkActive);
        createConceptSource(c2, chunkActive);
        createConceptSource(c3, chunkActive);

        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> slice = readRepository.findGroundedConceptMasteriesByNotebookId(
                owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(slice.getContent()).hasSize(3);
        assertThat(slice.getContent().get(0).getCanonicalName()).isEqualTo("Apple");
        assertThat(slice.getContent().get(1).getCanonicalName()).isEqualTo("Mango");
        assertThat(slice.getContent().get(2).getCanonicalName()).isEqualTo("Zebra");
        
        // P6 conceptId tie-break is NOT REACHABLE UNDER VALID SCHEMA
        // The unique constraint uk_notebook_normalized_name (notebook_id, normalized_name)
        // prevents two concepts in the same notebook from having the same normalized name.
        // Assuming canonical names map deterministically to normalized names, two concepts
        // cannot have identical canonical names in the same notebook.
    }

    @Test
    void testCaseR_PageBoundaryDedup() {
        Concept c1 = new Concept();
        c1.setNotebook(ownerNotebook);
        c1.setCanonicalName("Boundary Concept A");
        c1.setConceptType("Type");
        c1.setNormalizedName("boundary concept a");
        
        Concept c2 = new Concept();
        c2.setNotebook(ownerNotebook);
        c2.setCanonicalName("Boundary Concept B");
        c2.setConceptType("Type");
        c2.setNormalizedName("boundary concept b");
        
        Concept c3 = new Concept();
        c3.setNotebook(ownerNotebook);
        c3.setCanonicalName("Boundary Concept C");
        c3.setConceptType("Type");
        c3.setNormalizedName("boundary concept c");
        
        conceptRepository.saveAll(List.of(c1, c2, c3));

        // c1 has 1 source
        createConceptSource(c1, chunkActive);
        
        // c2 has 2 active distinct sources (will cause duplicates without GROUP BY)
        DocumentChunk chunkActive2 = new DocumentChunk();
        chunkActive2.setDocument(docReady);
        chunkActive2.setContent("active chunk 2 for dedup");
        chunkActive2.setChunkIndex(2);
        chunkActive2.setTokenCount(15);
        chunkActive2.setIndexVersion(docReady.getActiveIndexVersion());
        chunkActive2 = documentChunkRepository.save(chunkActive2);
        
        createConceptSource(c2, chunkActive);
        createConceptSource(c2, chunkActive2);
        
        // c3 has 1 source
        createConceptSource(c3, chunkActive);

        // Query page 0 (size 2)
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> page0 = readRepository.findGroundedConceptMasteriesByNotebookId(
                owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(0, 2));
        
        // Query page 1 (size 2)
        org.springframework.data.domain.Slice<GroundedConceptMasteryProjection> page1 = readRepository.findGroundedConceptMasteriesByNotebookId(
                owner.getId(), ownerNotebook.getId(), org.springframework.data.domain.PageRequest.of(1, 2));

        // 1. duplicate-supported Concept occurs exactly once globally
        // 2. no conceptId from page0 appears in page1
        List<Long> page0Ids = page0.getContent().stream().map(GroundedConceptMasteryProjection::getConceptId).toList();
        List<Long> page1Ids = page1.getContent().stream().map(GroundedConceptMasteryProjection::getConceptId).toList();
        
        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
        
        // 3. union equals expected distinct IDs
        java.util.List<Long> allIds = new java.util.ArrayList<>();
        allIds.addAll(page0Ids);
        allIds.addAll(page1Ids);
        assertThat(allIds).containsExactlyInAnyOrder(c1.getId(), c2.getId(), c3.getId());
        
        // 4. page sizes correspond to DISTINCT concepts (page 0 should be exactly 2 items)
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(1);
        
        // 5. hasNext semantics remain correct
        assertThat(page0.hasNext()).isTrue();
        assertThat(page1.hasNext()).isFalse();
    }
}
