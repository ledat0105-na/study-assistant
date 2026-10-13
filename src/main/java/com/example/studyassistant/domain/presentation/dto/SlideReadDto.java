package com.example.studyassistant.domain.presentation.dto;

import java.util.List;

public record SlideReadDto(
        Long id,
        Long deckId,
        Integer slideOrder,
        String layoutType,
        String title,
        String speakerNotes,
        String generationState,
        Long version,
        Long outlineRevisionId,
        List<SlideElementReadDto> elements,
        List<SlideSourceReadDto> sources
) {}
