package com.example.studyassistant.domain.quiz.attempt;

public record QuizAttemptStartCommand(
    long userId,
    long quizId
) {}
