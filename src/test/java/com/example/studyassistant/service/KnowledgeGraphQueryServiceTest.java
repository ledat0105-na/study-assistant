package com.example.studyassistant.service;

import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.RelationType;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.projection.GraphEdgeProjection;
import com.example.studyassistant.repository.projection.GraphNodeProjection;
import com.example.studyassistant.repository.projection.RelationEvidenceProjection;
import com.example.studyassistant.service.dto.ConceptDetailDto;
import com.example.studyassistant.service.dto.KnowledgeGraphDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KnowledgeGraphQueryServiceTest {

    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private KnowledgeGraphQueryRepository graphQueryRepository;

    @InjectMocks
    private KnowledgeGraphQueryService service;

    private User testUser;
    private Notebook testNotebook;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxNodes", 500);
        ReflectionTestUtils.setField(service, "maxEdges", 1000);

        testUser = new User();
        testUser.setId(1L);

        testNotebook = new Notebook();
        testNotebook.setId(10L);
        testNotebook.setUser(testUser);
    }

    private GraphNodeProjection createNode(Long id) {
        return new GraphNodeProjection() {
            @Override public Long getConceptId() { return id; }
            @Override public String getCanonicalName() { return "Node " + id; }
            @Override public String getConceptType() { return "TYPE"; }
            @Override public String getDescription() { return "Desc"; }
            @Override public Long getSourceCount() { return 1L; }
        };
    }

    private GraphEdgeProjection createEdge(Long id, Long src, Long target) {
        return new GraphEdgeProjection() {
            @Override public Long getRelationId() { return id; }
            @Override public Long getSourceConceptId() { return src; }
            @Override public Long getTargetConceptId() { return target; }
            @Override public RelationType getRelationType() { return RelationType.RELATED_TO; }
            @Override public Double getEdgeConfidence() { return 0.9; }
            @Override public Long getEvidenceCount() { return 1L; }
        };
    }

    private RelationEvidenceProjection createEvidence(Long id, Long relId) {
        return new RelationEvidenceProjection() {
            @Override public Long getRelationId() { return relId; }
            @Override public Long getChunkId() { return 100L; }
            @Override public Long getDocumentId() { return 200L; }
            @Override public String getDocumentTitle() { return "Title"; }
            @Override public String getEvidenceText() { return "Evidence"; }
            @Override public Double getConfidence() { return 0.9; }
        };
    }

    @Test
    void testGetGroundedKnowledgeGraph_Success() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L), createNode(2L)));
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(List.of(createEdge(100L, 1L, 2L)));

        KnowledgeGraphDto dto = service.getGroundedKnowledgeGraph(10L, 1L);

        assertThat(dto.getNotebookId()).isEqualTo(10L);
        assertThat(dto.getNodes()).hasSize(2);
        assertThat(dto.getEdges()).hasSize(1);
    }

    @Test
    void testGetGroundedKnowledgeGraph_Empty() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(Collections.emptyList());
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(Collections.emptyList());

        KnowledgeGraphDto dto = service.getGroundedKnowledgeGraph(10L, 1L);

        assertThat(dto.getNodes()).isEmpty();
        assertThat(dto.getEdges()).isEmpty();
    }

    @Test
    void testGetGroundedKnowledgeGraph_Unauthorized() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        assertThatThrownBy(() -> service.getGroundedKnowledgeGraph(10L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notebook not found");
    }

    @Test
    void testGetGroundedKnowledgeGraph_NotFound() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGroundedKnowledgeGraph(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notebook not found");
    }

    @Test
    void testGetGroundedKnowledgeGraph_DanglingEdgeRemoved() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L)));
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(List.of(createEdge(100L, 1L, 99L)));

        KnowledgeGraphDto dto = service.getGroundedKnowledgeGraph(10L, 1L);

        assertThat(dto.getNodes()).hasSize(1);
        assertThat(dto.getEdges()).isEmpty();
    }

    @Test
    void testGetGroundedKnowledgeGraph_TooManyNodes() {
        ReflectionTestUtils.setField(service, "maxNodes", 1);
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L), createNode(2L)));

        assertThatThrownBy(() -> service.getGroundedKnowledgeGraph(10L, 1L))
                .isInstanceOf(GraphTooLargeException.class);
    }

    @Test
    void testGetGroundedKnowledgeGraph_TooManyEdges() {
        ReflectionTestUtils.setField(service, "maxEdges", 0);
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L)));
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(List.of(createEdge(100L, 1L, 1L)));

        assertThatThrownBy(() -> service.getGroundedKnowledgeGraph(10L, 1L))
                .isInstanceOf(GraphTooLargeException.class);
    }

    @Test
    void testGetConceptDetail_Success() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodeByIdAndNotebookId(1L, 10L)).thenReturn(List.of(createNode(1L)));
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L), createNode(2L), createNode(3L)));
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(List.of(createEdge(100L, 1L, 2L), createEdge(101L, 3L, 1L)));
        when(graphQueryRepository.findRelationEvidenceInBatches(eq(10L), anyList())).thenReturn(List.of(createEvidence(1000L, 100L), createEvidence(1001L, 101L)));

        ConceptDetailDto dto = service.getConceptDetail(10L, 1L, 1L);

        assertThat(dto.getConcept().getId()).isEqualTo(1L);
        assertThat(dto.getOutgoingRelations()).hasSize(1);
        assertThat(dto.getOutgoingRelations().get(0).getId()).isEqualTo(100L);
        assertThat(dto.getIncomingRelations()).hasSize(1);
        assertThat(dto.getIncomingRelations().get(0).getId()).isEqualTo(101L);
        assertThat(dto.getRelationEvidence()).hasSize(2);
    }

    @Test
    void testGetConceptDetail_NotFound() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodeByIdAndNotebookId(99L, 10L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getConceptDetail(10L, 99L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetConceptDetail_DanglingEdgeRemoved() {
        when(notebookRepository.findById(10L)).thenReturn(Optional.of(testNotebook));
        when(graphQueryRepository.findGroundedNodeByIdAndNotebookId(1L, 10L)).thenReturn(List.of(createNode(1L)));
        
        // Notebook only has node 1 and 2 grounded. Node 99 is NOT grounded.
        when(graphQueryRepository.findGroundedNodesByNotebookId(10L)).thenReturn(List.of(createNode(1L), createNode(2L)));
        
        // Edges: 1->2 (valid), 1->99 (dangling target), 99->1 (dangling source)
        when(graphQueryRepository.findGroundedEdgesByNotebookId(10L)).thenReturn(List.of(
                createEdge(100L, 1L, 2L),
                createEdge(101L, 1L, 99L),
                createEdge(102L, 99L, 1L)
        ));
        
        when(graphQueryRepository.findRelationEvidenceInBatches(eq(10L), anyList())).thenReturn(List.of(createEvidence(1000L, 100L)));

        ConceptDetailDto dto = service.getConceptDetail(10L, 1L, 1L);

        assertThat(dto.getConcept().getId()).isEqualTo(1L);
        
        // 1->99 was filtered out
        assertThat(dto.getOutgoingRelations()).hasSize(1);
        assertThat(dto.getOutgoingRelations().get(0).getId()).isEqualTo(100L);
        
        // 99->1 was filtered out
        assertThat(dto.getIncomingRelations()).isEmpty();
        
        // Evidence query ONLY requested relation 100
        verify(graphQueryRepository).findRelationEvidenceInBatches(10L, List.of(100L));
    }
}
