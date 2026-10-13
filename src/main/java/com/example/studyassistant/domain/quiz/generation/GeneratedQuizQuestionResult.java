package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.QuizQuestionType;
import java.util.List;

public record GeneratedQuizQuestionResult(
        long questionId,
        QuizQuestionType questionType,
        String questionText,
        Double points,
        String contentHash,
        List<Long> sourceChunkIds
) {}
