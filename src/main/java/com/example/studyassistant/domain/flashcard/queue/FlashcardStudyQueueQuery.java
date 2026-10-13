package com.example.studyassistant.domain.flashcard.queue;

public record FlashcardStudyQueueQuery(
    long userId,
    long notebookId,
    Integer dueLimit,
    Integer newLimit
) {}
