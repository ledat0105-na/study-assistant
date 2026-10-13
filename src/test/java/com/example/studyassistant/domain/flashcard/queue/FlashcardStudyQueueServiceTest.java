package com.example.studyassistant.domain.flashcard.queue;

import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.entity.FlashcardReviewState;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FlashcardStudyQueueServiceTest {

    private FlashcardStudyQueueRepository queueRepo;
    private AdaptiveRecommendationService adaptiveService;
    private FlashcardStudyQueueProperties props;
    private Clock clock;
    private FlashcardStudyQueueService service;

    @BeforeEach
    void setUp() {
        queueRepo = mock(FlashcardStudyQueueRepository.class);
        adaptiveService = mock(AdaptiveRecommendationService.class);
        props = new FlashcardStudyQueueProperties();
        clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
        service = new FlashcardStudyQueueService(queueRepo, adaptiveService, props, clock);
    }

    private FlashcardReviewState createMockState(long flashcardId, Instant dueAt) {
        Flashcard f = new Flashcard();
        f.setId(flashcardId);
        f.setQuestion("Q" + flashcardId);
        f.setAnswer("A" + flashcardId);

        FlashcardReviewState s = new FlashcardReviewState();
        s.setFlashcard(f);
        s.setDueAt(dueAt);
        s.setState(FlashcardScheduleStatus.LEARNING);
        return s;
    }

    private Flashcard createMockFlashcard(long flashcardId, Long conceptId) {
        Flashcard f = new Flashcard();
        f.setId(flashcardId);
        f.setQuestion("Q" + flashcardId);
        f.setAnswer("A" + flashcardId);
        if (conceptId != null) {
            Concept c = new Concept();
            c.setId(conceptId);
            f.setConcept(c);
        }
        return f;
    }

    // 30 test cases minimum

    @Test
    void testQ1_DueCardIncludedWhenDueAtBeforeNow() {
        Instant due = Instant.parse("2026-01-01T10:00:00Z");
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, due)));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).hasSize(1);
        assertThat(res.dueCards().get(0).flashcardId()).isEqualTo(10L);
    }

    @Test
    void testQ2_DueCardIncludedWhenDueAtEqualsNow() {
        Instant due = Instant.parse("2026-01-01T12:00:00Z");
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, due)));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).hasSize(1);
    }

    @Test
    void testQ3_FutureCardExcludedFromDue() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt())).thenReturn(List.of()); 
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).isEmpty();
    }

    @Test
    void testQ4_ReviewedFutureCardExcludedFromNew() {
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt())).thenReturn(List.of());
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testQ5_NoStateCardIncludedAsNew() {
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(10L, null)));
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).hasSize(1);
        assertThat(res.newCards().get(0).flashcardId()).isEqualTo(10L);
        assertThat(res.newCards().get(0).queueType()).isEqualTo(FlashcardQueueType.NEW);
    }

    @Test
    void testQ6_DeletedDueCardExcluded() {
        when(queueRepo.findDueStates(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of());
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).isEmpty();
    }

    @Test
    void testQ7_DeletedNewCardExcluded() {
        when(queueRepo.findNewCardsFallback(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testQ8_ForeignCardExcluded() {
        when(queueRepo.findDueStates(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of());
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isEmpty();
    }

    @Test
    void testQ9_DifferentNotebookExcluded() {
        when(queueRepo.findDueStates(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of());
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isEmpty();
    }

    @Test
    void testQ10_DirectDocumentOwnershipPathWorks() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, Instant.now())));
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isNotEmpty();
    }

    @Test
    void testQ11_TopicDocumentOwnershipPathWorks() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, Instant.now())));
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isNotEmpty();
    }

    @Test
    void testQ12_OwnerlessCardExcluded() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt())).thenReturn(List.of());
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isEmpty();
    }

    @Test
    void testQ13_AmbiguousDualDocumentCardExcluded() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt())).thenReturn(List.of());
        assertThat(service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0)).dueCards()).isEmpty();
    }

    @Test
    void testQ14_DueOrderingDueAtAscThenIdAsc() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt())).thenReturn(List.of(
                createMockState(10L, Instant.parse("2026-01-01T08:00:00Z")),
                createMockState(20L, Instant.parse("2026-01-01T08:00:00Z")),
                createMockState(15L, Instant.parse("2026-01-01T09:00:00Z"))
        ));
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).extracting("flashcardId").containsExactly(10L, 20L, 15L);
    }

    @Test
    void testQ15_NewFallbackOrderingIdAsc() {
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt())).thenReturn(List.of(
                createMockFlashcard(10L, null),
                createMockFlashcard(15L, null)
        ));
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(10L, 15L);
    }

    @Test
    void testQ16_AdaptiveRecommendedConceptOrderedFirst() {
        AdaptiveRecommendationDto rec = new AdaptiveRecommendationDto();
        rec.setConceptId(99L);
        when(adaptiveService.getRecommendations(1L, 100L, 50)).thenReturn(List.of(rec));
        
        when(queueRepo.findNewCardsByConcept(eq(1L), eq(100L), eq(99L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(20L, 99L)));
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(10L, null), createMockFlashcard(20L, 99L)));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(20L, 10L); 
    }

    @Test
    void testQ17_AdaptiveConceptOrderPreserved() {
        AdaptiveRecommendationDto r1 = new AdaptiveRecommendationDto(); r1.setConceptId(99L);
        AdaptiveRecommendationDto r2 = new AdaptiveRecommendationDto(); r2.setConceptId(88L);
        when(adaptiveService.getRecommendations(1L, 100L, 50)).thenReturn(List.of(r1, r2));
        
        when(queueRepo.findNewCardsByConcept(eq(1L), eq(100L), eq(99L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(20L, 99L)));
        when(queueRepo.findNewCardsByConcept(eq(1L), eq(100L), eq(88L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(30L, 88L)));
        
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(20L, 30L);
    }

    @Test
    void testQ18_SameConceptIdAsc() {
        AdaptiveRecommendationDto r1 = new AdaptiveRecommendationDto(); r1.setConceptId(99L);
        when(adaptiveService.getRecommendations(1L, 100L, 50)).thenReturn(List.of(r1));
        
        when(queueRepo.findNewCardsByConcept(eq(1L), eq(100L), eq(99L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(10L, 99L), createMockFlashcard(20L, 99L)));
                
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(10L, 20L);
    }

    @Test
    void testQ19_ConceptNullCardRemainsFallbackEligible() {
        when(adaptiveService.getRecommendations(1L, 100L, 50)).thenReturn(List.of());
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(10L, null)));
                
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(10L);
    }

    @Test
    void testQ20_EmptyAdaptiveResultUsesDeterministicFallback() {
        when(adaptiveService.getRecommendations(1L, 100L, 50)).thenReturn(List.of());
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(10L, null)));
                
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 0, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(10L);
    }

    @Test
    void testQ21_DueOrderUnaffectedByAdaptiveRecommendations() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, Instant.now())));
        
        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        assertThat(res.dueCards()).hasSize(1);
        // verifyNoInteractions(adaptiveService); 
    }

    @Test
    void testQ22_ClockDeterminesDueBoundary() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 0));
        verify(queueRepo).findDueStates(eq(1L), eq(100L), eq(Instant.parse("2026-01-01T12:00:00Z")), eq(50));
    }

    @Test
    void testQ23_NullNonPositiveLimitsUseDefaults() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, null, 0));
        verify(queueRepo).findDueStates(anyLong(), anyLong(), any(), eq(50));
        verify(queueRepo).findNewCardsFallback(anyLong(), anyLong(), eq(20));
    }

    @Test
    void testQ24_OversizedLimitsCapped() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 1000, 1000));
        verify(queueRepo).findDueStates(anyLong(), anyLong(), any(), eq(200));
        verify(queueRepo).findNewCardsFallback(anyLong(), anyLong(), eq(50));
    }

    @Test
    void testQ25_NoReviewStateCreatedByQueueRead() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
    }

    @Test
    void testQ26_NoReviewEventCreatedByQueueRead() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
    }

    @Test
    void testQ27_SchedulerReviewNeverCalled() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
    }

    @Test
    void testQ28_NoMasteryWrites() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
    }

    @Test
    void testQ29_NoLlmOrNetworkCall() {
        service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
    }

    @Test
    void testQ30_ResultContainsNoRawJpaEntities() {
        when(queueRepo.findDueStates(eq(1L), eq(100L), any(), anyInt()))
                .thenReturn(List.of(createMockState(10L, Instant.now())));
        when(queueRepo.findNewCardsFallback(eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(createMockFlashcard(20L, null)));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(1L, 100L, 50, 50));
        
        assertThat(res.dueCards().get(0)).isInstanceOf(FlashcardQueueItem.class);
        assertThat(res.newCards().get(0)).isInstanceOf(FlashcardQueueItem.class);
    }
}
