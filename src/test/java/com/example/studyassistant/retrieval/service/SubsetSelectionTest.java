package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.config.AiChatProperties;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.service.GraphRagContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubsetSelectionTest {

    private RetrievalService retrievalService; // REAL instance
    private LlmProvider llmProvider;
    private GraphRagContextService graphRagContextService;
    private AiChatProperties aiChatProperties;
    private TokenCounter tokenCounter;
    private GroundedAnswerService groundedAnswerService;

    @BeforeEach
    void setUp() {
        // We use a real RetrievalService but mock its search method. Wait, we can't easily do that without Mockito spy.
        RetrievalService realService = new RetrievalService();
        ReflectionTestUtils.setField(realService, "minDenseSimilarity", 0.8);
        retrievalService = org.mockito.Mockito.spy(realService);

        llmProvider = mock(LlmProvider.class);
        graphRagContextService = mock(GraphRagContextService.class);
        aiChatProperties = mock(AiChatProperties.class);
        
        // Let's use real simple token counter for realistic sizes
        tokenCounter = new SimpleTokenCounter();

        GroundedContextService contextService = new GroundedContextService(retrievalService, tokenCounter);
        groundedAnswerService = new GroundedAnswerService(
                llmProvider, graphRagContextService, aiChatProperties, tokenCounter, contextService, new GroundedCitationService()
        );
        ReflectionTestUtils.setField(groundedAnswerService, "temperature", 0.1);

        lenient().when(aiChatProperties.getContextWindowTokens()).thenReturn(1000);
        lenient().when(aiChatProperties.getMaxOutputTokens()).thenReturn(20);
        lenient().when(aiChatProperties.getInputSafetyMarginTokens()).thenReturn(10);
        
        lenient().when(graphRagContextService.buildContext(anyLong(), anyList(), anyInt())).thenReturn("");
        lenient().when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("Ans [S1] [S2]");
    }

    private RetrievedChunk createChunk(Long id, String text, Double denseScore, Integer keywordRank) {
        return RetrievedChunk.builder()
                .chunkId(id)
                .documentId(1L)
                .content(text)
                .denseScore(denseScore)
                .keywordRank(keywordRank)
                .build();
    }

    @Test
    void testA_NonPrefixValidSubset() {
        // Real policy: denseScore >= 0.8 is sufficient.
        // A insufficient, B insufficient, C sufficient.
        // Total budget is small.
        String largeText = "A ".repeat(1000); 
        String smallTextA = "B ".repeat(10); 
        String smallTextB = "C ".repeat(10);

        RetrievedChunk a = createChunk(1L, largeText, 0.5, 10); // Insufficient
        RetrievedChunk b = createChunk(2L, smallTextA, 0.5, 10); // Insufficient
        RetrievedChunk c = createChunk(3L, smallTextB, 0.9, 10); // Sufficient (END anchor)

        List<RetrievedChunk> chunks = Arrays.asList(a, b, c);
        RetrievalResult mockResult = new RetrievalResult(chunks, true, 200);
        org.mockito.Mockito.doReturn(mockResult).when(retrievalService).search(anyLong(), anyLong(), anyString());

        GroundedAnswer answer = groundedAnswerService.answer(1L, 1L, "Q");
        
        assertThat(answer.isHasSufficientEvidence()).isTrue();
        // A is dropped because it's too large. B and C fit.
        // Wait, C is the anchor. subset = [C]. Then try to add A: too large. Try to add B: fits.
        // So subset is [B, C]. Order is preserved. S numbering starts at S1.
        // B -> S1, C -> S2
        assertThat(answer.getCitations()).hasSize(2);
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo(2L); // B
        assertThat(answer.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(answer.getCitations().get(1).getChunkId()).isEqualTo(3L); // C
        assertThat(answer.getCitations().get(1).getCitationId()).isEqualTo("S2");
    }

    @Test
    void testC_MiddleAnchor() {
        String largeText = "A ".repeat(1000);
        String smallText = "B ".repeat(10);

        RetrievedChunk a = createChunk(1L, smallText, 0.5, 10); // Insufficient
        RetrievedChunk b = createChunk(2L, smallText, 0.9, 10); // Sufficient (MIDDLE)
        RetrievedChunk c = createChunk(3L, largeText, 0.5, 10); // Insufficient

        List<RetrievedChunk> chunks = Arrays.asList(a, b, c);
        RetrievalResult mockResult = new RetrievalResult(chunks, true, 200);
        org.mockito.Mockito.doReturn(mockResult).when(retrievalService).search(anyLong(), anyLong(), anyString());

        GroundedAnswer answer = groundedAnswerService.answer(1L, 1L, "Q");
        assertThat(answer.isHasSufficientEvidence()).isTrue();
        assertThat(answer.getCitations()).hasSize(2);
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo(1L); // A
        assertThat(answer.getCitations().get(1).getChunkId()).isEqualTo(2L); // B
    }

    @Test
    void testD_StartAnchor() {
        String largeText = "A ".repeat(1000);
        String smallText = "B ".repeat(10);

        RetrievedChunk a = createChunk(1L, smallText, 0.9, 10); // Sufficient (START)
        RetrievedChunk b = createChunk(2L, largeText, 0.5, 10); // Insufficient
        RetrievedChunk c = createChunk(3L, smallText, 0.5, 10); // Insufficient

        List<RetrievedChunk> chunks = Arrays.asList(a, b, c);
        RetrievalResult mockResult = new RetrievalResult(chunks, true, 200);
        org.mockito.Mockito.doReturn(mockResult).when(retrievalService).search(anyLong(), anyLong(), anyString());

        GroundedAnswer answer = groundedAnswerService.answer(1L, 1L, "Q");
        assertThat(answer.isHasSufficientEvidence()).isTrue();
        assertThat(answer.getCitations()).hasSize(2);
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo(1L); // A
        assertThat(answer.getCitations().get(1).getChunkId()).isEqualTo(3L); // C
    }

    @Test
    void testF_NoValidEvidenceSubsetFits() {
        String largeText = "A ".repeat(1000); // Exceeds budget

        RetrievedChunk a = createChunk(1L, largeText, 0.9, 10); // Sufficient but too big
        
        List<RetrievedChunk> chunks = Arrays.asList(a);
        RetrievalResult mockResult = new RetrievalResult(chunks, true, 200);
        org.mockito.Mockito.doReturn(mockResult).when(retrievalService).search(anyLong(), anyLong(), anyString());

        GroundedAnswer answer = groundedAnswerService.answer(1L, 1L, "Q");
        
        assertThat(answer.isHasSufficientEvidence()).isFalse();
        assertThat(answer.getAnswer()).contains("do not contain enough information");
    }
    
    @Test
    void testE_MultipleEvidenceAnchorsDeterministic() {
        String smallText = "B ".repeat(10);

        RetrievedChunk a = createChunk(1L, smallText, 0.9, 10); // Sufficient
        RetrievedChunk b = createChunk(2L, smallText, 0.9, 10); // Sufficient

        // Total 2 chunks, both fit. Should include both, order preserved.
        List<RetrievedChunk> chunks = Arrays.asList(a, b);
        RetrievalResult mockResult = new RetrievalResult(chunks, true, 200);
        org.mockito.Mockito.doReturn(mockResult).when(retrievalService).search(anyLong(), anyLong(), anyString());

        GroundedAnswer answer = groundedAnswerService.answer(1L, 1L, "Q");
        assertThat(answer.isHasSufficientEvidence()).isTrue();
        assertThat(answer.getCitations()).hasSize(2);
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo(1L); // A
        assertThat(answer.getCitations().get(1).getChunkId()).isEqualTo(2L); // B
    }
}
