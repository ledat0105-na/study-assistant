package com.example.studyassistant.domain.presentation.dto;
import java.time.LocalDateTime;
public record PresentationDeckDto(
    Long id, Long notebookId, String title, String description,
    String status, String aspectRatio, String themeKey,
    Long approvedOutlineRevisionId, Long version,
    LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime archivedAt
) {}
