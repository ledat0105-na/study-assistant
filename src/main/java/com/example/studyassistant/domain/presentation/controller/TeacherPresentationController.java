package com.example.studyassistant.domain.presentation.controller;

import com.example.studyassistant.domain.presentation.dto.*;
import com.example.studyassistant.domain.presentation.generation.PresentationOutlineGenerationService;
import com.example.studyassistant.domain.presentation.service.PresentationService;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/presentations")
@RequiredArgsConstructor
public class TeacherPresentationController {

    private final TeacherAuthorizationService authService;
    private final PresentationService presentationService;
    private final PresentationOutlineGenerationService outlineGenerationService;
    private final com.example.studyassistant.domain.presentation.generation.PresentationSlideGenerationService slideGenerationService;

    @PostMapping
    public ResponseEntity<PresentationDeckDto> createDeck(@RequestBody PresentationDeckCreateCommand cmd, HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        PresentationDeckDto deck = presentationService.createDeck(user, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(deck);
    }

    @GetMapping
    public ResponseEntity<Page<PresentationDeckDto>> listDecks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        Page<PresentationDeckDto> decks = presentationService.listDecks(user, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        return ResponseEntity.ok(decks);
    }

    @GetMapping("/{deckId}")
    public ResponseEntity<PresentationDeckDto> getDeck(@PathVariable Long deckId, HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        return ResponseEntity.ok(presentationService.getDeck(user, deckId));
    }

    @PatchMapping("/{deckId}")
    public ResponseEntity<PresentationDeckDto> patchDeck(
            @PathVariable Long deckId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody PresentationDeckPatchCommand cmd,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        Long version = parseIfMatch(ifMatch);
        return ResponseEntity.ok(presentationService.patchDeck(user, deckId, cmd, version));
    }

    @PostMapping("/{deckId}/archive")
    public ResponseEntity<PresentationDeckDto> archiveDeck(
            @PathVariable Long deckId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        Long version = parseIfMatch(ifMatch);
        return ResponseEntity.ok(presentationService.archiveDeck(user, deckId, version));
    }
    
    @GetMapping("/{deckId}/outlines")
    public ResponseEntity<List<PresentationOutlineRevisionDto>> getOutlines(@PathVariable Long deckId, HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        return ResponseEntity.ok(presentationService.getOutlines(user, deckId));
    }
    
    @PostMapping("/{deckId}/outlines/{revisionId}/approve")
    public ResponseEntity<Void> approveOutline(
            @PathVariable Long deckId,
            @PathVariable Long revisionId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        Long version = parseIfMatch(ifMatch);
        presentationService.approveOutline(user, deckId, revisionId, version);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{deckId}/jobs/{jobId}")
    public ResponseEntity<PresentationGenerationJobDto> getJob(
            @PathVariable Long deckId,
            @PathVariable Long jobId,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        return ResponseEntity.ok(presentationService.getJob(user, deckId, jobId));
    }

    /**
     * POST /api/teacher/presentations/{deckId}/outlines/generate
     *
     * Submit an AI outline generation request.
     * Returns 202 Accepted with a job DTO immediately.
     * The job runs asynchronously; poll GET /jobs/{jobId} for status.
     *
     * Idempotency-Key header is REQUIRED.
     * Same key + same request body → returns existing job (no quota charge).
     * Same key + different body   → 409 Conflict.
     */
    @PostMapping("/{deckId}/outlines/generate")
    public ResponseEntity<PresentationGenerationJobDto> generateOutline(
            @PathVariable Long deckId,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody PresentationOutlineGenerationCommand cmd,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        // Merge idempotencyKey from header into the command
        PresentationOutlineGenerationCommand enriched = new PresentationOutlineGenerationCommand(
                idempotencyKey,
                cmd.topic(),
                cmd.teacherInstruction(),
                cmd.targetSlideCount(),
                cmd.audience(),
                cmd.language(),
                cmd.documentIds()
        );
        PresentationGenerationJobDto job = outlineGenerationService.submitOutlineGeneration(user, deckId, enriched);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @PostMapping("/{deckId}/slides/generate")
    public ResponseEntity<PresentationGenerationJobDto> generateSlides(
            @PathVariable Long deckId,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        SlideGenerationCommand cmd = new SlideGenerationCommand(idempotencyKey);
        var job = slideGenerationService.submitFullGeneration(user, deckId, cmd);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(presentationService.getJob(user, deckId, job.getId()));
    }

    @PostMapping("/{deckId}/slides/{slideId}/retry")
    public ResponseEntity<PresentationGenerationJobDto> retrySlide(
            @PathVariable Long deckId,
            @PathVariable Long slideId,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody SlideRetryCommand req,
            HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        SlideRetryCommand cmd = new SlideRetryCommand(idempotencyKey, req.outlineRevisionId(), req.outlineContentHash());
        var job = slideGenerationService.submitSlideRetry(user, deckId, slideId, cmd);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(presentationService.getJob(user, deckId, job.getId()));
    }

    private Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        try {
            return Long.parseLong(ifMatch.replace("\"", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
