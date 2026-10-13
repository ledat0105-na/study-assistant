package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Tag;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Tag("Integration")
public class ConceptRelationSourceRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ConceptRelationSourceRepository repository;
    
    @Autowired
    private ConceptRelationRepository relationRepository;

    private User user;
    private Notebook notebook;
    private Document document;
    private DocumentChunk chunk1;
    private DocumentChunk chunk2;
    private Concept conceptA;
    private Concept conceptB;
    private ConceptRelation relationAB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        user = new User();
        user.setUsername("testuser_" + suffix);
        user.setEmail("test_" + suffix + "@example.com");
        user.setPassword("pass");
        entityManager.persist(user); entityManager.flush();

        notebook = new Notebook();
        notebook.setTitle("Test Notebook");
        notebook.setUser(user);
        entityManager.persist(notebook); entityManager.flush();

        document = new Document();
        document.setUser(user);
        document.setNotebook(notebook);
        document.setFileName("doc.pdf");
        document.setFilePath("/tmp/doc_" + suffix + ".pdf");
        document.setFileSize(100L);
        document.setStatus("INDEXED");
        document.setActiveIndexVersion("v1");
        entityManager.persist(document); entityManager.flush();

        chunk1 = new DocumentChunk();
        chunk1.setDocument(document);
        chunk1.setChunkIndex(0);
        chunk1.setContent("Chunk 1 text");
        chunk1.setTokenCount(10);
        chunk1.setIndexVersion("v1");
        entityManager.persist(chunk1); entityManager.flush();

        chunk2 = new DocumentChunk();
        chunk2.setDocument(document);
        chunk2.setChunkIndex(1);
        chunk2.setContent("Chunk 2 text");
        chunk2.setTokenCount(10);
        chunk2.setIndexVersion("v1");
        entityManager.persist(chunk2); entityManager.flush();

        conceptA = new Concept();
        conceptA.setNotebook(notebook);
        conceptA.setCanonicalName("Concept A");
        conceptA.setNormalizedName("concept a");
        entityManager.persist(conceptA); entityManager.flush();

        conceptB = new Concept();
        conceptB.setNotebook(notebook);
        conceptB.setCanonicalName("Concept B");
        conceptB.setNormalizedName("concept b");
        entityManager.persist(conceptB); entityManager.flush();

        relationAB = new ConceptRelation();
        relationAB.setSourceConcept(conceptA);
        relationAB.setTargetConcept(conceptB);
        relationAB.setRelationType(RelationType.PREREQUISITE_OF);
        entityManager.persist(relationAB); entityManager.flush();
    }

    @Test
    void testA_SaveSuccessfully() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("A is prerequisite of B");
        src.setConfidence(0.95);
        
        src = repository.saveAndFlush(src);
        assertThat(src.getId()).isNotNull();
    }

    @Test
    void testB_FindByRelationId() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("A -> B");
        repository.saveAndFlush(src);

        List<ConceptRelationSource> found = repository.findByConceptRelationId(relationAB.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEvidenceText()).isEqualTo("A -> B");
    }

    @Test
    void testC_FindByChunkId() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("A -> B");
        repository.saveAndFlush(src);

        List<ConceptRelationSource> found = repository.findByDocumentChunkId(chunk1.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDocumentChunk().getId()).isEqualTo(chunk1.getId());
    }

    @Test
    void testD_FindExactRelationAndChunk() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("A -> B");
        repository.saveAndFlush(src);

        Optional<ConceptRelationSource> found = repository.findByConceptRelationIdAndDocumentChunkId(relationAB.getId(), chunk1.getId());
        assertThat(found).isPresent();
        assertThat(repository.existsByConceptRelationIdAndDocumentChunkId(relationAB.getId(), chunk1.getId())).isTrue();
    }

    @Test
    void testE_DuplicateRelationChunkRejected() {
        ConceptRelationSource src1 = new ConceptRelationSource();
        src1.setConceptRelation(relationAB);
        src1.setDocumentChunk(chunk1);
        src1.setEvidenceText("Ev 1");
        repository.saveAndFlush(src1);

        ConceptRelationSource src2 = new ConceptRelationSource();
        src2.setConceptRelation(relationAB);
        src2.setDocumentChunk(chunk1);
        src2.setEvidenceText("Ev 2");

        assertThatThrownBy(() -> repository.saveAndFlush(src2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testF_SameRelationDifferentChunksAllowed() {
        ConceptRelationSource src1 = new ConceptRelationSource();
        src1.setConceptRelation(relationAB);
        src1.setDocumentChunk(chunk1);
        src1.setEvidenceText("Ev 1");
        repository.saveAndFlush(src1);

        ConceptRelationSource src2 = new ConceptRelationSource();
        src2.setConceptRelation(relationAB);
        src2.setDocumentChunk(chunk2);
        src2.setEvidenceText("Ev 2");
        repository.saveAndFlush(src2);

        List<ConceptRelationSource> found = repository.findByConceptRelationId(relationAB.getId());
        assertThat(found).hasSize(2);
    }

    @Test
    void testG_DifferentRelationsSameChunkAllowed() {
        ConceptRelation relationBA = new ConceptRelation();
        relationBA.setSourceConcept(conceptB);
        relationBA.setTargetConcept(conceptA);
        relationBA.setRelationType(RelationType.RELATED_TO);
        entityManager.persist(relationBA); entityManager.flush();

        ConceptRelationSource src1 = new ConceptRelationSource();
        src1.setConceptRelation(relationAB);
        src1.setDocumentChunk(chunk1);
        src1.setEvidenceText("Ev 1");
        repository.saveAndFlush(src1);

        ConceptRelationSource src2 = new ConceptRelationSource();
        src2.setConceptRelation(relationBA);
        src2.setDocumentChunk(chunk1);
        src2.setEvidenceText("Ev 2");
        repository.saveAndFlush(src2);

        List<ConceptRelationSource> found = repository.findByDocumentChunkId(chunk1.getId());
        assertThat(found).hasSize(2);
    }

    @Test
    void testH_RelationDeletionCascadesEvidenceDeletion() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("Ev 1");
        src = repository.saveAndFlush(src);

        entityManager.clear();

        ConceptRelation rel = entityManager.find(ConceptRelation.class, relationAB.getId());
        entityManager.remove(rel);
        entityManager.flush();

        Optional<ConceptRelationSource> foundSrc = repository.findById(src.getId());
        assertThat(foundSrc).isEmpty();
    }

    @Test
    void testI_ChunkDeletionCascadesEvidenceDeletionWhileRelationRemains() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("Ev 1");
        src = repository.saveAndFlush(src);

        entityManager.clear();

        DocumentChunk chk = entityManager.find(DocumentChunk.class, chunk1.getId());
        entityManager.remove(chk);
        entityManager.flush();

        Optional<ConceptRelationSource> foundSrc = repository.findById(src.getId());
        assertThat(foundSrc).isEmpty();

        // Relation remains
        Optional<ConceptRelation> rel = relationRepository.findById(relationAB.getId());
        assertThat(rel).isPresent();
    }

    @Test
    void testJ_LazyAssociationBehavior() {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relationAB);
        src.setDocumentChunk(chunk1);
        src.setEvidenceText("Ev 1");
        src = repository.saveAndFlush(src);

        entityManager.clear();

        ConceptRelationSource foundSrc = repository.findById(src.getId()).get();
        // Accessing LAZY associations works within the test transaction context
        assertThat(foundSrc.getConceptRelation().getId()).isEqualTo(relationAB.getId());
        assertThat(foundSrc.getDocumentChunk().getId()).isEqualTo(chunk1.getId());
    }
}
