package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptDetailDto {
    private GraphConceptDto concept;
    private List<GraphRelationDto> incomingRelations;
    private List<GraphRelationDto> outgoingRelations;
    private List<RelationEvidenceDto> relationEvidence;
}
