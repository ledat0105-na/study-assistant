package com.example.studyassistant.domain.flashcard;

public record FlashcardReviewCommand(
    long userId,
    long flashcardId,
    String reviewToken,
    FlashcardRating rating,
    Integer durationMs
) {}
