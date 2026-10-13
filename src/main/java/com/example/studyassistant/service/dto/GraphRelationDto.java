package com.example.studyassistant.service.dto;

import com.example.studyassistant.entity.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphRelationDto {
    public Long getRelationId() { return id; }
    private Long id;
    private Long sourceConceptId;
    private Long targetConceptId;
    private RelationType relationType;
    private Double confidence;
    private Long evidenceCount;
}
