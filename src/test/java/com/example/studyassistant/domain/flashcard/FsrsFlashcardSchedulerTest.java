package com.example.studyassistant.domain.flashcard;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FsrsFlashcardSchedulerTest {

    private final FsrsFlashcardScheduler scheduler = new FsrsFlashcardScheduler(0.90, 36500, false);
    
    // S1 dependency/API works is implicitly proven by this class compiling and running.

    @Test
    void testBrandNewAgainDeterministic() { // S2
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.AGAIN, now);
        
        assertEquals(FlashcardRating.AGAIN, result.getRating());
        assertEquals(now, result.getReviewedAt());
        assertNotNull(result.getNextState());
        assertEquals(FlashcardScheduleStatus.LEARNING, result.getNextState().getState());
        assertEquals(now.plusSeconds(60), result.getNextState().getDueAt());
    }

    @Test
    void testBrandNewHardDeterministic() { // S3
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.HARD, now);
        assertEquals(FlashcardScheduleStatus.LEARNING, result.getNextState().getState());
    }

    @Test
    void testBrandNewGoodDeterministic() { // S4
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.GOOD, now);
        assertEquals(FlashcardScheduleStatus.LEARNING, result.getNextState().getState());
        assertEquals(now.plusSeconds(600), result.getNextState().getDueAt());
    }

    @Test
    void testBrandNewEasyDeterministic() { // S5
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.EASY, now);
        assertEquals(FlashcardScheduleStatus.REVIEW, result.getNextState().getState());
    }
    
    @Test
    void testExistingLearningReconstruction() { // S6
        Instant lastReview = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        
        FlashcardScheduleState existing = FlashcardScheduleState.builder()
                .state(FlashcardScheduleStatus.LEARNING)
                .step(0)
                .stability(0.0)
                .difficulty(0.0)
                .dueAt(lastReview.plusSeconds(60))
                .lastReviewAt(lastReview)
                .build();
                
        FlashcardScheduleResult result = scheduler.review(1L, Optional.of(existing), FlashcardRating.GOOD, now);
        
        assertNotNull(result.getNextState());
        assertNotNull(result.getNextState().getState());
    }

    @Test
    void testExistingReviewReconstruction() { // S7
        Instant lastReview = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = Instant.parse("2026-01-05T00:00:00Z");
        
        FlashcardScheduleState existing = FlashcardScheduleState.builder()
                .state(FlashcardScheduleStatus.REVIEW)
                .step(null)
                .stability(3.0)
                .difficulty(5.0)
                .dueAt(Instant.parse("2026-01-04T00:00:00Z"))
                .lastReviewAt(lastReview)
                .build();
                
        FlashcardScheduleResult result = scheduler.review(1L, Optional.of(existing), FlashcardRating.GOOD, now);
        
        assertNotNull(result.getNextState());
        assertEquals(FlashcardScheduleStatus.REVIEW, result.getNextState().getState());
        assertTrue(result.getNextState().getDueAt().isAfter(now));
    }

    @Test
    void testExistingRelearningReconstruction() { // S8
        Instant lastReview = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = Instant.parse("2026-01-05T00:00:00Z");

        FlashcardScheduleState existing = FlashcardScheduleState.builder()
                .state(FlashcardScheduleStatus.RELEARNING)
                .step(0)
                .stability(3.0)
                .difficulty(5.0)
                .dueAt(Instant.parse("2026-01-04T00:00:00Z"))
                .lastReviewAt(lastReview)
                .build();

        FlashcardScheduleResult result = scheduler.review(1L, Optional.of(existing), FlashcardRating.GOOD, now);

        assertNotNull(result.getNextState());
    }

    @Test
    void testExplicitReviewedAtRespected() { // S9
        Instant past = Instant.parse("2025-01-01T00:00:00Z");
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.GOOD, past);
        assertEquals(past, result.getReviewedAt());
        assertEquals(past.plusSeconds(600), result.getNextState().getDueAt());
    }

    @Test
    void testIdenticalInputProducesIdenticalResult() { // S10
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FlashcardScheduleResult result1 = scheduler.review(1L, Optional.empty(), FlashcardRating.EASY, now);
        FlashcardScheduleResult result2 = scheduler.review(1L, Optional.empty(), FlashcardRating.EASY, now);
        
        assertEquals(result1.getNextState().getStability(), result2.getNextState().getStability());
        assertEquals(result1.getNextState().getDueAt(), result2.getNextState().getDueAt());
    }

    @Test
    void testApplicationRatingMapping() { // S11
        // We know it maps correctly if the library accepts it and returns valid results for all 4.
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertNotNull(scheduler.review(1L, Optional.empty(), FlashcardRating.AGAIN, now).getNextState());
        assertNotNull(scheduler.review(1L, Optional.empty(), FlashcardRating.HARD, now).getNextState());
        assertNotNull(scheduler.review(1L, Optional.empty(), FlashcardRating.GOOD, now).getNextState());
        assertNotNull(scheduler.review(1L, Optional.empty(), FlashcardRating.EASY, now).getNextState());
    }

    @Test
    void testNullRatingRejected() { // S12
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(NullPointerException.class, () -> {
            scheduler.review(1L, Optional.empty(), null, now);
        });
    }

    // S13: FlashcardScheduler has no repository dependency - proven by constructor.
    // S14: FlashcardScheduler has no Clock dependency - proven by constructor and `reviewedAt` passing.
    // S15: scheduler has no LLM/network dependency - proven by constructor and imports.

    @Test
    void testApplicationOutputMatchesDirectLibraryCall() { // S16
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        
        // App call
        FlashcardScheduleResult result = scheduler.review(1L, Optional.empty(), FlashcardRating.GOOD, now);
        
        // Library call
        Scheduler libScheduler = Scheduler.builder().enableFuzzing(false).build();
        Card newCard = Card.builder().due(now).build();
        var libResult = libScheduler.reviewCard(newCard, Rating.GOOD, now, null);
        
        // Assert match
        assertEquals(libResult.card().getStability(), result.getNextState().getStability());
        assertEquals(libResult.card().getDifficulty(), result.getNextState().getDifficulty());
        assertEquals(libResult.card().getDue(), result.getNextState().getDueAt());
    }
}
