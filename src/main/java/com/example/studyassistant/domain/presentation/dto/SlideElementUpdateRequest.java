package com.example.studyassistant.domain.presentation.dto;

import java.math.BigDecimal;
import java.util.Map;

public record SlideElementUpdateRequest(
        String elementType,
        Long expectedSlideVersion,
        Long expectedElementVersion,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        BigDecimal rotation,
        Long mediaAssetId,
        Map<String, Object> payload,
        Map<String, Object> style,
        Integer zIndex
) {}
