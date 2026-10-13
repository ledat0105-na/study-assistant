package com.example.studyassistant.domain.quiz.submission;

import java.util.List;

/** HTTP input: identity and attempt ID come exclusively from session and path. */
public record QuizAttemptSubmitRequest(
        String submissionToken,
        List<QuizAttemptSubmitAnswerPayload> answers
) {}
