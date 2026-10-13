package com.example.studyassistant.domain.flashcard.generation;

import java.util.List;

public record GeneratedFlashcardResult(
    long flashcardId,
    String question,
    String answer,
    Long conceptId,
    String contentHash,
    List<FlashcardSourceResult> sources
) {}
