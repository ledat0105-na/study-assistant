package com.example.studyassistant.controller;

import com.example.studyassistant.domain.flashcard.FlashcardRating;
import com.example.studyassistant.domain.flashcard.generation.GroundedFlashcardGenerationCommand;
import com.example.studyassistant.domain.flashcard.generation.GroundedFlashcardGenerationResult;
import com.example.studyassistant.domain.flashcard.generation.GroundedFlashcardGenerationService;
import com.example.studyassistant.domain.flashcard.generation.exception.*;
import com.example.studyassistant.domain.flashcard.queue.FlashcardStudyQueueQuery;
import com.example.studyassistant.domain.flashcard.queue.FlashcardStudyQueueResult;
import com.example.studyassistant.domain.flashcard.queue.FlashcardStudyQueueService;
import com.example.studyassistant.domain.flashcard.FlashcardReviewCommand;
import com.example.studyassistant.domain.flashcard.FlashcardReviewResult;
import com.example.studyassistant.domain.flashcard.FlashcardReviewService;
import com.example.studyassistant.domain.flashcard.exception.FlashcardNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FlashcardPhase6Controller {

    private final GroundedFlashcardGenerationService generationService;
    private final FlashcardStudyQueueService queueService;
    private final FlashcardReviewService reviewService;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    // 1. REST generation endpoint
    @PostMapping("/documents/{documentId}/flashcards/generate")
    public ResponseEntity<?> generateFlashcards(
            @PathVariable Long documentId,
            @RequestBody(required = false) GenerationRequest requestBody,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Long conceptId = requestBody != null ? requestBody.getConceptId() : null;
        Integer requestedCount = requestBody != null ? requestBody.getRequestedCount() : null;

        GroundedFlashcardGenerationCommand command = new GroundedFlashcardGenerationCommand(
                userId, documentId, conceptId, requestedCount);

        GroundedFlashcardGenerationResult result = requestBody != null && Boolean.TRUE.equals(requestBody.getQuickStart())
                ? generationService.generateQuickBatch(command) : generationService.generate(command);
        return ResponseEntity.ok(result);
    }

    // 2. REST queue endpoint
    @GetMapping("/notebooks/{notebookId}/flashcards/queue")
    public ResponseEntity<?> getQueue(
            @PathVariable Long notebookId,
            @RequestParam(defaultValue = "50") int maxNewCards,
            @RequestParam(defaultValue = "50") int maxDueCards,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        FlashcardStudyQueueQuery query = new FlashcardStudyQueueQuery(
                userId, notebookId, maxNewCards, maxDueCards);
        FlashcardStudyQueueResult result = queueService.getQueue(query);
        return ResponseEntity.ok(result);
    }

    // 3. REST FSRS review endpoint
    @PostMapping("/flashcards/{flashcardId}/reviews")
    public ResponseEntity<?> submitReview(
            @PathVariable Long flashcardId,
            @RequestBody ReviewRequest requestBody,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (requestBody == null || requestBody.getRating() == null || requestBody.getReviewToken() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rating and reviewToken are required"));
        }

        FlashcardRating rating;
        try {
            rating = FlashcardRating.valueOf(requestBody.getRating().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid rating"));
        }

        FlashcardReviewCommand command = new FlashcardReviewCommand(userId, flashcardId, requestBody.getReviewToken(), rating, null);

        FlashcardReviewResult result = reviewService.processReview(command);
        return ResponseEntity.ok(result);
    }

    // ── Exception Handling ───────────────────────────────────────────────────

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<?> handleDocumentNotFound(DocumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(FlashcardNotFoundException.class)
    public ResponseEntity<?> handleFlashcardNotFound(FlashcardNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DocumentNotIndexedException.class)
    public ResponseEntity<?> handleDocumentNotIndexed(DocumentNotIndexedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InsufficientEvidenceException.class)
    public ResponseEntity<?> handleInsufficientEvidence(InsufficientEvidenceException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ConceptNotValidException.class)
    public ResponseEntity<?> handleConceptNotValid(ConceptNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(StaleGenerationSnapshotException.class)
    public ResponseEntity<?> handleStaleGenerationSnapshot(StaleGenerationSnapshotException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("Different rating")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(com.example.studyassistant.domain.flashcard.exception.ReviewTokenConflictException.class)
    public ResponseEntity<?> handleReviewTokenConflict(com.example.studyassistant.domain.flashcard.exception.ReviewTokenConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(com.example.studyassistant.domain.flashcard.exception.InvalidReviewTokenException.class)
    public ResponseEntity<?> handleInvalidReviewToken(com.example.studyassistant.domain.flashcard.exception.InvalidReviewTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    public static class GenerationRequest {
        private Long conceptId;
        private Integer requestedCount;
        private Boolean quickStart;
    }

    @Data
    public static class ReviewRequest {
        private String rating;
        private String reviewToken;
    }
}





