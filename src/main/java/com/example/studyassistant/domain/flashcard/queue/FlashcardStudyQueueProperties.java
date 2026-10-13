package com.example.studyassistant.domain.flashcard.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "flashcard.queue")
public class FlashcardStudyQueueProperties {
    private int defaultDueLimit = 50;
    private int maxDueLimit = 200;
    private int defaultNewLimit = 20;
    private int maxNewLimit = 50;

    public int getDefaultDueLimit() { return defaultDueLimit; }
    public void setDefaultDueLimit(int defaultDueLimit) { this.defaultDueLimit = defaultDueLimit; }
    public int getMaxDueLimit() { return maxDueLimit; }
    public void setMaxDueLimit(int maxDueLimit) { this.maxDueLimit = maxDueLimit; }
    public int getDefaultNewLimit() { return defaultNewLimit; }
    public void setDefaultNewLimit(int defaultNewLimit) { this.defaultNewLimit = defaultNewLimit; }
    public int getMaxNewLimit() { return maxNewLimit; }
    public void setMaxNewLimit(int maxNewLimit) { this.maxNewLimit = maxNewLimit; }
}
