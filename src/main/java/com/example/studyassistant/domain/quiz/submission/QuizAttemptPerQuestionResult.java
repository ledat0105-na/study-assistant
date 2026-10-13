package com.example.studyassistant.domain.quiz.submission;

import java.util.List;

public record QuizAttemptPerQuestionResult(
    int questionOrder,
    double pointsEarned,
    boolean isCorrect,
    List<Integer> correctOptionOrders,
    String explanation
) {
    public QuizAttemptPerQuestionResult(int questionOrder, double pointsEarned, boolean isCorrect) {
        this(questionOrder, pointsEarned, isCorrect, List.of(), null);
    }
}
