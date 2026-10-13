package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroundedAnswer {
    private String answer;
    private List<CitationDto> citations;
    private boolean hasSufficientEvidence;
    private boolean degraded;
    private String citationQuality; // "VALID", "MISSING", "NOT_APPLICABLE"
}
