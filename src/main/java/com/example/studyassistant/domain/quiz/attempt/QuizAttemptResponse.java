package com.example.studyassistant.domain.quiz.attempt;

import com.example.studyassistant.entity.QuizMode;
import java.time.LocalDateTime;
import java.util.List;

public record QuizAttemptResponse(
    long attemptId,
    long quizId,
    QuizMode mode,
    LocalDateTime startedAt,
    Integer timeLimitSeconds,
    List<QuizAttemptQuestionDto> questions
) {}
