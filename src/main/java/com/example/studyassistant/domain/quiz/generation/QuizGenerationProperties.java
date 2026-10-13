package com.example.studyassistant.domain.quiz.generation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "quiz.generation")
@Data
public class QuizGenerationProperties {
    private int defaultCount = 5;
    private int maxCount = 50;
    private int minAutoCount = 5;
    private int maxAutoCount = 40;
    private double questionsPerChunk = 0.7;
    private int questionsPerBatch = 5;
    private int maxSourcesPerQuestion = 3;
    private double temperature = 0.2;
}
