package com.example.studyassistant.domain.flashcard;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
@ToString
public class FlashcardScheduleResult {
    private final FlashcardScheduleState nextState;
    private final FlashcardRating rating;
    private final Instant reviewedAt;

    public FlashcardScheduleResult(FlashcardScheduleState nextState, FlashcardRating rating, Instant reviewedAt) {
        this.nextState = nextState;
        this.rating = rating;
        this.reviewedAt = reviewedAt != null ? reviewedAt.truncatedTo(ChronoUnit.MICROS) : null;
    }
}
