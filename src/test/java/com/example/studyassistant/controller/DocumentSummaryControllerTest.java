package com.example.studyassistant.controller;

import com.example.studyassistant.domain.summary.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentSummaryControllerTest {
    private MockHttpServletRequest authenticated() {
        var request = new MockHttpServletRequest(); request.getSession().setAttribute("userId",7L); return request;
    }

    @Test void existingDocumentWithoutSummaryReturnsExplicitCacheMiss() {
        var service = mock(DocumentSummaryService.class);
        when(service.getCached(7L,3710L)).thenReturn(Optional.empty());
        var result = new DocumentSummaryController(service).getSummary(3710L,authenticated());
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(new DocumentSummaryController.SummaryCacheMiss(3710L,false,null));
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service,never()).generate(anyLong(),anyLong());
    }

    @Test void missingOrInaccessibleDocumentRemains404WithSpecificCode() {
        var service = mock(DocumentSummaryService.class);
        when(service.getCached(7L,3710L)).thenThrow(new DocumentSummaryNotFoundException());
        var result = new DocumentSummaryController(service).getSummary(3710L,authenticated());
        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(((Map<?,?>)result.getBody()).get("code")).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test void generationUsesSessionOwnerAndReturnsResult() {
        var service = mock(DocumentSummaryService.class);
        var summary = new SummaryResponse(3710L,"Tóm tắt",false,"v1",null,List.of(),null);
        when(service.generate(7L,3710L)).thenReturn(summary);
        var result = new DocumentSummaryController(service).generateSummary(3710L,authenticated());
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(summary);
    }

    @Test void unauthenticatedReadDoesNotAccessDocument() {
        var service = mock(DocumentSummaryService.class);
        var result = new DocumentSummaryController(service).getSummary(3710L,new MockHttpServletRequest());
        assertThat(result.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(service);
    }

    @Test void canonicalHttpMappingsExistAndDoNotInvokeServiceWithoutSession() throws Exception {
        var service = mock(DocumentSummaryService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DocumentSummaryController(service)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/documents/3719/summary"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/documents/3719/summary/generate"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void cacheMissJsonContractIsExplicitOnHttpRoute() throws Exception {
        var service = mock(DocumentSummaryService.class);
        when(service.getCached(7L,3719L)).thenReturn(Optional.empty());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DocumentSummaryController(service)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/documents/3719/summary").sessionAttr("userId",7L))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"docId\":3719,\"cached\":false,\"summary\":null}"));
    }

    @Test void legacySummaryAliasDelegatesToCanonicalCacheContract() {
        var service = mock(DocumentSummaryService.class);
        when(service.getCached(7L,3719L)).thenReturn(Optional.empty());
        var canonical = new DocumentSummaryController(service);
        var legacy = new AiController();
        org.springframework.test.util.ReflectionTestUtils.setField(legacy,"documentSummaryController",canonical);
        var response = legacy.summarize(3719L,authenticated());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new DocumentSummaryController.SummaryCacheMiss(3719L,false,null));
        verify(service,never()).generate(anyLong(),anyLong());
    }
}
