package com.example.studyassistant.domain.flashcard;

import com.example.studyassistant.domain.flashcard.exception.*;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlashcardReviewServiceTest {

    private FlashcardReviewService service;
    private FlashcardRepository flashcardRepo;
    private FlashcardReviewStateRepository stateRepo;
    private FlashcardReviewEventRepository eventRepo;
    private FsrsFlashcardScheduler scheduler;
    private Clock clock;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        flashcardRepo = mock(FlashcardRepository.class);
        stateRepo = mock(FlashcardReviewStateRepository.class);
        eventRepo = mock(FlashcardReviewEventRepository.class);
        scheduler = mock(FsrsFlashcardScheduler.class);
        clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
        transactionTemplate = mock(TransactionTemplate.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        service = new FlashcardReviewService(flashcardRepo, stateRepo, eventRepo, scheduler, clock, transactionTemplate);
    }

    private Flashcard createValidFlashcard(long userId) {
        User user = new User();
        user.setId(userId);
        Document doc = new Document();
        doc.setUser(user);
        Flashcard f = new Flashcard();
        f.setId(10L);
        f.setDocument(doc);
        return f;
    }

    private FlashcardScheduleResult createMockResult(FlashcardRating rating) {
        FlashcardScheduleState state = FlashcardScheduleState.builder()
                .state(FlashcardScheduleStatus.LEARNING)
                .step(1)
                .difficulty(5.0)
                .stability(2.0)
                .dueAt(Instant.parse("2026-01-01T12:10:00Z"))
                .lastReviewAt(Instant.parse("2026-01-01T12:00:00Z"))
                .build();
        return new FlashcardScheduleResult(state, rating, Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void testR1_FirstReviewCreatesEventAndState() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(eventRepo.findByUserIdAndReviewToken(1L, "token-1")).thenReturn(Optional.empty());
        when(stateRepo.findByUserIdAndFlashcardId(1L, 10L)).thenReturn(Optional.empty());
        when(scheduler.review(eq(10L), eq(Optional.empty()), eq(FlashcardRating.GOOD), any())).thenReturn(createMockResult(FlashcardRating.GOOD));

        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 15000);
        FlashcardReviewResult res = service.processReview(cmd);

        verify(eventRepo).save(any(FlashcardReviewEvent.class));
        verify(stateRepo).save(any(FlashcardReviewState.class));
        assertThat(res.replayed()).isFalse();
    }

    @Test
    void testR2_SubsequentReviewUpdatesSameState() {
        Flashcard f = createValidFlashcard(1L);
        FlashcardReviewState existingState = new FlashcardReviewState();
        existingState.setState(FlashcardScheduleStatus.LEARNING);
        existingState.setDifficulty(5.0);
        existingState.setStability(2.0);
        existingState.setDueAt(Instant.now());
        
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(stateRepo.findByUserIdAndFlashcardId(1L, 10L)).thenReturn(Optional.of(existingState));
        when(scheduler.review(eq(10L), any(), eq(FlashcardRating.GOOD), any())).thenReturn(createMockResult(FlashcardRating.GOOD));

        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 15000);
        service.processReview(cmd);

        verify(eventRepo).save(any(FlashcardReviewEvent.class));
        verify(stateRepo).save(existingState); // Should save the same instance
    }

    @Test
    void testR3_AgainForwardedExactly() { testForwardedExactly(FlashcardRating.AGAIN); }
    
    @Test
    void testR4_HardForwardedExactly() { testForwardedExactly(FlashcardRating.HARD); }
    
    @Test
    void testR5_GoodForwardedExactly() { testForwardedExactly(FlashcardRating.GOOD); }
    
    @Test
    void testR6_EasyForwardedExactly() { testForwardedExactly(FlashcardRating.EASY); }

    private void testForwardedExactly(FlashcardRating rating) {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), eq(rating), any())).thenReturn(createMockResult(rating));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), rating, 1000);
        service.processReview(cmd);
        
        verify(scheduler).review(eq(10L), any(), eq(rating), any());
    }

    @Test
    void testR7_SameTokenSamePayloadReplayed() {
        FlashcardReviewEvent ev = new FlashcardReviewEvent();
        Flashcard f = createValidFlashcard(1L);
        ev.setFlashcard(f);
        ev.setRating(FlashcardRating.GOOD);
        ev.setReviewedAt(Instant.now());
        ev.setAlgorithmVersion("v");
        ev.setSchedulerVersion("v");
        ev.setSchedulerConfigVersion("v");
        
        when(eventRepo.findByUserIdAndReviewToken(1L, "b328a95d-db96-4171-89d5-78eec9b96434")).thenReturn(Optional.of(ev));
        
        FlashcardReviewState state = new FlashcardReviewState();
        state.setState(FlashcardScheduleStatus.LEARNING);
        state.setDifficulty(5.0);
        state.setStability(2.0);
        state.setDueAt(Instant.now());
        when(stateRepo.findByUserIdAndFlashcardId(1L, 10L)).thenReturn(Optional.of(state));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, "b328a95d-db96-4171-89d5-78eec9b96434", FlashcardRating.GOOD, 1000);
        FlashcardReviewResult res = service.processReview(cmd);
        
        assertThat(res.replayed()).isTrue();
    }

    @Test
    void testR8_ReplayDoesNotInvokeScheduler() {
        testR7_SameTokenSamePayloadReplayed();
        verify(scheduler, never()).review(anyLong(), any(), any(), any());
    }

    @Test
    void testR9_SameTokenDifferentRatingConflict() {
        FlashcardReviewEvent ev = new FlashcardReviewEvent();
        Flashcard f = createValidFlashcard(1L);
        ev.setFlashcard(f);
        ev.setRating(FlashcardRating.EASY);
        
        when(eventRepo.findByUserIdAndReviewToken(1L, "b328a95d-db96-4171-89d5-78eec9b96434")).thenReturn(Optional.of(ev));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, "b328a95d-db96-4171-89d5-78eec9b96434", FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(ReviewTokenConflictException.class);
    }
    
    @Test
    void testR10_SameTokenDifferentCardConflict() {
        FlashcardReviewEvent ev = new FlashcardReviewEvent();
        Flashcard f = createValidFlashcard(1L);
        f.setId(11L); // different card
        ev.setFlashcard(f);
        ev.setRating(FlashcardRating.GOOD);
        
        when(eventRepo.findByUserIdAndReviewToken(1L, "b328a95d-db96-4171-89d5-78eec9b96434")).thenReturn(Optional.of(ev));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, "b328a95d-db96-4171-89d5-78eec9b96434", FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(ReviewTokenConflictException.class);
    }

    @Test
    void testR11_MissingCardRejected() {
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(FlashcardNotFoundException.class);
    }

    @Test
    void testR12_ForeignCardRejected() {
        Flashcard f = createValidFlashcard(2L); // belongs to user 2
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(FlashcardNotFoundException.class);
    }

    @Test
    void testR13_DeletedCardRejected() {
        Flashcard f = createValidFlashcard(1L);
        f.setDeletedAt(Instant.now());
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(FlashcardNotFoundException.class);
    }

    @Test
    void testR14_InvalidTokenRejected() {
        FlashcardReviewCommand cmd1 = new FlashcardReviewCommand(1L, 10L, null, FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd1)).isInstanceOf(InvalidReviewTokenException.class);
        
        FlashcardReviewCommand cmd2 = new FlashcardReviewCommand(1L, 10L, "   ", FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd2)).isInstanceOf(InvalidReviewTokenException.class);
        
        FlashcardReviewCommand cmd3 = new FlashcardReviewCommand(1L, 10L, "invalid-uuid-format", FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd3)).isInstanceOf(InvalidReviewTokenException.class);
    }

    @Test
    void testR15_DurationValidation() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        // Null accepted
        service.processReview(new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, null));
        // 0 accepted
        service.processReview(new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 0));
        // 3600000 accepted
        service.processReview(new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 3600000));
        
        // Negative rejected
        assertThatThrownBy(() -> service.processReview(new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, -1)))
                .isInstanceOf(InvalidReviewDurationException.class);
                
        // > 3600000 rejected
        assertThatThrownBy(() -> service.processReview(new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 3600001)))
                .isInstanceOf(InvalidReviewDurationException.class);
    }

    @Test
    void testR16_ServerClockAuthoritative() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        FlashcardReviewResult res = service.processReview(cmd);
        
        assertThat(res.reviewedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z").truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void testR17_ClientCannotProvideReviewedAt() {
        // Confirmed by inspecting FlashcardReviewCommand structure (no reviewedAt field)
        assertThat(FlashcardReviewCommand.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("reviewedAt"));
    }

    @Test
    void testR18_FirstReviewBeforeSnapshotNull() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        service.processReview(cmd);
        
        ArgumentCaptor<FlashcardReviewEvent> captor = ArgumentCaptor.forClass(FlashcardReviewEvent.class);
        verify(eventRepo).save(captor.capture());
        
        FlashcardReviewEvent ev = captor.getValue();
        assertThat(ev.getStateBefore()).isNull();
        assertThat(ev.getStepBefore()).isNull();
        assertThat(ev.getDifficultyBefore()).isNull();
        assertThat(ev.getStabilityBefore()).isNull();
        assertThat(ev.getDueBefore()).isNull();
    }

    @Test
    void testR19_SubsequentReviewBeforeSnapshotExact() {
        Flashcard f = createValidFlashcard(1L);
        FlashcardReviewState state = new FlashcardReviewState();
        state.setState(FlashcardScheduleStatus.LEARNING);
        state.setStep(1);
        state.setDifficulty(4.0);
        state.setStability(2.5);
        state.setDueAt(Instant.parse("2026-01-01T10:00:00Z"));
        
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(stateRepo.findByUserIdAndFlashcardId(1L, 10L)).thenReturn(Optional.of(state));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        service.processReview(cmd);
        
        ArgumentCaptor<FlashcardReviewEvent> captor = ArgumentCaptor.forClass(FlashcardReviewEvent.class);
        verify(eventRepo).save(captor.capture());
        
        FlashcardReviewEvent ev = captor.getValue();
        assertThat(ev.getStateBefore()).isEqualTo(FlashcardScheduleStatus.LEARNING);
        assertThat(ev.getStepBefore()).isEqualTo(1);
        assertThat(ev.getDifficultyBefore()).isEqualTo(4.0);
        assertThat(ev.getStabilityBefore()).isEqualTo(2.5);
        assertThat(ev.getDueBefore()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void testR20_VersionMetadataPersisted() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        service.processReview(cmd);
        
        ArgumentCaptor<FlashcardReviewEvent> captor = ArgumentCaptor.forClass(FlashcardReviewEvent.class);
        verify(eventRepo).save(captor.capture());
        
        FlashcardReviewEvent ev = captor.getValue();
        assertThat(ev.getAlgorithmVersion()).isEqualTo(FlashcardFsrsConfigConstants.ALGORITHM_VERSION);
        assertThat(ev.getSchedulerVersion()).isEqualTo(FlashcardFsrsConfigConstants.SCHEDULER_VERSION);
        assertThat(ev.getSchedulerConfigVersion()).isEqualTo(FlashcardFsrsConfigConstants.SCHEDULER_CONFIG_VERSION);
    }

    @Test
    void testR21_NoMasteryWrites() {
        // Confirmed by verifying no mastery service is injected or called
        assertThat(service.getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("mastery"));
    }

    @Test
    void testR22_NoLlmOrNetworkDependency() {
        // Confirmed by verifying no LLM service is injected or called
        assertThat(service.getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("llm") || field.getName().toLowerCase().contains("network"));
    }

    @Test
    void testR23_EventStateAtomicity() {
        Flashcard f = createValidFlashcard(1L);
        when(flashcardRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(f));
        when(scheduler.review(anyLong(), any(), any(), any())).thenReturn(createMockResult(FlashcardRating.GOOD));
        
        // Deliberately fail state save
        when(stateRepo.save(any())).thenThrow(new RuntimeException("Simulated failure"));
        
        FlashcardReviewCommand cmd = new FlashcardReviewCommand(1L, 10L, UUID.randomUUID().toString(), FlashcardRating.GOOD, 1000);
        assertThatThrownBy(() -> service.processReview(cmd)).isInstanceOf(RuntimeException.class);
        
        // Note: Real DB rollback is tested in I9. This just verifies transactionTemplate runs both inside.
        verify(eventRepo).save(any());
        verify(stateRepo).save(any());
    }

    @Test
    void testR24_StateReusedRatherThanDuplicate() {
        testR2_SubsequentReviewUpdatesSameState();
        verify(stateRepo, times(1)).save(any(FlashcardReviewState.class));
    }
}
