package com.example.studyassistant.service.dto;

import com.example.studyassistant.entity.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphContextRelationDto {
    private Long relationId;
    private Long sourceConceptId;
    private String sourceCanonicalName;
    private Long targetConceptId;
    private String targetCanonicalName;
    private RelationType relationType;
    private Double confidence;
    private Long activeEvidenceCount;
    private Integer seedEndpointCount;
    private List<GraphContextEvidenceDto> evidence;
}
