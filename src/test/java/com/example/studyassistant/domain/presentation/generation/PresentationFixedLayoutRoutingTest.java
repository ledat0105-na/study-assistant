package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.retrieval.service.RetrievalService;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PresentationFixedLayoutRoutingTest {
    @Test void fixedModeUsesContentPromptAndRealComposerWithCitations() throws Exception {
        var llm = mock(LlmProvider.class);
        var retrieval = mock(RetrievalService.class);
        var evidence = List.of(RetrievedChunk.builder().chunkId(44L).documentId(10L).indexVersion("v1")
                .content("Activity là một màn hình ứng dụng Android.").build());
        var result = mock(RetrievalResult.class);
        when(result.getChunks()).thenReturn(evidence);
        when(retrieval.search(eq(7L), eq(2L), anyString(), eq(List.of(10L)))).thenReturn(result);
        String json = "{\"title\":\"Activity\",\"bullets\":[\"Một màn hình ứng dụng Android\"],\"sourceRefs\":[\"[S1]\"]}";
        when(llm.generate(anyString(), anyString(), eq(0.2))).thenReturn(json);
        var processor = new PresentationSlideGenerationProcessor(null,null,null,null,null,null,null,null,null,
                llm,retrieval,null,null,null,new ObjectMapper());
        ReflectionTestUtils.setField(processor, "layoutMode", "FIXED");
        var plan = new PlannedSlide(1,"Activity","CONTENT",List.of("Màn hình"));
        Object prepared = ReflectionTestUtils.invokeMethod(processor, "prepareSlide", 7L,2L,List.of(10L),plan);
        assertThat(prepared).isNotNull();
        verify(llm).generate(eq(SlideContentComposer.systemPrompt()), contains("[Source S1]"), eq(0.2));
        var parsed = processor.parseFixedLayout(json,evidence,plan);
        assertThat(parsed.elements()).isNotEmpty();
        assertThat(parsed.sourceRefs()).containsExactly("S1");
        assertThatThrownBy(() -> processor.parseFixedLayout(json,List.of(),plan))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class);
    }
}
