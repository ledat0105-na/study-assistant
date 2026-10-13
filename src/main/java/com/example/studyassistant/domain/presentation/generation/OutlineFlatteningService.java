package com.example.studyassistant.domain.presentation.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OutlineFlatteningService {

    private final ObjectMapper objectMapper;

    public OutlineFlatteningService(@Qualifier("legacyJackson2ObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PlannedSlide> flattenOutline(String outlineJson) {
        return flattenOutline(outlineJson, null);
    }

    public List<PlannedSlide> flattenOutline(String outlineJson, String scopeSnapshot) {
        try {
            OutlineJsonSchema schema = objectMapper.readValue(outlineJson, OutlineJsonSchema.class);
            java.util.Map<String, PlannedSlide.ApprovedSource> sources = new java.util.LinkedHashMap<>();
            if (scopeSnapshot != null) {
                var snapshot = objectMapper.readTree(scopeSnapshot);
                for (var source : snapshot.path("sources")) {
                    String alias = source.path("alias").asText();
                    long chunkId = source.path("chunkId").asLong(), documentId = source.path("documentId").asLong();
                    String version = source.path("indexVersion").asText();
                    if (alias.isBlank() || chunkId <= 0 || documentId <= 0 || version.isBlank()) throw new IllegalStateException("Invalid approved source");
                    if (sources.put(alias, new PlannedSlide.ApprovedSource(alias, chunkId, documentId, version)) != null) throw new IllegalStateException("Duplicate approved alias");
                }
            }
            List<PlannedSlide> slides = new ArrayList<>();
            int order = 1;
            for (OutlineJsonSchema.OutlineSection section : schema.sections()) {
                if (section.slides() != null) {
                    for (OutlineJsonSchema.OutlineSlide slide : section.slides()) {
                        slides.add(new PlannedSlide(
                            order++,
                            slide.title(),
                            slide.slideType(),
                            slide.keyPoints() == null ? List.of() : slide.keyPoints(),
                            slide.purpose(), schema.language(),
                            scopeSnapshot == null ? null : (slide.sourceRefs() == null ? List.<String>of() : slide.sourceRefs()).stream()
                                    .map(alias -> {
                                        var source = sources.get(alias);
                                        if (source == null) throw new IllegalStateException("Approved source not found: " + alias);
                                        return source;
                                    }).toList()
                        ));
                    }
                }
            }
            return slides;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse outline JSON", e);
        }
    }
}
