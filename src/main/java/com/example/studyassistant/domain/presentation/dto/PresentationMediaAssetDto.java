package com.example.studyassistant.domain.presentation.dto;

import java.time.LocalDateTime;

public record PresentationMediaAssetDto(
        Long id,
        Long deckId,
        String sourceType,
        String originalFilename,
        String mimeType,
        Long byteSize,
        Integer width,
        Integer height,
        String contentHash,
        LocalDateTime createdAt,
        String contentUrl) {}