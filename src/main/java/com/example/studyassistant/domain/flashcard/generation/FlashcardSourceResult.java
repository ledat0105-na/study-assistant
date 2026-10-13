package com.example.studyassistant.domain.flashcard.generation;

public record FlashcardSourceResult(
    long documentChunkId,
    int sourceOrder,
    Integer pageStart,
    Integer pageEnd,
    String sectionTitle
) {}
