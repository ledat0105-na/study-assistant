package com.example.studyassistant.domain.flashcard;

import java.time.Instant;

public record FlashcardReviewResult(
    long flashcardId,
    String reviewToken,
    FlashcardRating rating,
    Instant reviewedAt,
    
    FlashcardScheduleStatus state,
    Integer step,
    double difficulty,
    double stability,
    Instant dueAt,
    Instant lastReviewAt,
    
    String algorithmVersion,
    String schedulerVersion,
    String schedulerConfigVersion,
    
    boolean replayed
) {}
