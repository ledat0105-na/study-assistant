package com.example.studyassistant.service.dto;

import lombok.Data;

@Data
public class ExtractedConceptCandidate {
    private String name;
    private String description;
    private String conceptType;
    private String evidenceText;
    private Double confidence;
}
