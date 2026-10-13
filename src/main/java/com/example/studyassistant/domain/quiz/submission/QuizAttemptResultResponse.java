package com.example.studyassistant.domain.quiz.submission;

import java.time.LocalDateTime;
import java.util.List;

public record QuizAttemptResultResponse(
    long attemptId,
    long quizId,
    String status,
    Double score,
    Integer totalQuestions,
    Integer correctAnswers,
    LocalDateTime completedAt,
    List<QuizAttemptPerQuestionResult> perQuestionResults
) {}
