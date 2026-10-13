package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Tag("Integration")
public class ConceptRepositoryTest {

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private ConceptSourceRepository conceptSourceRepository;

    @Autowired
    private ConceptRelationRepository conceptRelationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;
    private Notebook notebook;
    private Document document;
    private DocumentChunk chunk;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpass");
        user = userRepository.save(user);

        notebook = new Notebook();
        notebook.setTitle("Test Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.save(notebook);

        document = new Document();
        document.setUser(user);
        document.setNotebook(notebook);
        document.setFileName("test.pdf");
        document.setFilePath("/path/to/test.pdf");
        document = documentRepository.save(document);

        chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(1);
        chunk.setContent("Test content");
        chunk.setTokenCount(10);
        chunk.setIndexVersion("v1");
        chunk = documentChunkRepository.save(chunk);
    }

    @Test
    void testSaveAndFindConcept() {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Machine Learning");
        concept.setNormalizedName("machine learning");
        concept.setConceptType("TOPIC");
        
        Concept saved = conceptRepository.saveAndFlush(concept);
        assertThat(saved.getId()).isNotNull();
        
        entityManager.clear();

        List<Concept> byNotebook = conceptRepository.findByNotebookId(notebook.getId());
        assertThat(byNotebook).hasSize(1);
        
        Optional<Concept> byNormalized = conceptRepository.findByNotebookIdAndNormalizedName(notebook.getId(), "machine learning");
        assertThat(byNormalized).isPresent();
    }

    @Test
    void testCrossNotebookSameNormalizedNameAllowed() {
        Concept concept1 = new Concept();
        concept1.setNotebook(notebook);
        concept1.setCanonicalName("Machine Learning");
        concept1.setNormalizedName("machine learning");
        conceptRepository.saveAndFlush(concept1);

        Notebook notebook2 = new Notebook();
        notebook2.setTitle("Notebook 2");
        notebook2.setUser(user);
        notebook2 = notebookRepository.save(notebook2);

        Concept concept2 = new Concept();
        concept2.setNotebook(notebook2);
        concept2.setCanonicalName("ML");
        concept2.setNormalizedName("machine learning");
        conceptRepository.saveAndFlush(concept2);
        
        entityManager.clear();
        
        assertThat(conceptRepository.findByNotebookId(notebook.getId())).hasSize(1);
        assertThat(conceptRepository.findByNotebookId(notebook2.getId())).hasSize(1);
    }

    @Test
    void testConceptUniqueConstraint() {
        Concept concept1 = new Concept();
        concept1.setNotebook(notebook);
        concept1.setCanonicalName("Machine Learning");
        concept1.setNormalizedName("machine learning");
        conceptRepository.saveAndFlush(concept1);

        Concept concept2 = new Concept();
        concept2.setNotebook(notebook);
        concept2.setCanonicalName("ML");
        concept2.setNormalizedName("machine learning"); // duplicate
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            conceptRepository.saveAndFlush(concept2);
        });
    }

    @Test
    void testSaveAndFindConceptSource() {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Machine Learning");
        concept.setNormalizedName("machine learning");
        concept = conceptRepository.save(concept);

        ConceptSource source = new ConceptSource();
        source.setConcept(concept);
        source.setDocumentChunk(chunk);
        source.setEvidenceText("Machine learning is a field of AI");
        source.setConfidence(0.95);
        
        ConceptSource saved = conceptSourceRepository.saveAndFlush(source);
        assertThat(saved.getId()).isNotNull();
        
        entityManager.clear();

        List<ConceptSource> byConcept = conceptSourceRepository.findByConceptId(concept.getId());
        assertThat(byConcept).hasSize(1);

        List<ConceptSource> byChunk = conceptSourceRepository.findByDocumentChunkId(chunk.getId());
        assertThat(byChunk).hasSize(1);
    }

    @Test
    void testConceptSourceUniqueConstraint() {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Machine Learning");
        concept.setNormalizedName("machine learning");
        concept = conceptRepository.save(concept);

        ConceptSource source1 = new ConceptSource();
        source1.setConcept(concept);
        source1.setDocumentChunk(chunk);
        source1.setEvidenceText("Evidence 1");
        conceptSourceRepository.saveAndFlush(source1);

        ConceptSource source2 = new ConceptSource();
        source2.setConcept(concept);
        source2.setDocumentChunk(chunk);
        source2.setEvidenceText("Evidence 2"); // duplicate concept+chunk
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            conceptSourceRepository.saveAndFlush(source2);
        });
    }

    @Test
    void testSaveAndFindConceptRelation() {
        Concept concept1 = new Concept();
        concept1.setNotebook(notebook);
        concept1.setCanonicalName("AI");
        concept1.setNormalizedName("ai");
        concept1 = conceptRepository.save(concept1);

        Concept concept2 = new Concept();
        concept2.setNotebook(notebook);
        concept2.setCanonicalName("Machine Learning");
        concept2.setNormalizedName("machine learning");
        concept2 = conceptRepository.save(concept2);

        ConceptRelation relation = new ConceptRelation();
        relation.setSourceConcept(concept2);
        relation.setTargetConcept(concept1);
        relation.setRelationType(RelationType.IS_A);
        
        ConceptRelation saved = conceptRelationRepository.saveAndFlush(relation);
        assertThat(saved.getId()).isNotNull();
        
        entityManager.clear();

        List<ConceptRelation> outgoing = conceptRelationRepository.findBySourceConceptId(concept2.getId());
        assertThat(outgoing).hasSize(1);

        List<ConceptRelation> incoming = conceptRelationRepository.findByTargetConceptId(concept1.getId());
        assertThat(incoming).hasSize(1);

        List<ConceptRelation> allForConcept2 = conceptRelationRepository.findByConceptId(concept2.getId());
        assertThat(allForConcept2).hasSize(1);
    }

    @Test
    void testSelfRelationRejection() {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("AI");
        concept.setNormalizedName("ai");
        concept = conceptRepository.save(concept);

        ConceptRelation relation = new ConceptRelation();
        relation.setSourceConcept(concept);
        relation.setTargetConcept(concept);
        relation.setRelationType(RelationType.RELATED_TO);
        
        org.springframework.dao.InvalidDataAccessApiUsageException ex = assertThrows(
            org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> {
                conceptRelationRepository.saveAndFlush(relation);
            }
        );
        
        Throwable rootCause = ex.getRootCause();
        if (rootCause == null) {
            rootCause = ex.getCause();
        }
        assertThat(rootCause).isNotNull();
        assertThat(rootCause).isInstanceOf(IllegalStateException.class);
        assertThat(rootCause.getMessage()).isEqualTo("Self-relations are not permitted");
    }

    @Test
    void testConceptRelationUniqueConstraint() {
        Concept concept1 = new Concept();
        concept1.setNotebook(notebook);
        concept1.setCanonicalName("AI");
        concept1.setNormalizedName("ai");
        concept1 = conceptRepository.save(concept1);

        Concept concept2 = new Concept();
        concept2.setNotebook(notebook);
        concept2.setCanonicalName("Machine Learning");
        concept2.setNormalizedName("machine learning");
        concept2 = conceptRepository.save(concept2);

        ConceptRelation relation1 = new ConceptRelation();
        relation1.setSourceConcept(concept2);
        relation1.setTargetConcept(concept1);
        relation1.setRelationType(RelationType.IS_A);
        conceptRelationRepository.saveAndFlush(relation1);

        ConceptRelation relation2 = new ConceptRelation();
        relation2.setSourceConcept(concept2);
        relation2.setTargetConcept(concept1);
        relation2.setRelationType(RelationType.IS_A);
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            conceptRelationRepository.saveAndFlush(relation2);
        });
    }
}
