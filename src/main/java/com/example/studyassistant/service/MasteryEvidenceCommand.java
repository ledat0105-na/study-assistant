package com.example.studyassistant.service;

import java.time.LocalDateTime;

public record MasteryEvidenceCommand(
    Long userId,
    Long notebookId,
    Long conceptId,
    String eventType,
    double normalizedScore,
    String sourceType,
    String sourceId,
    String idempotencyKey,
    LocalDateTime occurredAt
) {
    public MasteryEvidenceCommand {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (notebookId == null) throw new IllegalArgumentException("notebookId must not be null");
        if (conceptId == null) throw new IllegalArgumentException("conceptId must not be null");
        
        if (eventType == null || eventType.isBlank() || eventType.length() > 50) {
            throw new IllegalArgumentException("eventType must be non-blank and <= 50 characters");
        }
        
        if (!Double.isFinite(normalizedScore) || normalizedScore < 0.0 || normalizedScore > 1.0) {
            throw new IllegalArgumentException("normalizedScore must be finite and between 0.0 and 1.0");
        }
        
        if (sourceType == null || sourceType.isBlank() || sourceType.length() > 50) {
            throw new IllegalArgumentException("sourceType must be non-blank and <= 50 characters");
        }
        
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 128) {
            throw new IllegalArgumentException("sourceId must be non-blank and <= 128 characters");
        }
        
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be non-blank and <= 128 characters");
        }
        
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}
