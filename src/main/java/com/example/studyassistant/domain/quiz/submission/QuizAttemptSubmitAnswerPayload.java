package com.example.studyassistant.domain.quiz.submission;

import java.util.List;

public record QuizAttemptSubmitAnswerPayload(
    int questionOrder,
    List<Integer> selectedOptionOrders,
    String shortAnswerText
) {}
