package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentKnowledgeGraphGenerationServiceTest {
    @Mock DocumentRepository documents;
    @Mock DocumentChunkRepository chunks;
    @Mock AIConceptExtractor concepts;
    @Mock AIConceptRelationExtractor relations;
    @Mock KnowledgeGraphQueryService queries;
    @InjectMocks DocumentKnowledgeGraphGenerationService service;
    Document document;
    DocumentChunk first, second;

    @BeforeEach void setup() {
        User owner = new User(); owner.setId(1L);
        document = new Document(); document.setId(10L); document.setUser(owner);
        document.setStatus("READY"); document.setActiveIndexVersion("v1");
        first = chunk(11L); second = chunk(12L);
        when(documents.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentIdAndIndexVersionOrderByChunkIndexAsc(10L, "v1")).thenReturn(List.of(first, second));
    }
    DocumentChunk chunk(Long id) {
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(id); chunk.setDocument(document);
        chunk.setIndexVersion("v1"); return chunk;
    }
    void statuses(String firstStatus, String secondStatus) {
        when(concepts.extractConcepts(any(KnowledgeScope.class), eq(first)))
                .thenReturn(ConceptExtractionResult.builder().status(firstStatus).build());
        when(concepts.extractConcepts(any(KnowledgeScope.class), eq(second)))
                .thenReturn(ConceptExtractionResult.builder().status(secondStatus).build());
        lenient().when(relations.extractRelationsForChunk(any(KnowledgeScope.class), any()))
                .thenReturn(ConceptRelationExtractionResult.builder().status("NO_CANDIDATES").build());
    }
    @Test void failedChunkDoesNotPreventLaterSuccess() {
        statuses("PROVIDER_FAILURE", "SUCCESS");
        KnowledgeGraphDto graph = graph();
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(graph);
        assertSame(graph, service.generate(1L, 10L));
        verify(concepts).extractConcepts(any(KnowledgeScope.class), eq(second));
    }
    @Test void partialSuccessIsUsable() {
        statuses("PARTIAL_SUCCESS", "PARSE_FAILURE");
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(graph());
        assertNotNull(service.generate(1L, 10L));
    }
    @Test void allFailedChunksReturnFailureAndLeaveReadyIndex() {
        statuses("PROVIDER_FAILURE", "CANDIDATE_LIMIT_EXCEEDED");
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(KnowledgeGraphDto.builder().nodes(List.of()).edges(List.of()).build());
        assertEquals(GraphFailureCode.AI_PROVIDER_ERROR, assertThrows(KnowledgeGraphGenerationException.class, () -> service.generate(1L, 10L)).getCode());
        assertEquals("READY", document.getStatus()); assertEquals("v1", document.getActiveIndexVersion());
    }
    @Test void successfulExtractionStillRejectsChangedIndex() {
        statuses("SUCCESS", "SUCCESS");
        when(documents.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenAnswer(invocation -> {
            if (mockingDetails(concepts).getInvocations().size() == 2) document.setActiveIndexVersion("v2");
            return Optional.of(document);
        });
        assertThrows(IllegalStateException.class, () -> service.generate(1L, 10L));
        verifyNoInteractions(queries);
    }

    KnowledgeGraphDto graph() {
        return KnowledgeGraphDto.builder().nodes(List.of(GraphConceptDto.builder().id(1L).build())).edges(List.of()).build();
    }
    @Test void conceptsAndRelationsAreFullSuccess() {
        statuses("SUCCESS", "SUCCESS");
        when(relations.extractRelationsForChunk(any(KnowledgeScope.class), any()))
                .thenReturn(ConceptRelationExtractionResult.builder().status("SUCCESS").build());
        var graph = graph(); graph.setEdges(List.of(GraphRelationDto.builder().id(2L).build()));
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(graph);
        assertEquals("FULL_SUCCESS", service.generate(1L, 10L).getGenerationStatus());
    }
    @Test void legitimateEmptyRelationsAreSuccess() {
        statuses("SUCCESS", "SUCCESS");
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(graph());
        assertEquals("SUCCESS", service.generate(1L, 10L).getGenerationStatus());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PROVIDER_FAILURE","PARSE_FAILURE","CANDIDATE_LIMIT_EXCEEDED","FAILED"})
    void relationFailureRetainsGroundedConcepts(String status) {
        statuses("SUCCESS", "SUCCESS");
        when(relations.extractRelationsForChunk(any(KnowledgeScope.class), any()))
                .thenReturn(ConceptRelationExtractionResult.builder().status(status).build());
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(graph());
        var result = service.generate(1L, 10L);
        assertEquals("PARTIAL_SUCCESS", result.getGenerationStatus());
        assertEquals("PARTIAL_GRAPH", result.getCode());
        assertEquals(1, result.getNodes().size());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"FAILED,GRAPH_PERSISTENCE_ERROR", "PARSE_FAILURE,AI_SCHEMA_ERROR", "SUCCESS,NO_CONCEPTS"})
    void emptyGroundedGraphFailsWithCorrectCause(String status, GraphFailureCode code) {
        statuses(status, status);
        when(queries.getGroundedKnowledgeGraphForDocument(10L, 1L)).thenReturn(KnowledgeGraphDto.builder().nodes(List.of()).edges(List.of()).build());
        assertEquals(code, assertThrows(KnowledgeGraphGenerationException.class, () -> service.generate(1L, 10L)).getCode());
    }
}
