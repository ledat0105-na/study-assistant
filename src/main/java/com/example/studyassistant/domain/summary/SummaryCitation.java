package com.example.studyassistant.domain.summary;

public record SummaryCitation(
        String sourceId,
        Long chunkId,
        Long documentId,
        Integer pageStart,
        Integer pageEnd,
        String sectionTitle) {
}
