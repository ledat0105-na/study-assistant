package com.example.studyassistant.domain.flashcard;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
@ToString
public class FlashcardScheduleState {
    private final FlashcardScheduleStatus state;
    private final Integer step;
    private final Double stability;
    private final Double difficulty;
    private final Instant dueAt;
    private final Instant lastReviewAt;

    public FlashcardScheduleState(FlashcardScheduleStatus state, Integer step, Double stability, Double difficulty, Instant dueAt, Instant lastReviewAt) {
        this.state = state;
        this.step = step;
        this.stability = stability;
        this.difficulty = difficulty;
        // Truncate to MICROS for MySQL DATETIME(6) persistence boundary safety
        this.dueAt = dueAt != null ? dueAt.truncatedTo(ChronoUnit.MICROS) : null;
        this.lastReviewAt = lastReviewAt != null ? lastReviewAt.truncatedTo(ChronoUnit.MICROS) : null;
    }
}
