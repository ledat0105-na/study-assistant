package com.example.studyassistant.domain.presentation.dto;

public record SlideElementDeleteRequest(
        Long expectedSlideVersion,
        Long expectedElementVersion
) {}
