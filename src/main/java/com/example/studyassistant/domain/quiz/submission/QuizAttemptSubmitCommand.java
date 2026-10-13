package com.example.studyassistant.domain.quiz.submission;

import java.util.List;

public record QuizAttemptSubmitCommand(
    long userId,
    long attemptId,
    String submissionToken,
    List<QuizAttemptSubmitAnswerPayload> answers
) {}
