package com.example.studyassistant.domain.presentation.generation;

import java.util.List;

public record PlannedSlide(
    int slideOrder,
    String slideTitle,
    String layout,
    List<String> contentPoints,
    String purpose,
    String language,
    List<ApprovedSource> approvedSources
) {
    public record ApprovedSource(String alias, Long chunkId, Long documentId, String indexVersion) {}
    public PlannedSlide(int order, String title, String layout, List<String> points) {
        this(order, title, layout, points, "", "same as source material", null);
    }
}
