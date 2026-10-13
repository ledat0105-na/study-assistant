package com.example.studyassistant.domain.presentation.generation;

import java.util.List;

/**
 * Strict schema for outline JSON, schema version 1.
 *
 * All records are used for:
 * 1. Parsing the LLM response (via legacyJackson2ObjectMapper)
 * 2. Server-side semantic validation
 * 3. Canonical serialization for content hash
 * 4. Persistence in outline_json column
 *
 * schemaVersion: must equal 1.
 * title: non-blank.
 * learningObjective: non-blank.
 * audience: non-blank or derived from request.
 * language: non-blank or derived from request.
 * sections: >= 1, each with >= 1 slide.
 *
 * slide.sourceRefs: opaque server-assigned aliases ("S1", "S2", ...).
 *   - Factual slides: >= 1 valid ref required.
 *   - TITLE / SECTION-DIVIDER structural slides: may be empty.
 *   - Unknown refs: dropped; factual slides without valid evidence are omitted.
 *   - Duplicate refs: normalized to distinct list.
 */
public record OutlineJsonSchema(
    int schemaVersion,
    String title,
    String learningObjective,
    String audience,
    String language,
    List<OutlineSection> sections
) {

    public record OutlineSection(
        String title,
        String purpose,
        List<OutlineSlide> slides
    ) {}

    public record OutlineSlide(
        String title,
        String purpose,
        String slideType,       // "TITLE" | "SECTION" | "CONTENT" | "SUMMARY" — server inferred/validated
        List<String> keyPoints,
        List<String> sourceRefs
    ) {}
}
