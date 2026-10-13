package com.example.studyassistant.service.dto;

import lombok.Data;

@Data
public class ExtractedRelationCandidate {
    private Long sourceConceptId;
    private Long targetConceptId;
    private String relationType;
    private Double confidence;
    private String evidenceText;
}
