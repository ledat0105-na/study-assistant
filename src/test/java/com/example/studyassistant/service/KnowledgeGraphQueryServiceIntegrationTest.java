package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.dto.ConceptDetailDto;
import com.example.studyassistant.service.dto.KnowledgeGraphDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("Integration")
public class KnowledgeGraphQueryServiceIntegrationTest {

    @Autowired
    private KnowledgeGraphQueryService queryService;

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
    private ConceptSourceRepository conceptSourceRepository;
    @Autowired
    private ConceptRelationRepository conceptRelationRepository;
    @Autowired
    private ConceptRelationSourceRepository conceptRelationSourceRepository;

    private User testUser;
    private User otherUser;
    private Notebook notebookA;
    private Notebook notebookB;
    private Document docReady;
    private Document docFailed;
    private Document docNotebookB;
    private DocumentChunk chunkReady;
    private DocumentChunk chunkReady2;
    private DocumentChunk chunkReadyStale;
    private DocumentChunk chunkFailed;
    private DocumentChunk chunkNotebookB;
    
    private Concept conceptA;
    private Concept conceptB;
    private Concept conceptOrphan;
    private Concept conceptFailed;
    private Concept conceptOtherNb;
    private Concept conceptMixed;
    private Concept conceptStaleOnly;
    
    private ConceptSource srcA;
    private ConceptSource srcA2;
    private ConceptSource srcB;
    private ConceptSource srcFailed;
    private ConceptSource srcOtherNb;
    private ConceptSource srcMixedActive;
    private ConceptSource srcMixedStale;
    private ConceptSource srcStaleOnly;
    
    private ConceptRelation relValid;
    private ConceptRelationSource relSrcValid;
    private ConceptRelationSource relSrcValid2;
    
    private ConceptRelation relUnsupported;
    
    private ConceptRelation relFailed;
    private ConceptRelationSource relSrcFailed;
    
    private ConceptRelation relCrossTarget;
    private ConceptRelationSource relSrcCrossTarget;
    
    private ConceptRelation relDangling;
    private ConceptRelationSource relSrcDangling;
    
    private ConceptRelation relMixed;
    private ConceptRelationSource relSrcMixedActive;
    private ConceptRelationSource relSrcMixedStale;
    
    private ConceptRelation relStaleOnly;
    private ConceptRelationSource relSrcStaleOnly;
    
    private List<Long> managedUserIds = new ArrayList<>();
    private List<Long> managedNotebookIds = new ArrayList<>();
    private List<Long> managedDocIds = new ArrayList<>();
    private List<Long> managedChunkIds = new ArrayList<>();
    private List<Long> managedConceptIds = new ArrayList<>();
    private List<Long> managedSrcIds = new ArrayList<>();
    private List<Long> managedRelIds = new ArrayList<>();
    private List<Long> managedRelSrcIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queryService, "maxNodes", 500);
        ReflectionTestUtils.setField(queryService, "maxEdges", 1000);

        String s = UUID.randomUUID().toString().substring(0, 8);
        
        testUser = new User();
        testUser.setUsername("u_" + s);
        testUser.setEmail("u_" + s + "@example.com");
        testUser.setPassword("x");
        testUser = userRepository.saveAndFlush(testUser);
        managedUserIds.add(testUser.getId());
        
        otherUser = new User();
        otherUser.setUsername("u2_" + s);
        otherUser.setEmail("u2_" + s + "@example.com");
        otherUser.setPassword("x");
        otherUser = userRepository.saveAndFlush(otherUser);
        managedUserIds.add(otherUser.getId());
        
        notebookA = new Notebook();
        notebookA.setTitle("Nb A " + s);
        notebookA.setUser(testUser);
        notebookA = notebookRepository.saveAndFlush(notebookA);
        managedNotebookIds.add(notebookA.getId());
        
        notebookB = new Notebook();
        notebookB.setTitle("Nb B " + s);
        notebookB.setUser(testUser);
        notebookB = notebookRepository.saveAndFlush(notebookB);
        managedNotebookIds.add(notebookB.getId());
        
        docReady = new Document();
        docReady.setNotebook(notebookA);
        docReady.setUser(testUser);
        docReady.setFileName("doc_ready_" + s);
        docReady.setFilePath("p1");
        docReady.setStatus("READY");
        docReady.setActiveIndexVersion("V2"); // Active index version is V2
        docReady = documentRepository.saveAndFlush(docReady);
        managedDocIds.add(docReady.getId());
        
        docFailed = new Document();
        docFailed.setNotebook(notebookA);
        docFailed.setUser(testUser);
        docFailed.setFileName("doc_failed_" + s);
        docFailed.setFilePath("p2");
        docFailed.setStatus("FAILED");
        docFailed.setActiveIndexVersion("V2");
        docFailed = documentRepository.saveAndFlush(docFailed);
        managedDocIds.add(docFailed.getId());
        
        docNotebookB = new Document();
        docNotebookB.setNotebook(notebookB);
        docNotebookB.setUser(testUser);
        docNotebookB.setFileName("doc_b_" + s);
        docNotebookB.setFilePath("p3");
        docNotebookB.setStatus("READY");
        docNotebookB.setActiveIndexVersion("V2");
        docNotebookB = documentRepository.saveAndFlush(docNotebookB);
        managedDocIds.add(docNotebookB.getId());
        
        chunkReady = new DocumentChunk();
        chunkReady.setDocument(docReady);
        chunkReady.setChunkIndex(1);
        chunkReady.setContent("C1");
        chunkReady.setTokenCount(1);
        chunkReady.setIndexVersion("V2"); // ACTIVE
        chunkReady = documentChunkRepository.saveAndFlush(chunkReady);
        managedChunkIds.add(chunkReady.getId());
        
        chunkReady2 = new DocumentChunk();
        chunkReady2.setDocument(docReady);
        chunkReady2.setChunkIndex(2);
        chunkReady2.setContent("C2");
        chunkReady2.setTokenCount(1);
        chunkReady2.setIndexVersion("V2"); // ACTIVE
        chunkReady2 = documentChunkRepository.saveAndFlush(chunkReady2);
        managedChunkIds.add(chunkReady2.getId());
        
        chunkReadyStale = new DocumentChunk();
        chunkReadyStale.setDocument(docReady);
        chunkReadyStale.setChunkIndex(3);
        chunkReadyStale.setContent("C3_STALE");
        chunkReadyStale.setTokenCount(1);
        chunkReadyStale.setIndexVersion("V1"); // STALE (old version)
        chunkReadyStale = documentChunkRepository.saveAndFlush(chunkReadyStale);
        managedChunkIds.add(chunkReadyStale.getId());
        
        chunkFailed = new DocumentChunk();
        chunkFailed.setDocument(docFailed);
        chunkFailed.setChunkIndex(1);
        chunkFailed.setContent("F1");
        chunkFailed.setTokenCount(1);
        chunkFailed.setIndexVersion("V2");
        chunkFailed = documentChunkRepository.saveAndFlush(chunkFailed);
        managedChunkIds.add(chunkFailed.getId());
        
        chunkNotebookB = new DocumentChunk();
        chunkNotebookB.setDocument(docNotebookB);
        chunkNotebookB.setChunkIndex(1);
        chunkNotebookB.setContent("B1");
        chunkNotebookB.setTokenCount(1);
        chunkNotebookB.setIndexVersion("V2");
        chunkNotebookB = documentChunkRepository.saveAndFlush(chunkNotebookB);
        managedChunkIds.add(chunkNotebookB.getId());
        
        conceptA = new Concept();
        conceptA.setNotebook(notebookA);
        conceptA.setCanonicalName("Concept A");
        conceptA.setNormalizedName("concept a " + s);
        conceptA = conceptRepository.saveAndFlush(conceptA);
        managedConceptIds.add(conceptA.getId());
        
        conceptB = new Concept();
        conceptB.setNotebook(notebookA);
        conceptB.setCanonicalName("Concept B");
        conceptB.setNormalizedName("concept b " + s);
        conceptB = conceptRepository.saveAndFlush(conceptB);
        managedConceptIds.add(conceptB.getId());
        
        conceptOrphan = new Concept();
        conceptOrphan.setNotebook(notebookA);
        conceptOrphan.setCanonicalName("Concept Orphan");
        conceptOrphan.setNormalizedName("concept orphan " + s);
        conceptOrphan = conceptRepository.saveAndFlush(conceptOrphan);
        managedConceptIds.add(conceptOrphan.getId());
        
        conceptFailed = new Concept();
        conceptFailed.setNotebook(notebookA);
        conceptFailed.setCanonicalName("Concept Failed");
        conceptFailed.setNormalizedName("concept failed " + s);
        conceptFailed = conceptRepository.saveAndFlush(conceptFailed);
        managedConceptIds.add(conceptFailed.getId());
        
        conceptOtherNb = new Concept();
        conceptOtherNb.setNotebook(notebookB);
        conceptOtherNb.setCanonicalName("Concept Other Nb");
        conceptOtherNb.setNormalizedName("concept other nb " + s);
        conceptOtherNb = conceptRepository.saveAndFlush(conceptOtherNb);
        managedConceptIds.add(conceptOtherNb.getId());
        
        conceptMixed = new Concept();
        conceptMixed.setNotebook(notebookA);
        conceptMixed.setCanonicalName("Concept Mixed");
        conceptMixed.setNormalizedName("concept mixed " + s);
        conceptMixed = conceptRepository.saveAndFlush(conceptMixed);
        managedConceptIds.add(conceptMixed.getId());
        
        conceptStaleOnly = new Concept();
        conceptStaleOnly.setNotebook(notebookA);
        conceptStaleOnly.setCanonicalName("Concept Stale");
        conceptStaleOnly.setNormalizedName("concept stale " + s);
        conceptStaleOnly = conceptRepository.saveAndFlush(conceptStaleOnly);
        managedConceptIds.add(conceptStaleOnly.getId());
        
        srcA = new ConceptSource();
        srcA.setConcept(conceptA);
        srcA.setDocumentChunk(chunkReady);
        srcA.setEvidenceText("A");
        srcA = conceptSourceRepository.saveAndFlush(srcA);
        managedSrcIds.add(srcA.getId());
        
        srcA2 = new ConceptSource();
        srcA2.setConcept(conceptA);
        srcA2.setDocumentChunk(chunkReady2);
        srcA2.setEvidenceText("A2");
        srcA2 = conceptSourceRepository.saveAndFlush(srcA2);
        managedSrcIds.add(srcA2.getId());
        
        srcB = new ConceptSource();
        srcB.setConcept(conceptB);
        srcB.setDocumentChunk(chunkReady);
        srcB.setEvidenceText("B");
        srcB = conceptSourceRepository.saveAndFlush(srcB);
        managedSrcIds.add(srcB.getId());
        
        srcFailed = new ConceptSource();
        srcFailed.setConcept(conceptFailed);
        srcFailed.setDocumentChunk(chunkFailed);
        srcFailed.setEvidenceText("Failed");
        srcFailed = conceptSourceRepository.saveAndFlush(srcFailed);
        managedSrcIds.add(srcFailed.getId());
        
        srcOtherNb = new ConceptSource();
        srcOtherNb.setConcept(conceptOtherNb);
        srcOtherNb.setDocumentChunk(chunkNotebookB);
        srcOtherNb.setEvidenceText("A");
        srcOtherNb = conceptSourceRepository.saveAndFlush(srcOtherNb);
        managedSrcIds.add(srcOtherNb.getId());
        
        srcMixedActive = new ConceptSource();
        srcMixedActive.setConcept(conceptMixed);
        srcMixedActive.setDocumentChunk(chunkReady);
        srcMixedActive.setEvidenceText("Mixed Active");
        srcMixedActive = conceptSourceRepository.saveAndFlush(srcMixedActive);
        managedSrcIds.add(srcMixedActive.getId());
        
        srcMixedStale = new ConceptSource();
        srcMixedStale.setConcept(conceptMixed);
        srcMixedStale.setDocumentChunk(chunkReadyStale);
        srcMixedStale.setEvidenceText("Mixed Stale");
        srcMixedStale = conceptSourceRepository.saveAndFlush(srcMixedStale);
        managedSrcIds.add(srcMixedStale.getId());
        
        srcStaleOnly = new ConceptSource();
        srcStaleOnly.setConcept(conceptStaleOnly);
        srcStaleOnly.setDocumentChunk(chunkReadyStale);
        srcStaleOnly.setEvidenceText("Stale Only");
        srcStaleOnly = conceptSourceRepository.saveAndFlush(srcStaleOnly);
        managedSrcIds.add(srcStaleOnly.getId());
        
        relValid = new ConceptRelation();
        relValid.setSourceConcept(conceptA);
        relValid.setTargetConcept(conceptB);
        relValid.setRelationType(RelationType.RELATED_TO);
        relValid.setConfidence(0.9);
        relValid = conceptRelationRepository.saveAndFlush(relValid);
        managedRelIds.add(relValid.getId());
        
        relSrcValid = new ConceptRelationSource();
        relSrcValid.setConceptRelation(relValid);
        relSrcValid.setDocumentChunk(chunkReady);
        relSrcValid.setEvidenceText("Ev 1");
        relSrcValid.setConfidence(0.8);
        relSrcValid = conceptRelationSourceRepository.saveAndFlush(relSrcValid);
        managedRelSrcIds.add(relSrcValid.getId());
        
        relSrcValid2 = new ConceptRelationSource();
        relSrcValid2.setConceptRelation(relValid);
        relSrcValid2.setDocumentChunk(chunkReady2); // second chunk!
        relSrcValid2.setEvidenceText("Ev 2");
        relSrcValid2.setConfidence(0.95);
        relSrcValid2 = conceptRelationSourceRepository.saveAndFlush(relSrcValid2);
        managedRelSrcIds.add(relSrcValid2.getId());
        
        relUnsupported = new ConceptRelation();
        relUnsupported.setSourceConcept(conceptB);
        relUnsupported.setTargetConcept(conceptA);
        relUnsupported.setRelationType(RelationType.PREREQUISITE_OF);
        relUnsupported.setConfidence(0.5);
        relUnsupported = conceptRelationRepository.saveAndFlush(relUnsupported);
        managedRelIds.add(relUnsupported.getId());
        
        relFailed = new ConceptRelation();
        relFailed.setSourceConcept(conceptA);
        relFailed.setTargetConcept(conceptB);
        relFailed.setRelationType(RelationType.PART_OF);
        relFailed.setConfidence(0.6);
        relFailed = conceptRelationRepository.saveAndFlush(relFailed);
        managedRelIds.add(relFailed.getId());
        
        relSrcFailed = new ConceptRelationSource();
        relSrcFailed.setConceptRelation(relFailed);
        relSrcFailed.setDocumentChunk(chunkFailed);
        relSrcFailed.setEvidenceText("Failed chunk");
        relSrcFailed = conceptRelationSourceRepository.saveAndFlush(relSrcFailed);
        managedRelSrcIds.add(relSrcFailed.getId());
        
        relCrossTarget = new ConceptRelation();
        relCrossTarget.setSourceConcept(conceptA);
        relCrossTarget.setTargetConcept(conceptOtherNb);
        relCrossTarget.setRelationType(RelationType.IS_A);
        relCrossTarget = conceptRelationRepository.saveAndFlush(relCrossTarget);
        managedRelIds.add(relCrossTarget.getId());
        
        relSrcCrossTarget = new ConceptRelationSource();
        relSrcCrossTarget.setConceptRelation(relCrossTarget);
        relSrcCrossTarget.setDocumentChunk(chunkReady);
        relSrcCrossTarget.setEvidenceText("Cross target ev");
        relSrcCrossTarget = conceptRelationSourceRepository.saveAndFlush(relSrcCrossTarget);
        managedRelSrcIds.add(relSrcCrossTarget.getId());
        
        relDangling = new ConceptRelation();
        relDangling.setSourceConcept(conceptOrphan);
        relDangling.setTargetConcept(conceptA);
        relDangling.setRelationType(RelationType.RELATED_TO);
        relDangling = conceptRelationRepository.saveAndFlush(relDangling);
        managedRelIds.add(relDangling.getId());
        
        relSrcDangling = new ConceptRelationSource();
        relSrcDangling.setConceptRelation(relDangling);
        relSrcDangling.setDocumentChunk(chunkReady);
        relSrcDangling.setEvidenceText("Dangling ev");
        relSrcDangling = conceptRelationSourceRepository.saveAndFlush(relSrcDangling);
        managedRelSrcIds.add(relSrcDangling.getId());
        
        // Mixed Provenance Relation
        relMixed = new ConceptRelation();
        relMixed.setSourceConcept(conceptA);
        relMixed.setTargetConcept(conceptMixed);
        relMixed.setRelationType(RelationType.RELATED_TO);
        relMixed = conceptRelationRepository.saveAndFlush(relMixed);
        managedRelIds.add(relMixed.getId());
        
        relSrcMixedActive = new ConceptRelationSource();
        relSrcMixedActive.setConceptRelation(relMixed);
        relSrcMixedActive.setDocumentChunk(chunkReady); // Active
        relSrcMixedActive.setEvidenceText("Mixed Rel Active");
        relSrcMixedActive = conceptRelationSourceRepository.saveAndFlush(relSrcMixedActive);
        managedRelSrcIds.add(relSrcMixedActive.getId());
        
        relSrcMixedStale = new ConceptRelationSource();
        relSrcMixedStale.setConceptRelation(relMixed);
        relSrcMixedStale.setDocumentChunk(chunkReadyStale); // Stale
        relSrcMixedStale.setEvidenceText("Mixed Rel Stale");
        relSrcMixedStale = conceptRelationSourceRepository.saveAndFlush(relSrcMixedStale);
        managedRelSrcIds.add(relSrcMixedStale.getId());
        
        // Stale Only Relation - CHANGED TARGET TO AVOID CONSTRAINT VIOLATION
        relStaleOnly = new ConceptRelation();
        relStaleOnly.setSourceConcept(conceptA);
        relStaleOnly.setTargetConcept(conceptStaleOnly);
        relStaleOnly.setRelationType(RelationType.RELATED_TO);
        relStaleOnly = conceptRelationRepository.saveAndFlush(relStaleOnly);
        managedRelIds.add(relStaleOnly.getId());
        
        relSrcStaleOnly = new ConceptRelationSource();
        relSrcStaleOnly.setConceptRelation(relStaleOnly);
        relSrcStaleOnly.setDocumentChunk(chunkReadyStale); // Stale
        relSrcStaleOnly.setEvidenceText("Stale Rel Only");
        relSrcStaleOnly = conceptRelationSourceRepository.saveAndFlush(relSrcStaleOnly);
        managedRelSrcIds.add(relSrcStaleOnly.getId());
    }

    @AfterEach
    void tearDown() {
        for (Long id : managedRelSrcIds) conceptRelationSourceRepository.deleteById(id);
        for (Long id : managedRelIds) conceptRelationRepository.deleteById(id);
        for (Long id : managedSrcIds) conceptSourceRepository.deleteById(id);
        for (Long id : managedConceptIds) conceptRepository.deleteById(id);
        for (Long id : managedChunkIds) documentChunkRepository.deleteById(id);
        for (Long id : managedDocIds) documentRepository.deleteById(id);
        for (Long id : managedNotebookIds) notebookRepository.deleteById(id);
        for (Long id : managedUserIds) userRepository.deleteById(id);
    }

    @Test
    void testA_OwnedNotebookReturnsGroundedGraph() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        // conceptA, conceptB, conceptMixed (has active source)
        assertThat(dto.getNodes()).extracting("id").contains(conceptA.getId(), conceptB.getId(), conceptMixed.getId());
        // conceptStaleOnly should NOT be returned
        assertThat(dto.getNodes()).extracting("id").doesNotContain(conceptStaleOnly.getId());
        
        assertThat(dto.getEdges()).extracting("id").contains(relValid.getId(), relMixed.getId());
        // relStaleOnly should NOT be returned
        assertThat(dto.getEdges()).extracting("id").doesNotContain(relStaleOnly.getId());
    }

    @Test
    void testB_EmptyOwnedNotebookReturnsEmptyGraph() {
        Notebook emptyNb = new Notebook();
        emptyNb.setTitle("Empty");
        emptyNb.setUser(testUser);
        emptyNb = notebookRepository.saveAndFlush(emptyNb);
        managedNotebookIds.add(emptyNb.getId());
        
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(emptyNb.getId(), testUser.getId());
        assertThat(dto.getNodes()).isEmpty();
        assertThat(dto.getEdges()).isEmpty();
    }

    @Test
    void testC_UnauthorizedNotebookMaskedAsNotFound() {
        assertThatThrownBy(() -> queryService.getGroundedKnowledgeGraph(notebookA.getId(), otherUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notebook not found");
                
        assertThatThrownBy(() -> queryService.getGroundedKnowledgeGraph(9999L, testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notebook not found");
    }

    @Test
    void testD_GroundedConceptDetailSucceeds() {
        ConceptDetailDto dto = queryService.getConceptDetail(notebookA.getId(), conceptA.getId(), testUser.getId());
        assertThat(dto.getConcept().getId()).isEqualTo(conceptA.getId());
        
        assertThat(dto.getIncomingRelations()).isEmpty();
        assertThat(dto.getOutgoingRelations()).extracting("id").contains(relValid.getId(), relMixed.getId());
        assertThat(dto.getOutgoingRelations()).extracting("id").doesNotContain(relStaleOnly.getId());
    }

    @Test
    void testE_OrphanConceptDetailReturnsNotFound() {
        assertThatThrownBy(() -> queryService.getConceptDetail(notebookA.getId(), conceptOrphan.getId(), testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Concept not found or ungrounded");
    }

    @Test
    void testF_ConceptBackedOnlyByNonReadyDocumentReturnsNotFound() {
        assertThatThrownBy(() -> queryService.getConceptDetail(notebookA.getId(), conceptFailed.getId(), testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Concept not found or ungrounded");
    }

    @Test
    void testG_CrossNotebookConceptReturnsNotFound() {
        assertThatThrownBy(() -> queryService.getConceptDetail(notebookA.getId(), conceptOtherNb.getId(), testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Concept not found or ungrounded");
    }

    @Test
    void testH_GraphExcludesUnsupportedRelation() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        assertThat(dto.getEdges()).extracting("id").doesNotContain(relUnsupported.getId());
    }

    @Test
    void testI_GraphExcludesRelationWithUngroundedEndpoint() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        assertThat(dto.getEdges()).extracting("id").doesNotContain(relDangling.getId());
    }

    @Test
    void testJ_DetailExcludesRelationWithUngroundedEndpoint() {
        ConceptDetailDto dto = queryService.getConceptDetail(notebookA.getId(), conceptA.getId(), testUser.getId());
        assertThat(dto.getIncomingRelations()).extracting("id").doesNotContain(relDangling.getId());
    }

    @Test
    void testK_IncomingOutgoingDirectionPreserved() {
        ConceptDetailDto dtoA = queryService.getConceptDetail(notebookA.getId(), conceptA.getId(), testUser.getId());
        assertThat(dtoA.getOutgoingRelations()).extracting("id").contains(relValid.getId());
        assertThat(dtoA.getIncomingRelations()).isEmpty();
        
        ConceptDetailDto dtoB = queryService.getConceptDetail(notebookA.getId(), conceptB.getId(), testUser.getId());
        assertThat(dtoB.getIncomingRelations()).extracting("id").contains(relValid.getId());
        assertThat(dtoB.getOutgoingRelations()).isEmpty();
    }

    @Test
    void testL_MultiEvidenceRelationReturnsOneEdge() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        var edge = dto.getEdges().stream().filter(e -> e.getId().equals(relValid.getId())).findFirst().orElseThrow();
        assertThat(edge.getEvidenceCount()).isEqualTo(2L);
    }

    @Test
    void testM_DetailReceivesAllEvidenceViaBatchedRetrieval() {
        ConceptDetailDto dto = queryService.getConceptDetail(notebookA.getId(), conceptA.getId(), testUser.getId());
        var evidenceForValid = dto.getRelationEvidence().stream().filter(e -> e.getRelationId().equals(relValid.getId())).toList();
        assertThat(evidenceForValid).hasSize(2); // Ev 1 and Ev 2
    }

    @Test
    void testN_CrossNotebookRelationProvenanceExcluded() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        assertThat(dto.getEdges()).extracting("id").doesNotContain(relCrossTarget.getId());
    }

    @Test
    void testO_NonReadyRelationProvenanceExcluded() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        assertThat(dto.getEdges()).extracting("id").doesNotContain(relFailed.getId());
    }

    @Test
    void testP_FullGraphContainsNoDanglingEdgeIds() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        assertThat(dto.getEdges()).extracting("id").contains(relValid.getId(), relMixed.getId());
    }

    @Test
    void testQ_SourceCountFromMultipleReadyConceptSourcesIsCorrect() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        var nodeA = dto.getNodes().stream().filter(n -> n.getId().equals(conceptA.getId())).findFirst().orElseThrow();
        assertThat(nodeA.getSourceCount()).isEqualTo(2L); // Active ones
    }

    @Test
    void testR_AllFourRelationTypeValuesSurvive() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        var edge = dto.getEdges().stream().filter(e -> e.getId().equals(relValid.getId())).findFirst().orElseThrow();
        assertThat(edge.getRelationType()).isEqualTo(RelationType.RELATED_TO);
    }
    
    @Test
    void testS_MixedProvenanceConceptSurvivesAndCountsOnlyActive() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        var nodeMixed = dto.getNodes().stream().filter(n -> n.getId().equals(conceptMixed.getId())).findFirst().orElseThrow();
        assertThat(nodeMixed.getSourceCount()).isEqualTo(1L); // Mixed Active, skips Mixed Stale
    }
    
    @Test
    void testT_MixedProvenanceRelationSurvivesAndCountsOnlyActive() {
        KnowledgeGraphDto dto = queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId());
        var edgeMixed = dto.getEdges().stream().filter(e -> e.getId().equals(relMixed.getId())).findFirst().orElseThrow();
        assertThat(edgeMixed.getEvidenceCount()).isEqualTo(1L); // Mixed Rel Active, skips Mixed Rel Stale
        
        ConceptDetailDto detail = queryService.getConceptDetail(notebookA.getId(), conceptMixed.getId(), testUser.getId());
        var evMixed = detail.getRelationEvidence().stream().filter(e -> e.getRelationId().equals(relMixed.getId())).toList();
        assertThat(evMixed).hasSize(1);
        assertThat(evMixed.get(0).getEvidenceText()).isEqualTo("Mixed Rel Active");
    }
    
    @Test
    void testU_StaleOnlyProvenanceConceptReturnsNotFound() {
        assertThatThrownBy(() -> queryService.getConceptDetail(notebookA.getId(), conceptStaleOnly.getId(), testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Concept not found or ungrounded");
    }
    
    @Test
    void testLimit_NodeOverflow() {
        ReflectionTestUtils.setField(queryService, "maxNodes", 1);
        assertThatThrownBy(() -> queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId()))
                .isInstanceOf(GraphTooLargeException.class);
    }
    
    @Test
    void testLimit_EdgeOverflow() {
        ReflectionTestUtils.setField(queryService, "maxEdges", 0);
        assertThatThrownBy(() -> queryService.getGroundedKnowledgeGraph(notebookA.getId(), testUser.getId()))
                .isInstanceOf(GraphTooLargeException.class);
    }
}
