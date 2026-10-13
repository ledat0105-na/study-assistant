package com.example.studyassistant.domain.presentation.dto;

public record SlideRetryCommand(
    String idempotencyKey,
    long outlineRevisionId,
    String outlineContentHash
) {}
