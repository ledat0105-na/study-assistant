package com.example.studyassistant.domain.presentation.dto;
public record PresentationDeckCreateCommand(
    Long notebookId, String title, String description, String aspectRatio, String themeKey
) {}
