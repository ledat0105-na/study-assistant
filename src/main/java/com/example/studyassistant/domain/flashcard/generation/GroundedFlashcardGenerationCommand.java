package com.example.studyassistant.domain.flashcard.generation;

public record GroundedFlashcardGenerationCommand(
    long userId,
    long documentId,
    Long conceptId,        // null = document-level mode
    Integer requestedCount // null = use default
) {}
