package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.QuizMode;
import com.example.studyassistant.entity.QuizQuestionType;
import com.example.studyassistant.entity.QuizScopeType;

import java.util.List;

public record GroundedQuizGenerationCommand(
        Long userId,
        QuizScopeType scopeType,
        Long notebookId,
        Long documentId,
        Long topicId,
        Long scopeConceptId,
        QuizMode mode,
        Integer questionCount,
        List<QuizQuestionType> allowedQuestionTypes,
        String title,
        Integer timeLimitSeconds,
        Double passThreshold
) {}
