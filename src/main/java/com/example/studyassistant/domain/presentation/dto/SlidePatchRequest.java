package com.example.studyassistant.domain.presentation.dto;

public record SlidePatchRequest(
        String title,
        String speakerNotes,
        Long expectedSlideVersion
) {}
