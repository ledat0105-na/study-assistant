package com.example.studyassistant.domain.presentation.dto;

import java.math.BigDecimal;
import java.util.Map;

public record SlideElementReadDto(
        Long id,
        Long slideId,
        String elementType,
        Integer zIndex,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        BigDecimal rotation,
        Long version,
        Map<String, Object> payload,
        Map<String, Object> style,
        Long mediaAssetId,
        String originalFilename,
        String mimeType,
        Integer mediaWidth,
        Integer mediaHeight,
        String contentUrl
) {}
