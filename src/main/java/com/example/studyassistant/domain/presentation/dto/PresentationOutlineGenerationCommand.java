package com.example.studyassistant.domain.presentation.dto;

import java.util.List;

/**
 * Input command for AI outline generation.
 *
 * ownerId/userId is NEVER accepted from client — always derived server-side from
 * the authenticated session via TeacherAuthorizationService.
 *
 * documentIds: optional subset of documents from the deck's notebook.
 *   If null or empty, all authorized READY documents in the notebook are used.
 *   Any supplied ID must belong to the authenticated user's notebook.
 *
 * idempotencyKey: client-supplied opaque string (max 128 chars).
 *   Same key + same requestHash -> reuse existing job (no quota charge).
 *   Same key + different requestHash -> 409 conflict.
 */
public record PresentationOutlineGenerationCommand(
    String idempotencyKey,
    String topic,
    String teacherInstruction,
    Integer targetSlideCount,
    String audience,
    String language,
    List<Long> documentIds
) {}
