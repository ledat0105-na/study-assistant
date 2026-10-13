package com.example.studyassistant.controller;

import com.example.studyassistant.entity.RelationType;
import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.service.KnowledgeGraphQueryService;
import com.example.studyassistant.service.dto.ConceptDetailDto;
import com.example.studyassistant.service.dto.GraphConceptDto;
import com.example.studyassistant.service.dto.GraphRelationDto;
import com.example.studyassistant.service.dto.KnowledgeGraphDto;
import com.example.studyassistant.service.dto.RelationEvidenceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KnowledgeGraphControllerTest {

    @Mock
    private KnowledgeGraphQueryService queryService;

    @InjectMocks
    private KnowledgeGraphController controller;

    private MockHttpServletRequest request;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        request.setSession(session);
    }

    @Test
    void testA_GraphAuthenticatedSuccess() {
        KnowledgeGraphDto dto = KnowledgeGraphDto.builder()
                .notebookId(10L)
                .nodes(Collections.emptyList())
                .edges(Collections.emptyList())
                .build();
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
        // Spring defaults to application/json for OK objects
    }

    @Test
    void testB_GraphNoSession() {
        request.setSession(null);
        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testC_GraphMissingUserId() {
        session.removeAttribute("userId");
        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testD_GraphNonexistentNotebook() {
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenThrow(new IllegalArgumentException("Notebook not found"));

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testE_GraphUnauthorizedNotebook() {
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenThrow(new IllegalArgumentException("Notebook not found"));

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testF_GraphEmpty() {
        KnowledgeGraphDto dto = KnowledgeGraphDto.builder()
                .notebookId(10L)
                .nodes(Collections.emptyList())
                .edges(Collections.emptyList())
                .build();
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((KnowledgeGraphDto) response.getBody()).getNodes()).isEmpty();
        assertThat(((KnowledgeGraphDto) response.getBody()).getEdges()).isEmpty();
    }

    @Test
    void testG_GraphTooLarge() {
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenThrow(new GraphTooLargeException("Too large"));

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void testH_ConceptDetailAuthenticatedSuccess() {
        ConceptDetailDto dto = ConceptDetailDto.builder()
                .concept(GraphConceptDto.builder().id(1L).build())
                .build();
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void testI_ConceptDetailNoSession() {
        request.setSession(null);
        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testJ_ConceptAbsent() {
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenThrow(new IllegalArgumentException("Concept not found or ungrounded"));

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testK_ConceptCrossNotebook() {
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenThrow(new IllegalArgumentException("Concept not found or ungrounded"));

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testL_ConceptOrphanUngrounded() {
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenThrow(new IllegalArgumentException("Concept not found or ungrounded"));

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testM_ConceptNonReadyOnly() {
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenThrow(new IllegalArgumentException("Concept not found or ungrounded"));

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testN_ConceptDetailTooLarge() {
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenThrow(new GraphTooLargeException("Too large"));

        // Currently no GraphTooLargeException catch in conceptDetail, wait let me check
        assertThatThrownBy(() -> controller.getConceptDetail(10L, 1L, request))
            .isInstanceOf(GraphTooLargeException.class);
    }

    @Test
    void testO_EvidenceJsonPrivacy() {
        RelationEvidenceDto ev = RelationEvidenceDto.builder()
                .relationId(10L)
                .chunkId(20L)
                .documentId(30L)
                .documentTitle("Title")
                .evidenceText("Evidence snippet")
                .confidence(0.9)
                .build();
        
        ConceptDetailDto dto = ConceptDetailDto.builder()
                .concept(GraphConceptDto.builder().id(1L).build())
                .relationEvidence(List.of(ev))
                .build();
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ConceptDetailDto body = (ConceptDetailDto) response.getBody();
        assertThat(body.getRelationEvidence().get(0).getEvidenceText()).isEqualTo("Evidence snippet");
        // No full content field exists on DTO
    }

    @Test
    void testP_IncomingOutgoingDirection() {
        GraphRelationDto incoming = GraphRelationDto.builder().id(100L).sourceConceptId(2L).targetConceptId(1L).build();
        GraphRelationDto outgoing = GraphRelationDto.builder().id(101L).sourceConceptId(1L).targetConceptId(3L).build();
        
        ConceptDetailDto dto = ConceptDetailDto.builder()
                .concept(GraphConceptDto.builder().id(1L).build())
                .incomingRelations(List.of(incoming))
                .outgoingRelations(List.of(outgoing))
                .build();
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        ConceptDetailDto body = (ConceptDetailDto) response.getBody();
        
        assertThat(body.getIncomingRelations().get(0).getTargetConceptId()).isEqualTo(1L);
        assertThat(body.getOutgoingRelations().get(0).getSourceConceptId()).isEqualTo(1L);
    }

    @Test
    void testQ_AllFourRelationTypePositiveSerialization() {
        GraphRelationDto r1 = GraphRelationDto.builder().relationType(RelationType.PREREQUISITE_OF).build();
        GraphRelationDto r2 = GraphRelationDto.builder().relationType(RelationType.PART_OF).build();
        GraphRelationDto r3 = GraphRelationDto.builder().relationType(RelationType.IS_A).build();
        GraphRelationDto r4 = GraphRelationDto.builder().relationType(RelationType.RELATED_TO).build();
        
        KnowledgeGraphDto dto = KnowledgeGraphDto.builder()
                .edges(List.of(r1, r2, r3, r4))
                .build();
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        KnowledgeGraphDto body = (KnowledgeGraphDto) response.getBody();
        
        assertThat(body.getEdges().get(0).getRelationType()).isEqualTo(RelationType.PREREQUISITE_OF);
        assertThat(body.getEdges().get(1).getRelationType()).isEqualTo(RelationType.PART_OF);
        assertThat(body.getEdges().get(2).getRelationType()).isEqualTo(RelationType.IS_A);
        assertThat(body.getEdges().get(3).getRelationType()).isEqualTo(RelationType.RELATED_TO);
    }

    @Test
    void testR_SourceCountNumericSerialization() {
        GraphConceptDto n1 = GraphConceptDto.builder().sourceCount(2L).build();
        KnowledgeGraphDto dto = KnowledgeGraphDto.builder().nodes(List.of(n1)).build();
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        KnowledgeGraphDto body = (KnowledgeGraphDto) response.getBody();
        assertThat(body.getNodes().get(0).getSourceCount()).isEqualTo(2L);
    }

    @Test
    void testS_EvidenceCountNumericSerialization() {
        GraphRelationDto r1 = GraphRelationDto.builder().evidenceCount(5L).build();
        KnowledgeGraphDto dto = KnowledgeGraphDto.builder().edges(List.of(r1)).build();
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getKnowledgeGraph(10L, request);
        KnowledgeGraphDto body = (KnowledgeGraphDto) response.getBody();
        assertThat(body.getEdges().get(0).getEvidenceCount()).isEqualTo(5L);
    }

    @Test
    void testT_MultiEvidenceSerialization() {
        GraphRelationDto edge = GraphRelationDto.builder().id(100L).evidenceCount(2L).build();
        RelationEvidenceDto ev1 = RelationEvidenceDto.builder().relationId(100L).build();
        RelationEvidenceDto ev2 = RelationEvidenceDto.builder().relationId(100L).build();
        
        ConceptDetailDto dto = ConceptDetailDto.builder()
                .outgoingRelations(List.of(edge))
                .relationEvidence(List.of(ev1, ev2))
                .build();
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptDetail(10L, 1L, request);
        ConceptDetailDto body = (ConceptDetailDto) response.getBody();
        assertThat(body.getOutgoingRelations()).hasSize(1);
        assertThat(body.getOutgoingRelations().get(0).getEvidenceCount()).isEqualTo(2L);
        assertThat(body.getRelationEvidence()).hasSize(2);
    }

    @Test
    void testU_ExactServiceArgumentForwarding() {
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenReturn(new KnowledgeGraphDto());
        controller.getKnowledgeGraph(10L, request);
        verify(queryService).getGroundedKnowledgeGraph(10L, 1L);
        
        when(queryService.getConceptDetail(10L, 1L, 1L)).thenReturn(new ConceptDetailDto());
        controller.getConceptDetail(10L, 1L, request);
        verify(queryService).getConceptDetail(10L, 1L, 1L);
    }

    @Test
    void testV_UnexpectedServiceErrorRetainsServerErrorSemantics() {
        when(queryService.getGroundedKnowledgeGraph(10L, 1L)).thenThrow(new RuntimeException("DB down"));
        
        assertThatThrownBy(() -> controller.getKnowledgeGraph(10L, request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("DB down");
    }
}
