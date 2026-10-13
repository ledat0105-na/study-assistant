package com.example.studyassistant.domain.presentation.dto;
import java.time.LocalDateTime;
public record PresentationGenerationJobDto(
    Long jobId, String jobType, String status, Integer progressCurrent,
    Integer progressTotal, String errorCode, String errorSummary,
    LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime completedAt,
    Long outlineRevisionId
) {}
