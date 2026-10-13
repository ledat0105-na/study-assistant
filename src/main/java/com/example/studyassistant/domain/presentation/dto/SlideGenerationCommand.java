package com.example.studyassistant.domain.presentation.dto;

public record SlideGenerationCommand(
    String idempotencyKey
) {}
