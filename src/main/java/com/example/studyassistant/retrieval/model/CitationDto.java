package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationDto {
    private String citationId; // e.g. "S1"
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String pages; // formatted "page 7" or "pages 7-8"
    private String sectionTitle;
    private String snippet;
}
