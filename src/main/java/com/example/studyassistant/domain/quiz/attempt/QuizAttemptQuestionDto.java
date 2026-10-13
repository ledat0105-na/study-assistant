package com.example.studyassistant.domain.quiz.attempt;

import com.example.studyassistant.entity.QuizQuestionType;
import java.util.List;

public record QuizAttemptQuestionDto(
    long questionId,
    int questionOrder,
    String questionText,
    QuizQuestionType questionType,
    Double pointsPossible,
    List<QuizAttemptOptionDto> options
) {}
