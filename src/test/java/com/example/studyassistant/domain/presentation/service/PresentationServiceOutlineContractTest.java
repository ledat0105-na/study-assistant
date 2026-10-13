package com.example.studyassistant.domain.presentation.service;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationDeckStatus;
import com.example.studyassistant.domain.presentation.PresentationGenerationJob;
import com.example.studyassistant.domain.presentation.PresentationGenerationJobRepository;
import com.example.studyassistant.domain.presentation.PresentationJobStatus;
import com.example.studyassistant.domain.presentation.PresentationJobType;
import com.example.studyassistant.domain.presentation.PresentationOutlineRevision;
import com.example.studyassistant.domain.presentation.PresentationOutlineRevisionRepository;
import com.example.studyassistant.domain.presentation.PresentationOutlineStatus;
import com.example.studyassistant.domain.presentation.dto.PresentationGenerationJobDto;
import com.example.studyassistant.domain.presentation.dto.PresentationOutlineRevisionDto;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused contract tests for the additive Teacher Mode API delta:
 * real outline listing, outline contentHash exposure, job outlineRevisionId exposure.
 */
class PresentationServiceOutlineContractTest {

    private static final long OWNER_ID = 11L;
    private static final long FOREIGN_ID = 99L;
    private static final long DECK_ID = 500L;

    private PresentationDeckRepository deckRepository;
    private PresentationOutlineRevisionRepository outlineRepository;
    private PresentationGenerationJobRepository jobRepository;
    private PresentationService service;
    private User owner;
    private User foreign;

    @BeforeEach
    void setUp() {
        deckRepository = mock(PresentationDeckRepository.class);
        outlineRepository = mock(PresentationOutlineRevisionRepository.class);
        jobRepository = mock(PresentationGenerationJobRepository.class);
        NotebookRepository notebookRepository = mock(NotebookRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
        service = new PresentationService(deckRepository, outlineRepository, jobRepository, notebookRepository, clock);

        owner = new User();
        owner.setId(OWNER_ID);
        foreign = new User();
        foreign.setId(FOREIGN_ID);
    }

    @Test
    void ownerListsGeneratedRevisionsNewestFirstWithPersistedContentHash() {
        PresentationDeck deck = deck(PresentationDeckStatus.OUTLINE_READY);
        when(deckRepository.findByIdAndOwnerId(DECK_ID, OWNER_ID)).thenReturn(Optional.of(deck));
        PresentationOutlineRevision rev2 = revision(202L, 2, PresentationOutlineStatus.DRAFT, "hash-two", null);
        PresentationOutlineRevision rev1 = revision(201L, 1, PresentationOutlineStatus.APPROVED, "hash-one",
                LocalDateTime.of(2026, 10, 4, 9, 0));
        when(outlineRepository.findByDeckIdOrderByRevisionNumberDesc(DECK_ID)).thenReturn(List.of(rev2, rev1));

        List<PresentationOutlineRevisionDto> result = service.getOutlines(owner, DECK_ID);

        assertThat(result).extracting(PresentationOutlineRevisionDto::revisionNumber).containsExactly(2, 1);
        assertThat(result).extracting(PresentationOutlineRevisionDto::id).containsExactly(202L, 201L);
        assertThat(result).extracting(PresentationOutlineRevisionDto::contentHash).containsExactly("hash-two", "hash-one");
        assertThat(result).extracting(PresentationOutlineRevisionDto::status).containsExactly("DRAFT", "APPROVED");
        PresentationOutlineRevisionDto approved = result.get(1);
        assertThat(approved.outlineJson()).isEqualTo("{\"rev\":1}");
        assertThat(approved.createdBy()).isEqualTo(OWNER_ID);
        assertThat(approved.approvedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 9, 0));
        verify(outlineRepository).findByDeckIdOrderByRevisionNumberDesc(DECK_ID);
    }

    @Test
    void ownerWithNoRevisionsGetsEmptyList() {
        PresentationDeck deck = deck(PresentationDeckStatus.DRAFT);
        when(deckRepository.findByIdAndOwnerId(DECK_ID, OWNER_ID)).thenReturn(Optional.of(deck));
        when(outlineRepository.findByDeckIdOrderByRevisionNumberDesc(DECK_ID)).thenReturn(List.of());

        assertThat(service.getOutlines(owner, DECK_ID)).isEmpty();
    }

    @Test
    void foreignOwnerFailsClosedWith404AndNeverReadsRevisions() {
        when(deckRepository.findByIdAndOwnerId(DECK_ID, FOREIGN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOutlines(foreign, DECK_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(outlineRepository);
    }

    @Test
    void archivedDeckOutlineReadRemainsAllowed() {
        PresentationDeck deck = deck(PresentationDeckStatus.ARCHIVED);
        when(deckRepository.findByIdAndOwnerId(DECK_ID, OWNER_ID)).thenReturn(Optional.of(deck));
        PresentationOutlineRevision rev = revision(301L, 1, PresentationOutlineStatus.APPROVED, "hash-archived", null);
        when(outlineRepository.findByDeckIdOrderByRevisionNumberDesc(DECK_ID)).thenReturn(List.of(rev));

        List<PresentationOutlineRevisionDto> result = service.getOutlines(owner, DECK_ID);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(301L);
            assertThat(dto.contentHash()).isEqualTo("hash-archived");
        });
        verify(deckRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void succeededOutlineJobDtoExposesPersistedOutlineRevisionId() {
        PresentationGenerationJob job = job(PresentationJobStatus.SUCCEEDED, 777L);
        when(jobRepository.findByIdAndOwnerIdAndDeckId(41L, OWNER_ID, DECK_ID)).thenReturn(Optional.of(job));

        PresentationGenerationJobDto dto = service.getJob(owner, DECK_ID, 41L);

        assertThat(dto.jobId()).isEqualTo(41L);
        assertThat(dto.status()).isEqualTo("SUCCEEDED");
        assertThat(dto.jobType()).isEqualTo("OUTLINE");
        assertThat(dto.outlineRevisionId()).isEqualTo(777L);
    }

    @Test
    void runningOutlineJobDtoHasNullOutlineRevisionId() {
        PresentationGenerationJob job = job(PresentationJobStatus.RUNNING, null);
        when(jobRepository.findByIdAndOwnerIdAndDeckId(41L, OWNER_ID, DECK_ID)).thenReturn(Optional.of(job));

        assertThat(service.getJob(owner, DECK_ID, 41L).outlineRevisionId()).isNull();
    }

    @Test
    void foreignJobLookupStillFailsClosed() {
        when(jobRepository.findByIdAndOwnerIdAndDeckId(anyLong(), org.mockito.ArgumentMatchers.eq(FOREIGN_ID), anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(foreign, DECK_ID, 41L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PresentationDeck deck(PresentationDeckStatus status) {
        PresentationDeck deck = mock(PresentationDeck.class);
        when(deck.getId()).thenReturn(DECK_ID);
        org.mockito.Mockito.lenient().when(deck.getStatus()).thenReturn(status);
        return deck;
    }

    private PresentationOutlineRevision revision(long id, int number, PresentationOutlineStatus status,
                                                 String contentHash, LocalDateTime approvedAt) {
        PresentationOutlineRevision revision = mock(PresentationOutlineRevision.class);
        when(revision.getId()).thenReturn(id);
        when(revision.getRevisionNumber()).thenReturn(number);
        when(revision.getStatus()).thenReturn(status);
        when(revision.getOutlineJson()).thenReturn("{\"rev\":" + number + "}");
        when(revision.getCreatedBy()).thenReturn(owner);
        when(revision.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 8, number));
        when(revision.getApprovedAt()).thenReturn(approvedAt);
        when(revision.getContentHash()).thenReturn(contentHash);
        return revision;
    }

    private PresentationGenerationJob job(PresentationJobStatus status, Long outlineRevisionId) {
        PresentationGenerationJob job = mock(PresentationGenerationJob.class);
        when(job.getId()).thenReturn(41L);
        when(job.getDeckId()).thenReturn(DECK_ID);
        when(job.getJobType()).thenReturn(PresentationJobType.OUTLINE);
        when(job.getStatus()).thenReturn(status);
        when(job.getOutlineRevisionId()).thenReturn(outlineRevisionId);
        return job;
    }
}
