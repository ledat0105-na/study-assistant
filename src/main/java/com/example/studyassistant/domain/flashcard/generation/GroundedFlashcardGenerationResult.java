package com.example.studyassistant.domain.flashcard.generation;

import java.util.List;

public record GroundedFlashcardGenerationResult(
    long documentId,
    Long conceptId,
    int requestedCount,
    int generatedCandidateCount,
    int persistedCount,
    int duplicateSkippedCount,
    List<GeneratedFlashcardResult> cards
) {}
