package com.example.studyassistant.service;

import java.time.LocalDateTime;

public record MasteryUpdateResult(
    boolean applied,
    Long conceptId,
    Double masteryEstimate,
    Integer evidenceCount,
    LocalDateTime lastEvidenceAt
) {
    /**
     * Note: If applied = false (due to duplicate idempotency), the snapshot returned
     * represents the CURRENT mastery state in the database, which may include subsequent
     * learning events that happened after this retried event.
     */
}
