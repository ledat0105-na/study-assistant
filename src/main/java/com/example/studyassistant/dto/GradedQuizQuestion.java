package com.example.studyassistant.dto;

import com.example.studyassistant.entity.QuizQuestion;

public record GradedQuizQuestion(
    QuizQuestion question,
    boolean answered,
    boolean correct
) {}
