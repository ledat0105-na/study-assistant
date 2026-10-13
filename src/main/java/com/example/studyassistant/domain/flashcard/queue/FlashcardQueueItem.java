package com.example.studyassistant.domain.flashcard.queue;

import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import java.time.Instant;

public record FlashcardQueueItem(
    long flashcardId,
    String question,
    String answer,
    Long conceptId,
    FlashcardQueueType queueType,
    Instant dueAt,
    FlashcardScheduleStatus scheduleStatus
) {}
