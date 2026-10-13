package com.example.studyassistant.service.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExtractionResponseEnvelope {
    private List<ExtractedConceptCandidate> concepts;
}
