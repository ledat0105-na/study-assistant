package com.example.studyassistant.service.tutor;

import com.example.studyassistant.service.adaptive.AdaptiveCandidateService;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.retrieval.service.GroundedContextService;
import com.example.studyassistant.retrieval.model.GroundedContext;
import com.example.studyassistant.retrieval.service.GroundedCitationService;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.config.AiChatProperties;
import com.example.studyassistant.dto.TutorSessionState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutorTurnServiceTest {

    @Mock private TutorConversationReadService readService;
    @Mock private TutorTurnPersistenceService persistenceService;
    @Mock private AdaptiveCandidateService candidateService;
    @Mock private AdaptiveRecommendationService recommendationService;
    @Mock private TutorPolicyService policyService;
    @Mock private TutorPromptBuilder promptBuilder;
    @Mock private GroundedContextService groundedContextService;
    @Mock private GroundedCitationService citationService;
    @Mock private LlmProvider llmProvider;
    @Mock private AiChatProperties aiChatProperties;

    @InjectMocks
    private TutorTurnService service;
    
    private AdaptiveCandidateFactDto cand10;
    private AdaptiveCandidateFactDto cand20;

    @BeforeEach
    void setUp() {
        cand10 = new AdaptiveCandidateFactDto(10L, "Concept 10", MasteryState.UNSEEN, 0.0, 0, null, null, null, Collections.emptyList());
        cand20 = new AdaptiveCandidateFactDto(20L, "Concept 20", MasteryState.OBSERVED, 0.5, 3, null, null, null, Collections.singletonList(10L));
    }

    @Test
    void testExplicitTargetUsed() {
        TutorTurnCommand cmd = new TutorTurnCommand(1L, 100L, "Q", 10L, TutorRequestedMode.EXPLAIN);
        
        when(readService.findConversationState(1L, 100L)).thenReturn(new TutorConversationState(100L, 50L));
        when(candidateService.getCandidateFacts(1L, 50L)).thenReturn(Arrays.asList(cand10, cand20));
        
        when(policyService.decide(any())).thenReturn(new TutorPolicyDecision(10L, "Concept 10", 10L, "Concept 10", TutorMode.EXPLAIN));
        
        when(aiChatProperties.getContextWindowTokens()).thenReturn(8000);
        when(aiChatProperties.getMaxOutputTokens()).thenReturn(1000);
        when(aiChatProperties.getInputSafetyMarginTokens()).thenReturn(500);
        
        when(groundedContextService.buildContext(any(), any(), any(), anyLong())).thenReturn(new GroundedContext(true, "Data", Collections.emptyMap(), Collections.emptyList()));
        when(promptBuilder.buildPrompt(any(), any(), any(), any())).thenReturn(new TutorPrompt("sys", "user"));
        when(llmProvider.generate("sys", "user", 0.2)).thenReturn("Ans");
        
        GroundedAnswer ans = new GroundedAnswer("Clean Ans", Collections.singletonList(new CitationDto("S1", 1L, 1L, "Doc", "text", null, null)), true, false, "VALID");
        when(citationService.processCitations(any(), any())).thenReturn(ans);
        
        TutorTurnResult res = service.turn(cmd);
        
        assertThat(res.targetConceptId()).isEqualTo(10L);
        verify(recommendationService, never()).getRecommendations(any(), any(), any());
        verify(persistenceService).persistTurn(eq(1L), eq(100L), eq(10L), eq("Q"), eq("Clean Ans"), any());
    }
    
    @Test
    void testInsufficientEvidenceRefusal() {
        TutorTurnCommand cmd = new TutorTurnCommand(1L, 100L, "Q", null, null);
        
        when(readService.findConversationState(1L, 100L)).thenReturn(new TutorConversationState(100L, 50L));
        when(candidateService.getCandidateFacts(1L, 50L)).thenReturn(Collections.emptyList());
        
        when(readService.findSessionState(1L, 100L)).thenReturn(Optional.empty());
        when(recommendationService.getRecommendations(1L, 50L, 1)).thenReturn(Collections.emptyList());
        
        when(policyService.decide(any())).thenReturn(new TutorPolicyDecision(null, null, null, null, TutorMode.EXPLAIN));
        
        when(aiChatProperties.getContextWindowTokens()).thenReturn(8000);
        when(aiChatProperties.getMaxOutputTokens()).thenReturn(1000);
        when(aiChatProperties.getInputSafetyMarginTokens()).thenReturn(500);
        
        when(groundedContextService.buildContext(any(), any(), any(), anyLong())).thenReturn(new GroundedContext(false, "Data", Collections.emptyMap(), Collections.emptyList()));
        
        TutorTurnResult res = service.turn(cmd);
        
        assertThat(res.hasSufficientEvidence()).isFalse();
        assertThat(res.message()).contains("do not contain enough information");
        verify(llmProvider, never()).generate(any(), any(), anyDouble());
        verify(persistenceService).persistTurn(eq(1L), eq(100L), isNull(), eq("Q"), anyString(), any());
    }

    @Test
    void testExplainFailsOnZeroCitations() {
        TutorTurnCommand cmd = new TutorTurnCommand(1L, 100L, "Q", 10L, TutorRequestedMode.EXPLAIN);
        
        when(readService.findConversationState(1L, 100L)).thenReturn(new TutorConversationState(100L, 50L));
        when(candidateService.getCandidateFacts(1L, 50L)).thenReturn(Collections.singletonList(cand10));
        
        when(policyService.decide(any())).thenReturn(new TutorPolicyDecision(10L, "Concept 10", 10L, "Concept 10", TutorMode.EXPLAIN));
        
        when(aiChatProperties.getContextWindowTokens()).thenReturn(8000);
        when(aiChatProperties.getMaxOutputTokens()).thenReturn(1000);
        when(aiChatProperties.getInputSafetyMarginTokens()).thenReturn(500);
        
        when(groundedContextService.buildContext(any(), any(), any(), anyLong())).thenReturn(new GroundedContext(true, "Data", Collections.emptyMap(), Collections.emptyList()));
        when(promptBuilder.buildPrompt(any(), any(), any(), any())).thenReturn(new TutorPrompt("sys", "user"));
        when(llmProvider.generate("sys", "user", 0.2)).thenReturn("Ans without citations");
        
        GroundedAnswer ans = new GroundedAnswer("Ans without citations", Collections.emptyList(), true, false, "MISSING");
        when(citationService.processCitations(any(), any())).thenReturn(ans);
        
        assertThatThrownBy(() -> service.turn(cmd))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EXPLAIN mode requires at least one valid citation");
            
        verify(persistenceService, never()).persistTurn(any(), any(), any(), any(), any(), any());
    }
}