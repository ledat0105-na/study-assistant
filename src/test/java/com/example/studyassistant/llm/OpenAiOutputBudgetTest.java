package com.example.studyassistant.llm;

import com.example.studyassistant.config.AiChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiOutputBudgetTest {
    AiChatProperties configuration() throws Exception {
        Properties file = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) { file.load(input); }
        Map<String, Object> budget = Map.of(
                "ai.chat.context-window-tokens", file.getProperty("ai.chat.context-window-tokens"),
                "ai.chat.max-output-tokens", file.getProperty("ai.chat.max-output-tokens"));
        return new Binder(new MapConfigurationPropertySource(budget)).bind("ai.chat", Bindable.of(AiChatProperties.class)).get();
    }
    @Test void configuredBudgetReachesProviderRequest() throws Exception {
        AiChatProperties properties = configuration(); properties.validate();
        assertEquals(16000, properties.getContextWindowTokens()); assertEquals(4000, properties.getMaxOutputTokens());
        RestTemplate http = mock(RestTemplate.class);
        OpenAiLlmProvider provider = new OpenAiLlmProvider(http);
        ReflectionTestUtils.setField(provider, "openaiApiKey", "mock-key");
        ReflectionTestUtils.setField(provider, "aiChatProperties", properties);
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(invocation -> {
            HttpEntity<?> request = invocation.getArgument(1);
            assertEquals(4000, ((Map<?, ?>) request.getBody()).get("max_tokens"));
            return ResponseEntity.ok(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "{}")))));
        });
        assertEquals("{}", provider.generate("system", "untrusted source", 0.1));
    }
    @Test void truncatedJsonIsStillRejected() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        OpenAiLlmProvider provider = new OpenAiLlmProvider(http);
        ReflectionTestUtils.setField(provider, "openaiApiKey", "mock-key");
        ReflectionTestUtils.setField(provider, "aiChatProperties", configuration());
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(ResponseEntity.ok(
                Map.of("choices", List.of(Map.of("finish_reason", "length", "message", Map.of("content", "{incomplete"))))));
        RuntimeException error = assertThrows(RuntimeException.class, () -> provider.generate("system", "source", 0.1));
        assertTrue(error.getCause().getMessage().contains("truncated"));
    }
}
