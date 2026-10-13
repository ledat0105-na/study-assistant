package com.example.studyassistant.domain.flashcard;

import com.example.studyassistant.domain.flashcard.exception.*;
import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.entity.FlashcardReviewEvent;
import com.example.studyassistant.entity.FlashcardReviewState;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.FlashcardRepository;
import com.example.studyassistant.repository.FlashcardReviewEventRepository;
import com.example.studyassistant.repository.FlashcardReviewStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlashcardReviewService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardReviewStateRepository stateRepository;
    private final FlashcardReviewEventRepository eventRepository;
    private final FlashcardScheduler scheduler;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public FlashcardReviewResult processReview(FlashcardReviewCommand cmd) {
        validateCommand(cmd);

        // Idempotency precheck (lock-free)
        Optional<FlashcardReviewEvent> existing = eventRepository.findByUserIdAndReviewToken(cmd.userId(), cmd.reviewToken());
        if (existing.isPresent()) {
            return verifyAndReplay(existing.get(), cmd);
        }

        try {
            return transactionTemplate.execute(status -> executeReviewTransaction(cmd));
        } catch (DataIntegrityViolationException e) {
            // DB constraint violation could be duplicate unique token due to race condition
            existing = eventRepository.findByUserIdAndReviewToken(cmd.userId(), cmd.reviewToken());
            if (existing.isPresent()) {
                return verifyAndReplay(existing.get(), cmd);
            }
            throw e;
        }
    }

    private void validateCommand(FlashcardReviewCommand cmd) {
        if (cmd.reviewToken() == null || cmd.reviewToken().isBlank()) {
            throw new InvalidReviewTokenException("Review token must not be blank.");
        }
        try {
            UUID.fromString(cmd.reviewToken());
        } catch (IllegalArgumentException e) {
            throw new InvalidReviewTokenException("Review token must be a valid UUID.");
        }
        if (cmd.rating() == null) {
            throw new IllegalArgumentException("Rating cannot be null.");
        }
        if (cmd.durationMs() != null && (cmd.durationMs() < 0 || cmd.durationMs() > 3600000)) {
            throw new InvalidReviewDurationException("Duration must be between 0 and 3600000 ms.");
        }
    }

    private FlashcardReviewResult verifyAndReplay(FlashcardReviewEvent event, FlashcardReviewCommand cmd) {
        if (event.getFlashcard().getId() != cmd.flashcardId() || event.getRating() != cmd.rating()) {
            throw new ReviewTokenConflictException("Token exists with different payload.");
        }
        FlashcardReviewState state = stateRepository.findByUserIdAndFlashcardId(cmd.userId(), cmd.flashcardId())
                .orElseThrow(() -> new IllegalStateException("State missing for replayed event."));

        return new FlashcardReviewResult(
                cmd.flashcardId(),
                cmd.reviewToken(),
                cmd.rating(),
                event.getReviewedAt(),
                state.getState(),
                state.getStep(),
                state.getDifficulty(),
                state.getStability(),
                state.getDueAt(),
                state.getLastReviewAt(),
                event.getAlgorithmVersion(),
                event.getSchedulerVersion(),
                event.getSchedulerConfigVersion(),
                true
        );
    }

    private FlashcardReviewResult executeReviewTransaction(FlashcardReviewCommand cmd) {
        // Parent Lock EVERY Review
        Flashcard flashcard = flashcardRepository.findByIdForUpdate(cmd.flashcardId())
                .orElseThrow(() -> new FlashcardNotFoundException("Flashcard not found"));

        if (flashcard.getDeletedAt() != null) {
            throw new FlashcardNotFoundException("Flashcard is deleted.");
        }
        User owner = resolveOwner(flashcard, cmd.userId());

        // Idempotency re-check after lock
        Optional<FlashcardReviewEvent> existing = eventRepository.findByUserIdAndReviewToken(cmd.userId(), cmd.reviewToken());
        if (existing.isPresent()) {
            return verifyAndReplay(existing.get(), cmd);
        }

        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Optional<FlashcardReviewState> stateOpt = stateRepository.findByUserIdAndFlashcardId(cmd.userId(), cmd.flashcardId());
        Optional<FlashcardScheduleState> currentSchedule = stateOpt.map(s -> FlashcardScheduleState.builder()
                .state(s.getState())
                .step(s.getStep())
                .difficulty(s.getDifficulty())
                .stability(s.getStability())
                .dueAt(s.getDueAt())
                .lastReviewAt(s.getLastReviewAt())
                .build()
        );

        FlashcardScheduleResult result = scheduler.review(
                cmd.flashcardId(),
                currentSchedule,
                cmd.rating(),
                reviewedAt
        );

        FlashcardReviewEvent event = new FlashcardReviewEvent();
        event.setUser(owner);
        event.setFlashcard(flashcard);
        event.setReviewToken(cmd.reviewToken());
        event.setRating(cmd.rating());
        event.setDurationMs(cmd.durationMs());
        event.setReviewedAt(reviewedAt);
        event.setAlgorithmVersion(FlashcardFsrsConfigConstants.ALGORITHM_VERSION);
        event.setSchedulerVersion(FlashcardFsrsConfigConstants.SCHEDULER_VERSION);
        event.setSchedulerConfigVersion(FlashcardFsrsConfigConstants.SCHEDULER_CONFIG_VERSION);

        if (stateOpt.isPresent()) {
            FlashcardReviewState s = stateOpt.get();
            event.setStateBefore(s.getState());
            event.setStepBefore(s.getStep());
            event.setDifficultyBefore(s.getDifficulty());
            event.setStabilityBefore(s.getStability());
            event.setDueBefore(s.getDueAt());
        }
        
        event.setStateAfter(result.getNextState().getState());
        event.setStepAfter(result.getNextState().getStep());
        event.setDifficultyAfter(result.getNextState().getDifficulty());
        event.setStabilityAfter(result.getNextState().getStability());
        event.setDueAfter(result.getNextState().getDueAt());

        eventRepository.save(event);

        FlashcardReviewState stateToUpdate = stateOpt.orElseGet(() -> {
            FlashcardReviewState s = new FlashcardReviewState();
            s.setUser(owner);
            s.setFlashcard(flashcard);
            return s;
        });

        stateToUpdate.setState(result.getNextState().getState());
        stateToUpdate.setStep(result.getNextState().getStep());
        stateToUpdate.setDifficulty(result.getNextState().getDifficulty());
        stateToUpdate.setStability(result.getNextState().getStability());
        stateToUpdate.setDueAt(result.getNextState().getDueAt());
        stateToUpdate.setLastReviewAt(result.getNextState().getLastReviewAt());
        
        stateRepository.save(stateToUpdate);

        return new FlashcardReviewResult(
                cmd.flashcardId(),
                cmd.reviewToken(),
                cmd.rating(),
                reviewedAt,
                result.getNextState().getState(),
                result.getNextState().getStep(),
                result.getNextState().getDifficulty(),
                result.getNextState().getStability(),
                result.getNextState().getDueAt(),
                result.getNextState().getLastReviewAt(),
                FlashcardFsrsConfigConstants.ALGORITHM_VERSION,
                FlashcardFsrsConfigConstants.SCHEDULER_VERSION,
                FlashcardFsrsConfigConstants.SCHEDULER_CONFIG_VERSION,
                false
        );
    }

    private User resolveOwner(Flashcard f, long userId) {
        User ownerA = null;
        if (f.getDocument() != null && f.getDocument().getUser() != null) {
            ownerA = f.getDocument().getUser();
        }
        User ownerB = null;
        if (f.getTopic() != null && f.getTopic().getDocument() != null && f.getTopic().getDocument().getUser() != null) {
            ownerB = f.getTopic().getDocument().getUser();
        }
        if (ownerA != null && ownerB != null && !ownerA.getId().equals(ownerB.getId())) {
            throw new FlashcardNotFoundException("Flashcard not found");
        }
        User actualOwner = ownerA != null ? ownerA : ownerB;
        if (actualOwner != null && actualOwner.getId().equals(userId)) {
            return actualOwner;
        }
        throw new FlashcardNotFoundException("Flashcard not found");
    }
}
