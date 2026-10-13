package com.example.studyassistant.domain.presentation.dto;

public record SlideSourceReadDto(
        Long id,
        Long documentId,
        Integer sourceOrder,
        String evidenceExcerpt,
        String sourceAlias,
        Integer pageStart,
        Integer pageEnd,
        String sectionTitle
) {}
