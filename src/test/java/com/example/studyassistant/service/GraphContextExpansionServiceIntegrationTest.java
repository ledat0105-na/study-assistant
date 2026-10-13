package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.dto.GraphContextDto;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("Integration")
public class GraphContextExpansionServiceIntegrationTest {

    @Autowired
    private GraphContextExpansionService graphContextExpansionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private ConceptRelationRepository conceptRelationRepository;

    @Autowired
    private ConceptRelationSourceRepository conceptRelationSourceRepository;

    private User user;
    private Notebook notebook;
    private Notebook otherNotebook;
    
    private Document docReady;
    private Document docReadyStale;
    private Document docFailed;
    private Document docProcessing;
    private Document docOtherNotebook;
    private Document docNullActive;
    
    private DocumentChunk chunkReady;
    private DocumentChunk chunkReadyStale;
    private DocumentChunk chunkFailed;
    private DocumentChunk chunkProcessing;
    private DocumentChunk chunkOtherNotebook;
    private DocumentChunk chunkNullActive;
    private DocumentChunk chunkReady2;
    
    private Concept conceptA;
    private Concept conceptB;
    private Concept conceptC;
    private Concept conceptOther;
    
    @BeforeEach
    void setup() {
        conceptRelationSourceRepository.deleteAllInBatch();
        conceptRelationRepository.deleteAllInBatch();
        documentChunkRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        conceptRepository.deleteAllInBatch();
        notebookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        user = new User();
        user.setEmail("context@example.com");
        user.setPassword("pass");
        user.setRole("STUDENT");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Notebook A");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        otherNotebook = new Notebook();
        otherNotebook.setTitle("Notebook B");
        otherNotebook.setUser(user);
        otherNotebook = notebookRepository.saveAndFlush(otherNotebook);

        // Concepts
        conceptA = new Concept();
        conceptA.setNotebook(notebook);
        conceptA.setCanonicalName("A");
        conceptA.setNormalizedName("a");
        conceptA.setConceptType("T");
        conceptA = conceptRepository.saveAndFlush(conceptA);

        conceptB = new Concept();
        conceptB.setNotebook(notebook);
        conceptB.setCanonicalName("B");
        conceptB.setNormalizedName("b");
        conceptB.setConceptType("T");
        conceptB = conceptRepository.saveAndFlush(conceptB);

        conceptC = new Concept();
        conceptC.setNotebook(notebook);
        conceptC.setCanonicalName("C");
        conceptC.setNormalizedName("c");
        conceptC.setConceptType("T");
        conceptC = conceptRepository.saveAndFlush(conceptC);

        conceptOther = new Concept();
        conceptOther.setNotebook(otherNotebook);
        conceptOther.setCanonicalName("Other");
        conceptOther.setNormalizedName("other");
        conceptOther.setConceptType("T");
        conceptOther = conceptRepository.saveAndFlush(conceptOther);

        // Docs
        docReady = new Document();
        docReady.setNotebook(notebook);
        docReady.setUser(user);
        docReady.setFileName("ready.pdf");
        docReady.setFilePath("/tmp");
        docReady.setStatus("READY");
        docReady.setActiveIndexVersion("v1");
        docReady = documentRepository.saveAndFlush(docReady);

        docFailed = new Document();
        docFailed.setNotebook(notebook);
        docFailed.setUser(user);
        docFailed.setFileName("failed.pdf");
        docFailed.setFilePath("/tmp");
        docFailed.setStatus("FAILED");
        docFailed.setActiveIndexVersion("v1");
        docFailed = documentRepository.saveAndFlush(docFailed);

        docProcessing = new Document();
        docProcessing.setNotebook(notebook);
        docProcessing.setUser(user);
        docProcessing.setFileName("proc.pdf");
        docProcessing.setFilePath("/tmp");
        docProcessing.setStatus("PROCESSING");
        docProcessing.setActiveIndexVersion("v1");
        docProcessing = documentRepository.saveAndFlush(docProcessing);

        docOtherNotebook = new Document();
        docOtherNotebook.setNotebook(otherNotebook);
        docOtherNotebook.setUser(user);
        docOtherNotebook.setFileName("other.pdf");
        docOtherNotebook.setFilePath("/tmp");
        docOtherNotebook.setStatus("READY");
        docOtherNotebook.setActiveIndexVersion("v1");
        docOtherNotebook = documentRepository.saveAndFlush(docOtherNotebook);

        docNullActive = new Document();
        docNullActive.setNotebook(notebook);
        docNullActive.setUser(user);
        docNullActive.setFileName("null.pdf");
        docNullActive.setFilePath("/tmp");
        docNullActive.setStatus("READY");
        docNullActive.setActiveIndexVersion(null);
        docNullActive = documentRepository.saveAndFlush(docNullActive);

        // Chunks
        chunkReady = new DocumentChunk();
        chunkReady.setDocument(docReady);
        chunkReady.setChunkIndex(0);
        chunkReady.setContent("Active");
        chunkReady.setIndexVersion("v1");
        chunkReady.setTokenCount(100);
        chunkReady = documentChunkRepository.saveAndFlush(chunkReady);

        chunkReadyStale = new DocumentChunk();
        chunkReadyStale.setDocument(docReady);
        chunkReadyStale.setChunkIndex(1);
        chunkReadyStale.setContent("Stale");
        chunkReadyStale.setIndexVersion("v0_stale");
        chunkReadyStale.setTokenCount(100);
        chunkReadyStale = documentChunkRepository.saveAndFlush(chunkReadyStale);

        chunkReady2 = new DocumentChunk();
        chunkReady2.setDocument(docReady);
        chunkReady2.setChunkIndex(2);
        chunkReady2.setContent("Active 2");
        chunkReady2.setIndexVersion("v1");
        chunkReady2.setTokenCount(100);
        chunkReady2 = documentChunkRepository.saveAndFlush(chunkReady2);

        chunkFailed = new DocumentChunk();
        chunkFailed.setDocument(docFailed);
        chunkFailed.setChunkIndex(0);
        chunkFailed.setContent("Failed");
        chunkFailed.setIndexVersion("v1");
        chunkFailed.setTokenCount(100);
        chunkFailed = documentChunkRepository.saveAndFlush(chunkFailed);

        chunkProcessing = new DocumentChunk();
        chunkProcessing.setDocument(docProcessing);
        chunkProcessing.setChunkIndex(0);
        chunkProcessing.setContent("Proc");
        chunkProcessing.setIndexVersion("v1");
        chunkProcessing.setTokenCount(100);
        chunkProcessing = documentChunkRepository.saveAndFlush(chunkProcessing);

        chunkOtherNotebook = new DocumentChunk();
        chunkOtherNotebook.setDocument(docOtherNotebook);
        chunkOtherNotebook.setChunkIndex(0);
        chunkOtherNotebook.setContent("Other");
        chunkOtherNotebook.setIndexVersion("v1");
        chunkOtherNotebook.setTokenCount(100);
        chunkOtherNotebook = documentChunkRepository.saveAndFlush(chunkOtherNotebook);

        chunkNullActive = new DocumentChunk();
        chunkNullActive.setDocument(docNullActive);
        chunkNullActive.setChunkIndex(0);
        chunkNullActive.setContent("Null");
        chunkNullActive.setIndexVersion("v1");
        chunkNullActive.setTokenCount(100);
        chunkNullActive = documentChunkRepository.saveAndFlush(chunkNullActive);
    }

    private ConceptRelation createRel(Concept src, Concept tgt, RelationType type) {
        ConceptRelation rel = new ConceptRelation();
        rel.setSourceConcept(src);
        rel.setTargetConcept(tgt);
        rel.setRelationType(type);
        rel.setConfidence(0.9);
        return conceptRelationRepository.saveAndFlush(rel);
    }

    private ConceptRelationSource createSrc(ConceptRelation rel, DocumentChunk chunk) {
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(rel);
        src.setDocumentChunk(chunk);
        src.setEvidenceText("Ev");
        src.setConfidence(0.8);
        return conceptRelationSourceRepository.saveAndFlush(src);
    }

    private GraphSeedConceptDto seed(Concept c) {
        return GraphSeedConceptDto.builder().conceptId(c.getId()).build();
    }

    @Test
    void testYZ_activeGroundedOutgoingAndIncoming() {
        ConceptRelation relOut = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(relOut, chunkReady);

        ConceptRelation relIn = createRel(conceptC, conceptA, RelationType.PART_OF);
        createSrc(relIn, chunkReady);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).hasSize(2);
    }

    @Test
    void testAA_staleOnlyRelationExcluded() {
        ConceptRelation rel = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel, chunkReadyStale);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAB_mixedActiveAndStale_returnsActiveEvidenceOnly() {
        ConceptRelation rel = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel, chunkReady);
        createSrc(rel, chunkReadyStale);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).hasSize(1);
        assertThat(ctx.getRelations().get(0).getActiveEvidenceCount()).isEqualTo(1); // AB, AK
        assertThat(ctx.getRelations().get(0).getEvidence()).hasSize(1); // AL
    }

    @Test
    void testACAD_failedAndProcessingExcluded() {
        ConceptRelation relF = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(relF, chunkFailed);

        ConceptRelation relP = createRel(conceptA, conceptC, RelationType.IS_A);
        createSrc(relP, chunkProcessing);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAEAF_crossNotebookEndpointsExcluded() {
        ConceptRelation relAE = createRel(conceptOther, conceptA, RelationType.IS_A);
        createSrc(relAE, chunkReady);

        ConceptRelation relAF = createRel(conceptA, conceptOther, RelationType.IS_A);
        createSrc(relAF, chunkReady);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAG_crossNotebookProvenanceExcluded() {
        ConceptRelation rel = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel, chunkOtherNotebook);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAH_nullActiveIndexExcluded() {
        ConceptRelation rel = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel, chunkNullActive);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAI_relationTouchingNoSeedExcluded() {
        ConceptRelation rel = createRel(conceptB, conceptC, RelationType.IS_A); // neither is A
        createSrc(rel, chunkReady);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        assertThat(ctx.getRelations()).isEmpty();
    }

    @Test
    void testAJ_twoSeedsTouchingSameRelation_oneRow() {
        ConceptRelation rel = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel, chunkReady);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA), seed(conceptB)));
        assertThat(ctx.getRelations()).hasSize(1);
    }
    
    @Test
    void testAM_evidenceQueryReturnsOnlySelected() {
        // Relation 1 (touching seed)
        ConceptRelation rel1 = createRel(conceptA, conceptB, RelationType.IS_A);
        createSrc(rel1, chunkReady);

        // Relation 2 (touching no seed)
        ConceptRelation rel2 = createRel(conceptB, conceptC, RelationType.IS_A);
        createSrc(rel2, chunkReady);

        GraphContextDto ctx = graphContextExpansionService.expandContext(notebook.getId(), List.of(seed(conceptA)));
        
        assertThat(ctx.getRelations()).hasSize(1);
        assertThat(ctx.getRelations().get(0).getEvidence()).hasSize(1);
        // Only evidence for rel1 is fetched, rel2 evidence should not be there (checked by count)
    }
}
