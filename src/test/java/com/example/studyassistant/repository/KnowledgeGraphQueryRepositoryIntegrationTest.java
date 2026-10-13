package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.projection.GraphEdgeProjection;
import com.example.studyassistant.repository.projection.GraphNodeProjection;
import com.example.studyassistant.repository.projection.RelationEvidenceProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("Integration")
public class KnowledgeGraphQueryRepositoryIntegrationTest {

    @Autowired
    private KnowledgeGraphQueryRepository queryRepository;

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
    private Notebook notebookA;
    private Notebook notebookB;
    
    private Document docReady;
    private Document docFailed;
    private Document docNotebookB;
    
    private DocumentChunk chunkReady;
    private DocumentChunk chunkReady2;
    private DocumentChunk chunkFailed;
    private DocumentChunk chunkNotebookB;
    
    private Concept conceptA;
    private Concept conceptB;
    private Concept conceptOrphan;
    private Concept conceptFailed;
    private Concept conceptOtherNb;
    private Concept conceptSameNameOtherNb;
    
    private ConceptSource srcA;
    private ConceptSource srcA2;
    private ConceptSource srcB;
    private ConceptSource srcFailed;
    private ConceptSource srcOtherNb;
    
    private ConceptRelation relValid;
    private ConceptRelationSource relSrcValid;
    private ConceptRelationSource relSrcValid2;
    
    private ConceptRelation relUnsupported;
    
    private ConceptRelation relFailed;
    private ConceptRelationSource relSrcFailed;
    
    private ConceptRelation relCrossTarget;
    private ConceptRelationSource relSrcCrossTarget;
    
    private ConceptRelation relCrossProvenance;
    private ConceptRelationSource relSrcCrossProvenance;
    
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
        String s = UUID.randomUUID().toString().substring(0, 8);
        
        testUser = new User();
        testUser.setUsername("u_" + s);
        testUser.setEmail("u_" + s + "@example.com");
        testUser.setPassword("x");
        testUser = userRepository.saveAndFlush(testUser);
        managedUserIds.add(testUser.getId());
        
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
        docReady.setActiveIndexVersion("v1");
        docReady = documentRepository.saveAndFlush(docReady);
        managedDocIds.add(docReady.getId());
        
        docFailed = new Document();
        docFailed.setNotebook(notebookA);
        docFailed.setUser(testUser);
        docFailed.setFileName("doc_failed_" + s);
        docFailed.setFilePath("p2");
        docFailed.setStatus("FAILED");
        docFailed.setActiveIndexVersion("v1");
        docFailed = documentRepository.saveAndFlush(docFailed);
        managedDocIds.add(docFailed.getId());
        
        docNotebookB = new Document();
        docNotebookB.setNotebook(notebookB);
        docNotebookB.setUser(testUser);
        docNotebookB.setFileName("doc_b_" + s);
        docNotebookB.setFilePath("p3");
        docNotebookB.setStatus("READY");
        docNotebookB.setActiveIndexVersion("v1");
        docNotebookB = documentRepository.saveAndFlush(docNotebookB);
        managedDocIds.add(docNotebookB.getId());
        
        chunkReady = new DocumentChunk();
        chunkReady.setDocument(docReady);
        chunkReady.setChunkIndex(1);
        chunkReady.setContent("C1");
        chunkReady.setTokenCount(1);
        chunkReady.setIndexVersion("v1");
        chunkReady = documentChunkRepository.saveAndFlush(chunkReady);
        managedChunkIds.add(chunkReady.getId());
        
        chunkReady2 = new DocumentChunk();
        chunkReady2.setDocument(docReady);
        chunkReady2.setChunkIndex(2);
        chunkReady2.setContent("C2");
        chunkReady2.setTokenCount(1);
        chunkReady2.setIndexVersion("v1");
        chunkReady2 = documentChunkRepository.saveAndFlush(chunkReady2);
        managedChunkIds.add(chunkReady2.getId());
        
        chunkFailed = new DocumentChunk();
        chunkFailed.setDocument(docFailed);
        chunkFailed.setChunkIndex(1);
        chunkFailed.setContent("F1");
        chunkFailed.setTokenCount(1);
        chunkFailed.setIndexVersion("v1");
        chunkFailed = documentChunkRepository.saveAndFlush(chunkFailed);
        managedChunkIds.add(chunkFailed.getId());
        
        chunkNotebookB = new DocumentChunk();
        chunkNotebookB.setDocument(docNotebookB);
        chunkNotebookB.setChunkIndex(1);
        chunkNotebookB.setContent("B1");
        chunkNotebookB.setTokenCount(1);
        chunkNotebookB.setIndexVersion("v1");
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
        
        conceptSameNameOtherNb = new Concept();
        conceptSameNameOtherNb.setNotebook(notebookB);
        conceptSameNameOtherNb.setCanonicalName("Concept A"); // same name
        conceptSameNameOtherNb.setNormalizedName("concept a " + s);
        conceptSameNameOtherNb = conceptRepository.saveAndFlush(conceptSameNameOtherNb);
        managedConceptIds.add(conceptSameNameOtherNb.getId());
        
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
        srcOtherNb.setConcept(conceptSameNameOtherNb);
        srcOtherNb.setDocumentChunk(chunkNotebookB);
        srcOtherNb.setEvidenceText("A");
        srcOtherNb = conceptSourceRepository.saveAndFlush(srcOtherNb);
        managedSrcIds.add(srcOtherNb.getId());
        
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
        relUnsupported.setRelationType(RelationType.RELATED_TO);
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
        relCrossTarget.setTargetConcept(conceptSameNameOtherNb);
        relCrossTarget.setRelationType(RelationType.IS_A);
        relCrossTarget = conceptRelationRepository.saveAndFlush(relCrossTarget);
        managedRelIds.add(relCrossTarget.getId());
        
        relSrcCrossTarget = new ConceptRelationSource();
        relSrcCrossTarget.setConceptRelation(relCrossTarget);
        relSrcCrossTarget.setDocumentChunk(chunkReady);
        relSrcCrossTarget.setEvidenceText("Cross target ev");
        relSrcCrossTarget = conceptRelationSourceRepository.saveAndFlush(relSrcCrossTarget);
        managedRelSrcIds.add(relSrcCrossTarget.getId());
        
        relCrossProvenance = new ConceptRelation();
        relCrossProvenance.setSourceConcept(conceptA);
        relCrossProvenance.setTargetConcept(conceptB);
        relCrossProvenance.setRelationType(RelationType.PREREQUISITE_OF);
        relCrossProvenance = conceptRelationRepository.saveAndFlush(relCrossProvenance);
        managedRelIds.add(relCrossProvenance.getId());
        
        relSrcCrossProvenance = new ConceptRelationSource();
        relSrcCrossProvenance.setConceptRelation(relCrossProvenance);
        relSrcCrossProvenance.setDocumentChunk(chunkNotebookB);
        relSrcCrossProvenance.setEvidenceText("Cross provenance ev");
        relSrcCrossProvenance = conceptRelationSourceRepository.saveAndFlush(relSrcCrossProvenance);
        managedRelSrcIds.add(relSrcCrossProvenance.getId());
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
    void testNodeQuery() {
        List<GraphNodeProjection> nodes = queryRepository.findGroundedNodesByNotebookId(notebookA.getId());
        
        assertThat(nodes).hasSize(2);
        
        GraphNodeProjection nodeA = nodes.stream().filter(n -> n.getConceptId().equals(conceptA.getId())).findFirst().orElseThrow();
        assertThat(nodeA.getSourceCount()).isEqualTo(2L);
        
        GraphNodeProjection nodeB = nodes.stream().filter(n -> n.getConceptId().equals(conceptB.getId())).findFirst().orElseThrow();
        assertThat(nodeB.getSourceCount()).isEqualTo(1L);
        
        // Excluded:
        assertThat(nodes).noneMatch(n -> n.getConceptId().equals(conceptOrphan.getId())); // B
        assertThat(nodes).noneMatch(n -> n.getConceptId().equals(conceptFailed.getId())); // C
        assertThat(nodes).noneMatch(n -> n.getConceptId().equals(conceptOtherNb.getId())); // D
        assertThat(nodes).noneMatch(n -> n.getConceptId().equals(conceptSameNameOtherNb.getId())); // E
    }

    @Test
    void testEdgeQuery() {
        List<GraphEdgeProjection> edges = queryRepository.findGroundedEdgesByNotebookId(notebookA.getId());
        
        assertThat(edges).hasSize(1); // Only relValid should match!
        
        GraphEdgeProjection edge = edges.get(0);
        assertThat(edge.getRelationId()).isEqualTo(relValid.getId());
        assertThat(edge.getSourceConceptId()).isEqualTo(conceptA.getId()); // M
        assertThat(edge.getTargetConceptId()).isEqualTo(conceptB.getId()); // M
        assertThat(edge.getRelationType()).isEqualTo(RelationType.RELATED_TO); // N
        assertThat(edge.getEvidenceCount()).isEqualTo(2L); // L
        assertThat(edge.getEdgeConfidence()).isEqualTo(0.9);
        
        // Excluded:
        assertThat(edges).noneMatch(e -> e.getRelationId().equals(relUnsupported.getId())); // G
        assertThat(edges).noneMatch(e -> e.getRelationId().equals(relCrossTarget.getId())); // I
        assertThat(edges).noneMatch(e -> e.getRelationId().equals(relFailed.getId())); // J
        assertThat(edges).noneMatch(e -> e.getRelationId().equals(relCrossProvenance.getId())); // K
    }

    @Test
    void testEvidenceQuery() {
        List<RelationEvidenceProjection> evs = queryRepository.findRelationEvidence(notebookA.getId(), relValid.getId());
        
        assertThat(evs).hasSize(2); // O
        
        RelationEvidenceProjection ev1 = evs.get(0);
        assertThat(ev1.getChunkId()).isNotNull();
        assertThat(ev1.getDocumentTitle()).isEqualTo(docReady.getFileName());
        assertThat(ev1.getEvidenceText()).startsWith("Ev");
        
        // Should not return cross notebook evidence if we ask for cross provenance rel
        List<RelationEvidenceProjection> crossEvs = queryRepository.findRelationEvidence(notebookA.getId(), relCrossProvenance.getId());
        assertThat(crossEvs).isEmpty();
    }
}
