package com.example.studyassistant.domain.presentation.dto;
import java.time.LocalDateTime;
public record PresentationOutlineRevisionDto(
    Long id, Integer revisionNumber, String status, String outlineJson,
    Long createdBy, LocalDateTime createdAt, LocalDateTime approvedAt,
    String contentHash
) {}
