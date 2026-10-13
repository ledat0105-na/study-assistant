package com.example.studyassistant.domain.flashcard.generation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "flashcard.generation")
@Data
public class FlashcardGenerationProperties {
    private int defaultCount = 10;
    private int maxCount = 60;
    private int minAutoCount = 8;
    private int maxAutoCount = 50;
    private double cardsPerChunk = 1.0;
    private int cardsPerBatch = 8;
    private int maxSourcesPerCard = 3;
    private int maxQuestionLength = 500;
    private int maxAnswerLength = 2000;
    private double temperature = 0.2;
}
