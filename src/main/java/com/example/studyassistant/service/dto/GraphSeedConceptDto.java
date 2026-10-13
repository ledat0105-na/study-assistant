package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSeedConceptDto {
    private Long conceptId;
    private String canonicalName;
    private String conceptType;
    private Double bestRetrievalScore; // Best RRF score
    private Integer bestRetrievalRank; // Fallback if RRF score is not present
    private Double bestSourceConfidence;
    private Integer supportingChunkCount;
}
