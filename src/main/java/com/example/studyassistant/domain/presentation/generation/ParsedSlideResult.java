package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Validated and parsed LLM slide result, ready for persistence.
 * Only server-trusted values. LLM-supplied server-owned fields are rejected upstream.
 */
public record ParsedSlideResult(
    String title,
    PresentationSlideLayout layout,
    String speakerNotes,
    List<ParsedElement> elements,
    List<String> sourceRefs  // deduped, validated aliases
) {
    public record ParsedElement(
        String type,  // TEXT or SHAPE
        BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
        Map<String, Object> payload,
        Map<String, Object> style
    ) {}
}
