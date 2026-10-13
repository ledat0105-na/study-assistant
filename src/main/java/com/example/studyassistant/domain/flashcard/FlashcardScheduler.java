package com.example.studyassistant.domain.flashcard;

import java.time.Instant;
import java.util.Optional;

public interface FlashcardScheduler {
    FlashcardScheduleResult review(
            long flashcardId,
            Optional<FlashcardScheduleState> currentState,
            FlashcardRating rating,
            Instant reviewedAt
    );
}
