package com.example.studyassistant.service.dto;

import com.example.studyassistant.entity.ConceptRelationSource;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConceptRelationExtractionResult {
    private Long chunkId;
    private List<ConceptRelationSource> acceptedRelations;
    private int rejectedCount;
    private String status;
}
