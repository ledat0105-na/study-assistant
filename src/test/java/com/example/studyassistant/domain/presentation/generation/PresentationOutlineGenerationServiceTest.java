package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.domain.presentation.dto.PresentationGenerationJobDto;
import com.example.studyassistant.domain.presentation.dto.PresentationOutlineGenerationCommand;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PresentationOutlineGenerationService.
 * Tests: A auth, B foreign deck, C foreign document, D student role,
 * N idempotent replay, O idempotency conflict, P replay quota,
 * Q new attempt quota.
 */
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresentationOutlineGenerationServiceTest {

    @Mock private PresentationDeckRepository deckRepository;
    @Mock private PresentationGenerationJobRepository jobRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private QuotaService quotaService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private PresentationOutlineGenerationWorker worker;

    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private PresentationOutlineGenerationService service;

    private User teacherUser;
    private User studentUser;
    private PresentationDeck activeDeck;

    @BeforeEach
    void setUp() {
        teacherUser = spy(new User());
        doReturn(1L).when(teacherUser).getId();
        teacherUser.setRole("TEACHER");
        teacherUser.setPlan("PREMIUM");

        studentUser = spy(new User());
        doReturn(2L).when(studentUser).getId();
        studentUser.setRole("STUDENT");
        studentUser.setPlan("FREE");

        Notebook notebook = spy(new Notebook());
        doReturn(10L).when(notebook).getId();

        activeDeck = new PresentationDeck();
        activeDeck.setNotebook(notebook);
        activeDeck.setStatus(PresentationDeckStatus.DRAFT);
    }

    private PresentationOutlineGenerationCommand buildCmd() {
        return new PresentationOutlineGenerationCommand(
                "idem-key-1", "Test Topic", null, 10, "students", "en", null);
    }

    @SuppressWarnings("unchecked")
    private void mockTransactionTemplateExecutes() {
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(userRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(teacherUser));
    }

    // ── A: Authorization not tested here (done at controller layer via TeacherAuthorizationService)

    // ── B: Foreign deck → 404
    @Test
    void testB_foreignDeckRejected() {
        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(anyLong(), anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());
        mockTransactionTemplateExecutes();
        when(deckRepository.findOwnedByIdForUpdate(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 999L, buildCmd()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── C: Foreign document → UNPROCESSABLE_ENTITY
    @Test
    void testC_foreignDocumentRejected() {
        Notebook notebook = spy(new Notebook());
        doReturn(10L).when(notebook).getId();
        activeDeck.setNotebook(notebook);

        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(anyLong(), anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());
        mockTransactionTemplateExecutes();
        when(deckRepository.findOwnedByIdForUpdate(anyLong(), anyLong())).thenReturn(Optional.of(activeDeck));
        // No documents returned → requested doc 99L is foreign
        when(documentRepository.findByUserIdAndNotebookId(anyLong(), anyLong())).thenReturn(List.of());

        var cmdWithForeignDoc = new PresentationOutlineGenerationCommand(
                "idem-key-1", "Topic", null, 10, null, null, List.of(99L));

        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, cmdWithForeignDoc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ── N: Idempotent replay same key+hash → existing job returned, ZERO quota
    @Test
    void testN_idempotentReplaySameHashReturnsExistingJobNoQuotaCharge() {
        PresentationGenerationJob existingJob = spy(new PresentationGenerationJob());
        doReturn(42L).when(existingJob).getId();
        existingJob.setStatus(PresentationJobStatus.SUCCEEDED);
        existingJob.setJobType(PresentationJobType.OUTLINE);
        existingJob.setProgressCurrent(3);
        existingJob.setProgressTotal(3);
        // Pre-compute same hash
        String expectedHash = OutlineContentHasher.requestHash(
                1L, "Test Topic", null, 10, "students", "en", null);
        existingJob.setRequestHash(expectedHash);
        existingJob.setIdempotencyKey("idem-key-1");
        existingJob.setCreatedAt(LocalDateTime.now(clock));

        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
                eq(1L), eq(1L), eq(PresentationJobType.OUTLINE), eq("idem-key-1")))
                .thenReturn(Optional.of(existingJob));
        mockTransactionTemplateExecutes();

        PresentationGenerationJobDto result = service.submitOutlineGeneration(teacherUser, 1L, buildCmd());

        assertThat(result.jobId()).isEqualTo(42L);
        // Quota must NOT be charged
        verify(quotaService, never()).checkAndIncrementPresentationOutlineLimit(anyLong(), anyString());
        // Worker must NOT be invoked
        verify(worker, never()).generateOutlineAsync(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any());
    }

    // ── O: Idempotency conflict different hash → 409
    @Test
    void testO_idempotencyConflictDifferentHash() {
        PresentationGenerationJob existingJob = spy(new PresentationGenerationJob());
        doReturn(42L).when(existingJob).getId();
        existingJob.setRequestHash("completely-different-hash-not-matching");
        existingJob.setIdempotencyKey("idem-key-1");
        existingJob.setStatus(PresentationJobStatus.QUEUED);
        existingJob.setJobType(PresentationJobType.OUTLINE);
        existingJob.setCreatedAt(LocalDateTime.now(clock));

        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
                eq(1L), eq(1L), eq(PresentationJobType.OUTLINE), eq("idem-key-1")))
                .thenReturn(Optional.of(existingJob));
        mockTransactionTemplateExecutes();

        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, buildCmd()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(quotaService, never()).checkAndIncrementPresentationOutlineLimit(anyLong(), anyString());
    }

    // ── Q: New attempt consumes quota exactly once
    @Test
    void testQ_newAttemptConsumesQuotaOnce() {
        Notebook notebook = spy(new Notebook());
        doReturn(10L).when(notebook).getId();
        activeDeck.setNotebook(notebook);

        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(anyLong(), anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());
        mockTransactionTemplateExecutes();
        when(deckRepository.findOwnedByIdForUpdate(anyLong(), anyLong())).thenReturn(Optional.of(activeDeck));
        // no documentIds in cmd, so findByUserIdAndNotebookId is NOT called

        PresentationGenerationJob newJob = spy(new PresentationGenerationJob());
        doReturn(99L).when(newJob).getId();
        newJob.setStatus(PresentationJobStatus.QUEUED);
        newJob.setJobType(PresentationJobType.OUTLINE);
        newJob.setProgressCurrent(0);
        newJob.setProgressTotal(3);
        newJob.setCreatedAt(LocalDateTime.now(clock));

        when(jobRepository.save(any())).thenReturn(newJob);
        when(deckRepository.findById(anyLong())).thenReturn(Optional.of(activeDeck));

        service.submitOutlineGeneration(teacherUser, 1L, buildCmd());

        verify(quotaService, times(1)).checkAndIncrementPresentationOutlineLimit(eq(1L), eq("PREMIUM"));
    }

    // ── Archived deck → 409
    @Test
    void testArchivedDeckRejected() {
        activeDeck.setStatus(PresentationDeckStatus.ARCHIVED);
        Notebook notebook = spy(new Notebook());
        doReturn(10L).when(notebook).getId();
        activeDeck.setNotebook(notebook);

        when(jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(anyLong(), anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());
        mockTransactionTemplateExecutes();
        when(deckRepository.findOwnedByIdForUpdate(anyLong(), anyLong())).thenReturn(Optional.of(activeDeck));

        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, buildCmd()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── Missing idempotencyKey → 400
    @Test
    void testMissingIdempotencyKey() {
        var cmd = new PresentationOutlineGenerationCommand(
                null, "Topic", null, 10, null, null, null);
        mockTransactionTemplateExecutes();

        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Slide count bounds
    @Test
    void testSlideCountTooLow() {
        var cmd = new PresentationOutlineGenerationCommand(
                "key", "Topic", null, 1, null, null, null);
        mockTransactionTemplateExecutes();
        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void testSlideCountTooHigh() {
        var cmd = new PresentationOutlineGenerationCommand(
                "key", "Topic", null, 100, null, null, null);
        mockTransactionTemplateExecutes();
        assertThatThrownBy(() -> service.submitOutlineGeneration(teacherUser, 1L, cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
