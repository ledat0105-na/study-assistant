package com.example.studyassistant.domain.flashcard;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class FsrsFlashcardScheduler implements FlashcardScheduler {

    private final Scheduler scheduler;

    public FsrsFlashcardScheduler(
            @Value("${flashcard.fsrs.desired-retention:0.90}") double desiredRetention,
            @Value("${flashcard.fsrs.maximum-interval-days:36500}") int maximumInterval,
            @Value("${flashcard.fsrs.fuzzing-enabled:true}") boolean fuzzingEnabled) {
        
        this.scheduler = Scheduler.builder()
                .desiredRetention(desiredRetention)
                .maximumInterval(maximumInterval)
                .enableFuzzing(fuzzingEnabled)
                .build();
    }

    @Override
    public FlashcardScheduleResult review(long flashcardId, Optional<FlashcardScheduleState> currentState, FlashcardRating rating, Instant reviewedAt) {
        Card card = currentState.map(this::toLibraryCard).orElseGet(() -> createNewLibraryCard(reviewedAt));
        Rating libraryRating = toLibraryRating(rating);
        
        CardAndReviewLog result = scheduler.reviewCard(card, libraryRating, reviewedAt, null);
        
        FlashcardScheduleState nextState = toAppState(result.card());
        return new FlashcardScheduleResult(nextState, rating, reviewedAt);
    }

    private Card createNewLibraryCard(Instant reviewedAt) {
        return Card.builder()
                .due(reviewedAt)
                .build();
    }

    private Card toLibraryCard(FlashcardScheduleState appState) {
        Card.Builder builder = Card.builder()
                .state(toLibraryState(appState.getState()))
                .stability(appState.getStability())
                .difficulty(appState.getDifficulty())
                .due(appState.getDueAt());
                
        if (appState.getStep() != null) {
            builder.step(appState.getStep());
        }
        if (appState.getLastReviewAt() != null) {
            builder.lastReview(appState.getLastReviewAt());
        }
        return builder.build();
    }

    private FlashcardScheduleState toAppState(Card card) {
        return FlashcardScheduleState.builder()
                .state(toAppScheduleStatus(card.getState()))
                .step(card.getStep())
                .stability(card.getStability())
                .difficulty(card.getDifficulty())
                .dueAt(card.getDue())
                .lastReviewAt(card.getLastReview())
                .build();
    }

    private Rating toLibraryRating(FlashcardRating rating) {
        return switch (rating) {
            case AGAIN -> Rating.AGAIN;
            case HARD -> Rating.HARD;
            case GOOD -> Rating.GOOD;
            case EASY -> Rating.EASY;
        };
    }

    private State toLibraryState(FlashcardScheduleStatus status) {
        return switch (status) {
            case LEARNING -> State.LEARNING;
            case REVIEW -> State.REVIEW;
            case RELEARNING -> State.RELEARNING;
        };
    }

    private FlashcardScheduleStatus toAppScheduleStatus(State state) {
        return switch (state) {
            case LEARNING -> FlashcardScheduleStatus.LEARNING;
            case REVIEW -> FlashcardScheduleStatus.REVIEW;
            case RELEARNING -> FlashcardScheduleStatus.RELEARNING;
        };
    }
}
