package com.example.studyassistant.domain.flashcard.queue;

import java.time.Instant;
import java.util.List;

public record FlashcardStudyQueueResult(
    Instant generatedAt,
    List<FlashcardQueueItem> dueCards,
    List<FlashcardQueueItem> newCards
) {}
