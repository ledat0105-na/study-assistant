package com.example.studyassistant.domain.quiz.generation;

import java.util.List;

public record GroundedQuizGenerationResult(
        long quizId,
        int requestedCount,
        int generatedCount,
        List<GeneratedQuizQuestionResult> questions
) {}
