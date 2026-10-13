package com.example.studyassistant.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.chat")
@Data
public class AiChatProperties {

    private String model = "gpt-4o-mini";
    private int contextWindowTokens = 8000;
    private int maxOutputTokens = 1000;
    private int inputSafetyMarginTokens = 500;

    @PostConstruct
    public void validate() {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalStateException("ai.chat.model cannot be null or blank");
        }
        if (contextWindowTokens <= 0) {
            throw new IllegalStateException("ai.chat.context-window-tokens must be > 0");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalStateException("ai.chat.max-output-tokens must be > 0");
        }
        if (inputSafetyMarginTokens < 0) {
            throw new IllegalStateException("ai.chat.input-safety-margin-tokens must be >= 0");
        }
        long context = contextWindowTokens;
        long output = maxOutputTokens;
        long margin = inputSafetyMarginTokens;
        if (context - output - margin <= 0) {
            throw new IllegalStateException("usable input tokens (context - maxOutput - safetyMargin) must be > 0");
        }
    }
}
