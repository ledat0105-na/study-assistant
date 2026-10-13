package com.example.studyassistant.controller;

import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.dto.adaptive.AdaptiveReasonCode;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class AdaptiveRecommendationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdaptiveRecommendationService recommendationService;

    @InjectMocks
    private AdaptiveRecommendationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // C1: AUTHENTICATED SUCCESS
    @Test
    void c1_authenticatedSuccess() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        AdaptiveRecommendationDto dto1 = new AdaptiveRecommendationDto();
        dto1.setConceptId(10L);
        dto1.setConceptName("A");
        
        AdaptiveRecommendationDto dto2 = new AdaptiveRecommendationDto();
        dto2.setConceptId(20L);
        dto2.setConceptName("B");

        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/notebooks/42/recommendations")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(AdaptiveRecommendationService.POLICY_VERSION))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.recommendations[0].conceptId").value(10))
                .andExpect(jsonPath("$.recommendations[1].conceptId").value(20));

        verify(recommendationService).getRecommendations(7L, 42L, null);
    }

    // C2: EMPTY
    @Test
    void c2_empty() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(AdaptiveRecommendationService.POLICY_VERSION))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    // C3: NO SESSION
    @Test
    void c3_noSession() throws Exception {
        mockMvc.perform(get("/api/notebooks/42/recommendations"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recommendationService);
    }

    // C4: SESSION WITHOUT USERID
    @Test
    void c4_sessionWithoutUserId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // no userId attribute
        
        mockMvc.perform(get("/api/notebooks/42/recommendations")
                .session(session))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recommendationService);
    }

    // C5: OWNED/FOREIGN/MISSING MASKING
    @Test
    void c5_ownedForeignMissingMasking() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        when(recommendationService.getRecommendations(7L, 999L, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/999/recommendations")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(AdaptiveRecommendationService.POLICY_VERSION))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    // C6: LIMIT OMITTED
    @Test
    void c6_limitOmitted() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations").session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, null);
    }

    // C7: LIMIT 1
    @Test
    void c7_limit1() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(recommendationService.getRecommendations(7L, 42L, 1)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations?limit=1").session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, 1);
    }

    // C8: LIMIT 21
    @Test
    void c8_limit21() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(recommendationService.getRecommendations(7L, 42L, 21)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations?limit=21").session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, 21);
    }

    // C9: LIMIT 0
    @Test
    void c9_limit0() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(recommendationService.getRecommendations(7L, 42L, 0)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations?limit=0").session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, 0);
    }

    // C10: LIMIT NEGATIVE
    @Test
    void c10_limitNegative() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(recommendationService.getRecommendations(7L, 42L, -5)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations?limit=-5").session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, -5);
    }

    // C11: MALFORMED LIMIT
    @Test
    void c11_malformedLimit() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        mockMvc.perform(get("/api/notebooks/42/recommendations?limit=abc").session(session))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    // C12: MALFORMED NOTEBOOK ID
    @Test
    void c12_malformedNotebookId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        mockMvc.perform(get("/api/notebooks/abc/recommendations").session(session))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    // C13, C14, C15: REASON AND MASTERY SERIALIZATION, ONE-SHOT RESPONSE
    @Test
    void c13_c14_c15_reasonMasterySerializationOneShot() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        AdaptiveRecommendationDto dto = new AdaptiveRecommendationDto();
        dto.setConceptId(10L);
        dto.setConceptName("Graph Theory");
        dto.setMasteryState(MasteryState.OBSERVED);
        dto.setMasteryEstimate(1.0);
        dto.setEvidenceCount(1);
        dto.setReasonCodes(List.of(AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY));

        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notebooks/42/recommendations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].reasonCodes[0]").value("INSUFFICIENT_EVIDENCE_FOR_MASTERY"))
                .andExpect(jsonPath("$.recommendations[0].masteryState").value("OBSERVED"))
                .andExpect(jsonPath("$.recommendations[0].masteryEstimate").value(1.0))
                .andExpect(jsonPath("$.recommendations[0].evidenceCount").value(1));
    }

    // C16: ORDER PRESERVATION
    @Test
    void c16_orderPreservation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        AdaptiveRecommendationDto dtoB = new AdaptiveRecommendationDto();
        dtoB.setConceptId(2L);
        AdaptiveRecommendationDto dtoA = new AdaptiveRecommendationDto();
        dtoA.setConceptId(1L);
        AdaptiveRecommendationDto dtoC = new AdaptiveRecommendationDto();
        dtoC.setConceptId(3L);

        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(List.of(dtoB, dtoA, dtoC));

        mockMvc.perform(get("/api/notebooks/42/recommendations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].conceptId").value(2))
                .andExpect(jsonPath("$.recommendations[1].conceptId").value(1))
                .andExpect(jsonPath("$.recommendations[2].conceptId").value(3));
    }

    // C17: NO INTERNAL FIELDS
    @Test
    void c17_noInternalFields() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        AdaptiveRecommendationDto dto = new AdaptiveRecommendationDto();
        dto.setConceptId(10L);
        dto.setConceptName("A");
        dto.setMasteryState(MasteryState.UNSEEN);

        when(recommendationService.getRecommendations(7L, 42L, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notebooks/42/recommendations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].firstDocumentId").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].firstChunkIndex").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].lastEvidenceAt").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].prerequisiteConceptIds").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].userId").doesNotExist());
    }

    // SECURITY: USERID OVERRIDE
    @Test
    void security_userIdOverrideNotPossible() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        when(recommendationService.getRecommendations(anyLong(), anyLong(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notebooks/42/recommendations?userId=999")
                .session(session))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(7L, 42L, null);
    }
}
