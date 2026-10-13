package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand;
import com.example.studyassistant.domain.presentation.dto.SlideRetryCommand;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PresentationSlideGenerationService {

    private final PresentationDeckRepository deckRepository;
    private final PresentationGenerationJobRepository jobRepository;
    private final PresentationSlideRepository slideRepository;
    private final PresentationSlideElementRepository elementRepository;
    private final PresentationSlideSourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final OutlineFlatteningService outlineFlatteningService;
    private final GenerationScopeParser scopeParser;
    private final PresentationSlideGenerationWorker worker;
    private final SlideGenerationSubmissionHook submissionHook;
    private final Clock clock;

    private record SubmitResult(
        PresentationGenerationJob job,
        boolean isNew,
        Long revisionId,
        String contentHash,
        GenerationScopeParser.ParsedScope scope,
        List<PlannedSlide> plannedSlides
    ) {}

    public PresentationGenerationJob submitFullGeneration(User owner, long deckId, SlideGenerationCommand command) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required");
        }

        SubmitResult result = transactionTemplate.execute(status -> {
            userRepository.findByIdForUpdate(owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

            PresentationOutlineRevision approvedRevision = deck.getApprovedOutlineRevision();
            if (approvedRevision == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck has no approved outline");
            }
            Long revisionId = approvedRevision.getId();
            String contentHash = approvedRevision.getContentHash();
            String requestHash = SlideContentHasher.fullJobRequestHash(deckId, revisionId, contentHash);

            Optional<PresentationGenerationJob> existingJob = jobRepository.findIdempotencyIdentityForUpdate(
                owner.getId(), deckId, PresentationJobType.SLIDES, command.idempotencyKey()
            );

            if (existingJob.isPresent()) {
                PresentationGenerationJob job = existingJob.get();
                if (!job.getRequestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different request");
                }
                return new SubmitResult(job, false, revisionId, contentHash, null, List.of());
            }

            if (jobRepository.existsByDeckIdAndJobTypeInAndStatusIn(deckId,
                    List.of(PresentationJobType.SLIDES, PresentationJobType.SLIDE_RETRY),
                    List.of(PresentationJobStatus.QUEUED, PresentationJobStatus.RUNNING))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Active slide generation job exists");
            }

            List<PresentationSlide> existingSlides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId);
            for (PresentationSlide slide : existingSlides) {
                if (!slide.getGeneratedFromOutlineRevisionId().equals(revisionId)) {
                    if (slide.getGenerationState() == PresentationSlideGenerationState.GENERATED ||
                        slide.getGenerationState() == PresentationSlideGenerationState.EDITED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck contains generated or edited slides from a prior revision");
                    }
                }
            }

            for (PresentationSlide slide : existingSlides) {
                if (!slide.getGeneratedFromOutlineRevisionId().equals(revisionId)) {
                    elementRepository.deleteBySlideId(slide.getId());
                    sourceRepository.deleteBySlideId(slide.getId());
                    slideRepository.delete(slide);
                }
            }

            GenerationScopeParser.ParsedScope scope = scopeParser.parse(approvedRevision.getGenerationScopeSnapshot());
            List<PlannedSlide> plannedSlides = outlineFlatteningService.flattenOutline(approvedRevision.getOutlineJson(), approvedRevision.getGenerationScopeSnapshot());

            List<PlannedSlide> slidesToGenerate = new ArrayList<>();
            for (PlannedSlide pSlide : plannedSlides) {
                boolean alreadyDone = existingSlides.stream()
                    .anyMatch(s -> s.getGeneratedFromOutlineRevisionId().equals(revisionId) &&
                                   s.getSlideOrder() == pSlide.slideOrder() &&
                                   (s.getGenerationState() == PresentationSlideGenerationState.GENERATED ||
                                    s.getGenerationState() == PresentationSlideGenerationState.EDITED));
                if (!alreadyDone) {
                    slidesToGenerate.add(pSlide);
                }
            }

            int targetSlideCount = slidesToGenerate.size();

            if (targetSlideCount == 0) {
                PresentationGenerationJob newJob = new PresentationGenerationJob();
                newJob.setOwner(owner);
                newJob.setDeckId(deckId);
                newJob.setJobType(PresentationJobType.SLIDES);
                newJob.setStatus(PresentationJobStatus.SUCCEEDED);
                newJob.setIdempotencyKey(command.idempotencyKey());
                newJob.setRequestHash(requestHash);
                newJob.setOutlineRevisionId(revisionId);
                newJob.setProgressCurrent(0);
                newJob.setProgressTotal(0);
                newJob.setCreatedAt(LocalDateTime.now(clock));
                newJob.setCompletedAt(LocalDateTime.now(clock));
                PresentationGenerationJob savedJob = jobRepository.saveAndFlush(newJob);
                return new SubmitResult(savedJob, true, revisionId, contentHash, scope, slidesToGenerate);
            }

            LocalDate reservationDate = LocalDate.now(clock);
            quotaService.checkAndReserveSlideQuota(owner.getId(), reservationDate, owner.getPlan(), targetSlideCount);
            submissionHook.afterQuotaReservation(owner.getId(), reservationDate, targetSlideCount);

            PresentationGenerationJob newJob = new PresentationGenerationJob();
            newJob.setOwner(owner);
            newJob.setDeckId(deckId);
            newJob.setJobType(PresentationJobType.SLIDES);
            newJob.setStatus(PresentationJobStatus.QUEUED);
            newJob.setIdempotencyKey(command.idempotencyKey());
            newJob.setRequestHash(requestHash);
            newJob.setOutlineRevisionId(revisionId);
            newJob.setProgressCurrent(0);
            newJob.setProgressTotal(targetSlideCount);
            newJob.setCreatedAt(LocalDateTime.now(clock));

            PresentationGenerationJob savedJob = jobRepository.saveAndFlush(newJob);

            for (PlannedSlide pSlide : slidesToGenerate) {
                Optional<PresentationSlide> optExisting = existingSlides.stream()
                    .filter(s -> s.getGeneratedFromOutlineRevisionId().equals(revisionId) && s.getSlideOrder() == pSlide.slideOrder())
                    .findFirst();

                if (optExisting.isEmpty()) {
                    PresentationSlide slide = new PresentationSlide();
                    slide.setDeckId(deckId);
                    slide.setSlideOrder(pSlide.slideOrder());
                    slide.setTitle(pSlide.slideTitle());
                    slide.setLayoutType(toPresentationLayout(pSlide.layout()));
                    slide.setGenerationState(PresentationSlideGenerationState.EMPTY);
                    slide.setGenerationKey(fullSlideGenerationKey(revisionId, pSlide.slideOrder()));
                    slide.setGeneratedFromOutlineRevisionId(revisionId);
                    LocalDateTime now = LocalDateTime.now(clock);
                    slide.setCreatedAt(now);
                    slide.setUpdatedAt(now);
                    slideRepository.save(slide);
                } else {
                    PresentationSlide s = optExisting.get();
                    s.setTitle(pSlide.slideTitle());
                    s.setLayoutType(toPresentationLayout(pSlide.layout()));
                    s.setGenerationKey(fullSlideGenerationKey(revisionId, pSlide.slideOrder()));
                    s.setGenerationState(PresentationSlideGenerationState.EMPTY);
                    s.setUpdatedAt(LocalDateTime.now(clock));
                    slideRepository.save(s);
                }
            }

            return new SubmitResult(savedJob, true, revisionId, contentHash, scope, slidesToGenerate);
        });

        if (result.isNew() && PresentationJobStatus.QUEUED.equals(result.job().getStatus())) {
            if (result.job().getId() == null) {
                throw new IllegalStateException("Queued slide-generation job was not persisted");
            }
            worker.generateSlidesAsync(
                result.job().getId(), deckId, owner.getId(),
                result.scope().notebookId(), result.scope().documentIds(),
                result.revisionId(), result.contentHash(),
                result.plannedSlides()
            );
        }

        return result.job();
    }

    public PresentationGenerationJob submitSlideRetry(User owner, long deckId, long slideId, SlideRetryCommand command) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required");
        }
        if (command.outlineRevisionId() <= 0 || command.outlineContentHash() == null || command.outlineContentHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outline details required for retry");
        }

        SubmitResult result = transactionTemplate.execute(status -> {
            userRepository.findByIdForUpdate(owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

            PresentationOutlineRevision approvedRevision = deck.getApprovedOutlineRevision();
            if (approvedRevision == null || !approvedRevision.getId().equals(command.outlineRevisionId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Outline revision mismatch");
            }
            if (!approvedRevision.getContentHash().equals(command.outlineContentHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Outline content hash mismatch");
            }

            PresentationSlide slide = slideRepository.findByIdAndDeckId(slideId, deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slide not found"));

            String requestHash = SlideContentHasher.retryRequestHash(deckId, command.outlineRevisionId(), command.outlineContentHash(), slide.getSlideOrder());

            Optional<PresentationGenerationJob> existingJob = jobRepository.findIdempotencyIdentityForUpdate(
                owner.getId(), deckId, PresentationJobType.SLIDE_RETRY, command.idempotencyKey()
            );

            if (existingJob.isPresent()) {
                PresentationGenerationJob job = existingJob.get();
                if (!job.getRequestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different request");
                }
                return new SubmitResult(job, false, command.outlineRevisionId(), command.outlineContentHash(), null, List.of());
            }

            if (jobRepository.existsByDeckIdAndJobTypeInAndStatusIn(deckId,
                    List.of(PresentationJobType.SLIDES, PresentationJobType.SLIDE_RETRY),
                    List.of(PresentationJobStatus.QUEUED, PresentationJobStatus.RUNNING))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Active slide generation job exists");
            }

            if (slide.getGenerationState() == PresentationSlideGenerationState.GENERATED ||
                slide.getGenerationState() == PresentationSlideGenerationState.EDITED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot retry GENERATED or EDITED slide");
            }

            GenerationScopeParser.ParsedScope scope = scopeParser.parse(approvedRevision.getGenerationScopeSnapshot());
            List<PlannedSlide> allSlides = outlineFlatteningService.flattenOutline(approvedRevision.getOutlineJson(), approvedRevision.getGenerationScopeSnapshot());
            
            PlannedSlide targetSlide = allSlides.stream()
                .filter(s -> s.slideOrder() == slide.getSlideOrder())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Slide missing in outline"));

            quotaService.checkAndReserveSlideQuota(owner.getId(), LocalDate.now(clock), owner.getPlan(), 1);

            PresentationGenerationJob newJob = new PresentationGenerationJob();
            newJob.setOwner(owner);
            newJob.setDeckId(deckId);
            newJob.setJobType(PresentationJobType.SLIDE_RETRY);
            newJob.setStatus(PresentationJobStatus.QUEUED);
            newJob.setIdempotencyKey(command.idempotencyKey());
            newJob.setRequestHash(requestHash);
            newJob.setOutlineRevisionId(command.outlineRevisionId());
            newJob.setTargetSlideId(slideId);
            newJob.setProgressCurrent(0);
            newJob.setProgressTotal(1);
            newJob.setCreatedAt(LocalDateTime.now(clock));

            jobRepository.save(newJob);

            slide.setGenerationState(PresentationSlideGenerationState.EMPTY);
            slide.setGenerationKey(requestHash);
            slideRepository.save(slide);

            return new SubmitResult(newJob, true, command.outlineRevisionId(), command.outlineContentHash(), scope, List.of(targetSlide));
        });

        if (result.isNew() && PresentationJobStatus.QUEUED.equals(result.job().getStatus())) {
            worker.generateSlidesAsync(
                result.job().getId(), deckId, owner.getId(),
                result.scope().notebookId(), result.scope().documentIds(),
                result.revisionId(), result.contentHash(),
                result.plannedSlides()
            );
        }

        return result.job();
    }

    private String fullSlideGenerationKey(long revisionId, int slideOrder) {
        return "r" + revisionId + "-s" + slideOrder;
    }

    private PresentationSlideLayout toPresentationLayout(String outlineSlideType) {
        return switch (outlineSlideType) {
            case "TITLE" -> PresentationSlideLayout.TITLE;
            case "SECTION" -> PresentationSlideLayout.SECTION;
            case "CONTENT" -> PresentationSlideLayout.TITLE_CONTENT;
            case "SUMMARY" -> PresentationSlideLayout.SUMMARY;
            default -> throw new IllegalArgumentException("Unsupported outline slide type: " + outlineSlideType);
        };
    }
}
