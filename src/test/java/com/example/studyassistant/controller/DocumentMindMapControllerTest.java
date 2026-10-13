package com.example.studyassistant.controller;

import com.example.studyassistant.service.*;
import com.example.studyassistant.service.dto.KnowledgeGraphDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMindMapControllerTest {
    @Mock KnowledgeGraphQueryService query;
    @Mock DocumentKnowledgeGraphGenerationService generation;
    @InjectMocks DocumentMindMapController controller;
    MockHttpServletRequest request() {
        var request = new MockHttpServletRequest(); var session = new MockHttpSession();
        session.setAttribute("userId", 1L); request.setSession(session); return request;
    }
    @Test void unauthenticatedPostDenied() {
        assertThat(controller.generate(3710L, new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(generation);
    }
    @Test void postUsesSessionOwnerAndCanonicalGeneration() {
        when(generation.generate(1L, 3710L)).thenReturn(new KnowledgeGraphDto());
        assertThat(controller.generate(3710L, request()).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(query);
    }
    @Test void readOnlyCallsQuery() {
        when(query.getGroundedKnowledgeGraphForDocument(3710L, 1L)).thenReturn(new KnowledgeGraphDto());
        assertThat(controller.getDocumentMindMap(3710L, request()).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(generation);
    }
    @Test void processingConflictIsFriendly() {
        when(generation.generate(1L, 3710L)).thenThrow(new IllegalStateException("technical detail"));
        var response = controller.generate(3710L, request());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().toString()).doesNotContain("technical detail");
    }
    @Test void foreignOwnerReturnsNotFound() {
        when(generation.generate(1L, 3710L)).thenThrow(new IllegalArgumentException("secret"));
        assertThat(controller.generate(3710L, request()).getStatusCode().value()).isEqualTo(404);
    }
    @Test void providerFailureIsFriendly() {
        when(generation.generate(1L, 3710L)).thenThrow(new KnowledgeGraphGenerationException());
        assertThat(controller.generate(3710L, request()).getStatusCode().value()).isEqualTo(502);
    }

    @Test void graphTooLargeHasSameCodeAndStatusOnReadAndGenerate() {
        when(query.getGroundedKnowledgeGraphForDocument(3710L, 1L)).thenThrow(new com.example.studyassistant.exception.GraphTooLargeException("secret"));
        when(generation.generate(1L, 3710L)).thenThrow(new com.example.studyassistant.exception.GraphTooLargeException("secret"));
        var get = controller.getDocumentMindMap(3710L, request());
        var post = controller.generate(3710L, request());
        assertThat(get.getStatusCode().value()).isEqualTo(413);
        assertThat(post.getStatusCode()).isEqualTo(get.getStatusCode());
        assertThat(post.getBody()).isEqualTo(get.getBody());
        assertThat(post.getBody().toString()).contains("GRAPH_TOO_LARGE").doesNotContain("secret");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"GRAPH_PERSISTENCE_ERROR,500","AI_SCHEMA_ERROR,422","AI_PROVIDER_ERROR,502"})
    void safeFailureClassification(GraphFailureCode code, int status) {
        when(generation.generate(1L, 3710L)).thenThrow(new KnowledgeGraphGenerationException(code));
        var response = controller.generate(3710L, request());
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().toString()).contains(code.name());
    }
    @Test void rawLocalIntegrityFailureNeverBecomes502OrLeaksSql() {
        when(generation.generate(1L, 3710L)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("secret SQL"));
        var response = controller.generate(3710L, request());
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().toString()).contains("GRAPH_PERSISTENCE_ERROR").doesNotContain("secret SQL");
    }
}
