package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.domain.presentation.dto.PresentationGenerationJobDto;
import com.example.studyassistant.domain.presentation.dto.PresentationOutlineGenerationCommand;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * T1.4 — AI Outline Generation Orchestrator.
 *
 * Synchronous entry-point. Implements the mandated flow:
 *
 * A. Authenticate (caller responsibility via TeacherAuthorizationService)
 * B. Canonicalize request + compute requestHash
 * C. Fast Idempotency lookup (optimization)
 * D. Atomic TX1:
 *    - Acquire PESSIMISTIC_WRITE lock on PresentationDeck
 *    - Re-check idempotency (authoritative)
 *    - Validate generation scope (no legacy extractedText path)
 *    - Consume PRESENTATION_OUTLINE_GENERATION quota exactly once
 *    - Create QUEUED job
 * E. Submit async worker ONLY IF newly created
 *
 * The async worker (PresentationOutlineGenerationWorker) handles the rest:
 *   retrieval → LLM → validation → TX2 persist
 *
 * No DB transaction is held across retrieval or LLM calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationOutlineGenerationService {

    private static final int DEFAULT_TARGET_SLIDES = 10;
    private static final int MIN_TARGET_SLIDES = 3;
    private static final int MAX_TARGET_SLIDES = 30;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final PresentationDeckRepository deckRepository;
    private final PresentationGenerationJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final com.example.studyassistant.repository.UserRepository userRepository;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final PresentationOutlineGenerationWorker worker;
    private final Clock clock;

    /**
     * Submit an outline generation request.
     *
     * @param owner  authenticated TEACHER user (server-derived, never from client)
     * @param deckId target deck
     * @param cmd    generation parameters
     * @return job DTO (immediately, before async work completes)
     */
    public PresentationGenerationJobDto submitOutlineGeneration(
            User owner, Long deckId, PresentationOutlineGenerationCommand cmd) {

        // --- INPUT GUARD ---
        validateCommand(cmd);

        // --- B: CANONICALIZE REQUEST ---
        int targetSlideCount = resolveTargetSlideCount(cmd.targetSlideCount());
        List<Long> documentIds = canonicalizeDocumentIds(cmd.documentIds());

        String requestHash = OutlineContentHasher.requestHash(
                deckId,
                cmd.topic(),
                cmd.teacherInstruction(),
                targetSlideCount,
                cmd.audience(),
                cmd.language(),
                documentIds
        );

        // --- C: FAST IDEMPOTENCY LOOKUP (Optimization, no lock) ---
        PresentationGenerationJob existingJob = jobRepository
                .findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
                        owner.getId(), deckId, PresentationJobType.OUTLINE, cmd.idempotencyKey())
                .orElse(null);

        if (existingJob != null) {
            if (!existingJob.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key reused with different request parameters");
            }
            log.info("Idempotent replay (fast): returning existing job {} for deck {}", existingJob.getId(), deckId);
            return mapJobToDto(existingJob);
        }

        // --- D: ATOMIC TX1 (Deck Lock, Idempotency, Quota, Job Create) ---
        SubmitResult result = transactionTemplate.execute(status -> {
            userRepository.findByIdForUpdate(owner.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            // A. acquire PESSIMISTIC_WRITE lock on the owned PresentationDeck
            PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, owner.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

            if (PresentationDeckStatus.ARCHIVED.equals(deck.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck is archived and cannot generate new outlines");
            }

            // B. re-check idempotency identity under lock
            PresentationGenerationJob authoritativeJob = jobRepository
                    .findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
                            owner.getId(), deckId, PresentationJobType.OUTLINE, cmd.idempotencyKey())
                    .orElse(null);

            if (authoritativeJob != null) {
                if (!authoritativeJob.getRequestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Idempotency key reused with different request parameters");
                }
                return new SubmitResult(authoritativeJob, false);
            }

            // D. only if no existing job
            // Validate generation scope inside TX so it's strongly consistent with the quota
            Long notebookId = deck.getNotebook().getId();
            validateDocumentScope(owner.getId(), notebookId, documentIds);

            // consume presentation outline quota (rolls back if job insert fails)
            quotaService.checkAndIncrementPresentationOutlineLimit(owner.getId(), owner.getPlan());

            // create QUEUED job
            PresentationGenerationJob newJob = new PresentationGenerationJob();
            newJob.setOwner(owner);
            newJob.setDeckId(deckId);
            newJob.setJobType(PresentationJobType.OUTLINE);
            newJob.setIdempotencyKey(cmd.idempotencyKey());
            newJob.setRequestHash(requestHash);
            newJob.setStatus(PresentationJobStatus.QUEUED);
            newJob.setProgressCurrent(0);
            newJob.setProgressTotal(3); // retrieval, llm, persist
            newJob.setCreatedAt(LocalDateTime.now(clock));
            
            return new SubmitResult(jobRepository.save(newJob), true);
        });

        // --- E: SUBMIT ASYNC WORKER (Only if newly created) ---
        if (result.isNew()) {
            PresentationGenerationJob job = result.job();
            // Use same deck.getNotebook().getId() which we know is valid, but we must retrieve it if outside TX
            // We can just rely on the job owner and deck logic inside the worker to reload notebookId, 
            // but the worker signature takes it. We can re-fetch or trust the DB. 
            // It's cleaner to just fetch notebookId again or add to SubmitResult.
            // But we know deckId is valid, worker can look it up.
            // For now, let's just fetch it outside TX to pass to worker (dirty read is fine for notebook ID which never changes)
            PresentationDeck deck = deckRepository.findById(deckId).orElseThrow();
            Long notebookId = deck.getNotebook().getId();

            worker.generateOutlineAsync(
                    job.getId(), deckId, owner.getId(), notebookId,
                    cmd.topic(), cmd.teacherInstruction(),
                    targetSlideCount, cmd.audience(), cmd.language(),
                    documentIds
            );
            log.info("Submitted new outline generation job {} for deck {} owner {}", job.getId(), deckId, owner.getId());
        } else {
            log.info("Idempotent replay (TX1): returning existing job {} for deck {}", result.job().getId(), deckId);
        }

        return mapJobToDto(result.job());
    }

    private record SubmitResult(PresentationGenerationJob job, boolean isNew) {}

    // ---------- helpers ----------

    private void validateCommand(PresentationOutlineGenerationCommand cmd) {
        if (cmd == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required");
        }
        if (cmd.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "idempotencyKey exceeds maximum length of " + MAX_IDEMPOTENCY_KEY_LENGTH);
        }
        if (cmd.targetSlideCount() != null
                && (cmd.targetSlideCount() < MIN_TARGET_SLIDES || cmd.targetSlideCount() > MAX_TARGET_SLIDES)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetSlideCount must be between " + MIN_TARGET_SLIDES + " and " + MAX_TARGET_SLIDES);
        }
    }

    /**
     * Validates that all supplied documentIds:
     *  - belong to the authenticated user (owner.id)
     *  - belong to the deck's notebook
     * Rejects foreign document IDs. Does NOT bypass retrieval security.
     * No raw extractedText is read here.
     */
    private void validateDocumentScope(Long ownerId, Long notebookId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return; // Worker will use all authorized docs via RetrievalService
        }
        // All authorized docs for this owner+notebook
        var authorizedDocs = documentRepository.findByUserIdAndNotebookId(ownerId, notebookId);
        var authorizedIds = authorizedDocs.stream()
                .map(doc -> doc.getId())
                .collect(java.util.stream.Collectors.toSet());

        for (Long docId : documentIds) {
            if (!authorizedIds.contains(docId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Document " + docId + " does not belong to this user's notebook or does not exist");
            }
        }
    }

    private List<Long> canonicalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }
        for (Long id : documentIds) {
            if (id == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentIds list cannot contain null elements");
            }
        }
        return documentIds.stream().distinct().sorted().collect(Collectors.toList());
    }

    private int resolveTargetSlideCount(Integer requested) {
        if (requested == null) return DEFAULT_TARGET_SLIDES;
        return Math.max(MIN_TARGET_SLIDES, Math.min(MAX_TARGET_SLIDES, requested));
    }

    private PresentationGenerationJobDto mapJobToDto(PresentationGenerationJob job) {
        return new PresentationGenerationJobDto(
                job.getId(), job.getJobType().name(), job.getStatus().name(),
                job.getProgressCurrent(), job.getProgressTotal(),
                job.getErrorCode(), job.getErrorSummary(),
                job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(),
                job.getOutlineRevisionId()
        );
    }
}
