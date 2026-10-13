package com.example.studyassistant.service.dto;

import com.example.studyassistant.entity.ConceptSource;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConceptExtractionResult {
    private Long chunkId;
    private List<ConceptSource> acceptedConcepts;
    private int rejectedCount;
    private String status;
}
