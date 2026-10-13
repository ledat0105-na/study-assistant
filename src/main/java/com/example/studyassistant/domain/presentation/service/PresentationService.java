package com.example.studyassistant.domain.presentation.service;

import com.example.studyassistant.domain.presentation.dto.*;
import com.example.studyassistant.domain.presentation.*;


import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresentationService {
    private final PresentationDeckRepository deckRepository;
    private final PresentationOutlineRevisionRepository outlineRepository;
    private final PresentationGenerationJobRepository jobRepository;
    private final NotebookRepository notebookRepository;
    private final Clock clock; // INJECTABLE CLOCK

    @Transactional
    public PresentationDeckDto createDeck(User owner, PresentationDeckCreateCommand cmd) {
        if (cmd.title() == null || cmd.title().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        Notebook notebook = notebookRepository.findById(cmd.notebookId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook not found"));
            
        if (!notebook.getUser().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook not found or foreign");
        }
        
        PresentationDeck deck = new PresentationDeck();
        deck.setOwner(owner);
        deck.setNotebook(notebook);
        deck.setTitle(cmd.title());
        deck.setDescription(cmd.description());
        deck.setAspectRatio(cmd.aspectRatio() != null ? cmd.aspectRatio() : "16:9");
        if (!"16:9".equals(deck.getAspectRatio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aspect ratio must be 16:9");
        }
        deck.setThemeKey(cmd.themeKey());
        deck.setStatus(PresentationDeckStatus.DRAFT);
        
        LocalDateTime now = LocalDateTime.now(clock);
        deck.setCreatedAt(now);
        deck.setUpdatedAt(now);
        
        deck = deckRepository.save(deck);
        return mapToDto(deck);
    }

    @Transactional(readOnly = true)
    public Page<PresentationDeckDto> listDecks(User owner, Pageable pageable) {
        return deckRepository.findWorkspaceDecks(owner.getId(), PresentationDeckStatus.ARCHIVED, pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public PresentationDeckDto getDeck(User owner, Long deckId) {
        return deckRepository.findByIdAndOwnerId(deckId, owner.getId())
            .map(this::mapToDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
    }

    @Transactional
    public PresentationDeckDto patchDeck(User owner, Long deckId, PresentationDeckPatchCommand cmd, Long version) {
        PresentationDeck deck = deckRepository.findByIdAndOwnerId(deckId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
            
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match header with version is required");
        }
        if (!deck.getVersion().equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale optimistic version");
        }
        
        if (cmd.title() != null && !cmd.title().trim().isEmpty()) {
            deck.setTitle(cmd.title());
        }
        if (cmd.description() != null) {
            deck.setDescription(cmd.description());
        }
        if (cmd.themeKey() != null) {
            deck.setThemeKey(cmd.themeKey());
        }
        
        deck.setUpdatedAt(LocalDateTime.now(clock));
        
        deck = deckRepository.save(deck);
        return mapToDto(deck);
    }

    @Transactional
    public PresentationDeckDto archiveDeck(User owner, Long deckId, Long version) {
        PresentationDeck deck = deckRepository.findByIdAndOwnerId(deckId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
            
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match header with version is required");
        }
        if (!deck.getVersion().equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale optimistic version");
        }
        
        if ("ARCHIVED".equals(deck.getStatus().name())) {
            return mapToDto(deck); // Idempotent
        }
        
        deck.setStatus(PresentationDeckStatus.ARCHIVED);
        LocalDateTime now = LocalDateTime.now(clock);
        deck.setArchivedAt(now);
        deck.setUpdatedAt(now);
        deck = deckRepository.save(deck);
        return mapToDto(deck);
    }
    
    @Transactional(readOnly = true)
    public List<PresentationOutlineRevisionDto> getOutlines(User owner, Long deckId) {
        PresentationDeck deck = deckRepository.findByIdAndOwnerId(deckId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

        return outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deck.getId()).stream()
            .map(this::mapOutlineToDto)
            .toList();
    }

    @Transactional
    public void approveOutline(User owner, Long deckId, Long revisionId, Long expectedVersion) {
        PresentationDeck deck = deckRepository.findByIdAndOwnerId(deckId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
            
        if ("ARCHIVED".equals(deck.getStatus().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck is archived");
        }
        if (expectedVersion == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match header with version is required");
        }
        if (!deck.getVersion().equals(expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale optimistic version");
        }
        
        PresentationOutlineRevision revision = outlineRepository.findByIdAndDeckId(revisionId, deckId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outline not found for this deck"));
            
        if (!PresentationOutlineStatus.DRAFT.equals(revision.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revision is not DRAFT");
        }
        
        if (deck.getApprovedOutlineRevision() != null) {
            PresentationOutlineRevision current = deck.getApprovedOutlineRevision();
            current.setStatus(PresentationOutlineStatus.SUPERSEDED);
            outlineRepository.save(current);
        }
        
        revision.setStatus(PresentationOutlineStatus.APPROVED);
        revision.setApprovedAt(LocalDateTime.now(clock));
        outlineRepository.save(revision);
        
        deck.setApprovedOutlineRevision(revision);
        deck.setStatus(PresentationDeckStatus.OUTLINE_READY);
        deckRepository.save(deck);
    }
    
    public PresentationGenerationJobDto getJob(User owner, Long deckId, Long jobId) {
        // Just checking owner/deck match is good, but the repo method findByIdAndOwnerId is sufficient
        PresentationGenerationJob job = jobRepository.findByIdAndOwnerIdAndDeckId(jobId, owner.getId(), deckId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
            
        if (!job.getDeckId().equals(deckId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        
        return new PresentationGenerationJobDto(
            job.getId(), job.getJobType().name(), job.getStatus().name(), job.getProgressCurrent(),
            job.getProgressTotal(), job.getErrorCode(), job.getErrorSummary(),
            job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(),
            job.getOutlineRevisionId()
        );
    }
    
    @Transactional
    public PresentationGenerationJobDto idempotentCreateJob(User owner, Long deckId, String jobType, String idempotencyKey, String requestHash) {
        PresentationGenerationJob existing = jobRepository.findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
            owner.getId(), deckId, PresentationJobType.valueOf(jobType), idempotencyKey).orElse(null);
            
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key conflict with different request hash");
            }
            return new PresentationGenerationJobDto(
                existing.getId(), existing.getJobType().name(), existing.getStatus().name(), existing.getProgressCurrent(),
                existing.getProgressTotal(), existing.getErrorCode(), existing.getErrorSummary(),
                existing.getCreatedAt(), existing.getStartedAt(), existing.getCompletedAt(),
                existing.getOutlineRevisionId()
            );
        }
        
        PresentationGenerationJob job = new PresentationGenerationJob();
        job.setOwner(owner);
        job.setDeckId(deckId);
        job.setJobType(PresentationJobType.valueOf(jobType));
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestHash(requestHash);
        job.setStatus(PresentationJobStatus.QUEUED);
        job.setCreatedAt(LocalDateTime.now(clock));
        job = jobRepository.save(job);
        
        return new PresentationGenerationJobDto(
            job.getId(), job.getJobType().name(), job.getStatus().name(), job.getProgressCurrent(),
            job.getProgressTotal(), job.getErrorCode(), job.getErrorSummary(),
            job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(),
            job.getOutlineRevisionId()
        );
    }

    private PresentationOutlineRevisionDto mapOutlineToDto(PresentationOutlineRevision revision) {
        return new PresentationOutlineRevisionDto(
            revision.getId(),
            revision.getRevisionNumber(),
            revision.getStatus() != null ? revision.getStatus().name() : null,
            revision.getOutlineJson(),
            revision.getCreatedBy() != null ? revision.getCreatedBy().getId() : null,
            revision.getCreatedAt(),
            revision.getApprovedAt(),
            revision.getContentHash()
        );
    }

    private PresentationDeckDto mapToDto(PresentationDeck deck) {
        return new PresentationDeckDto(
            deck.getId(), deck.getNotebook().getId(), deck.getTitle(), deck.getDescription(),
            deck.getStatus().name(), deck.getAspectRatio(), deck.getThemeKey(),
            deck.getApprovedOutlineRevision() != null ? deck.getApprovedOutlineRevision().getId() : null,
            deck.getVersion(), deck.getCreatedAt(), deck.getUpdatedAt(), deck.getArchivedAt()
        );
    }
}
