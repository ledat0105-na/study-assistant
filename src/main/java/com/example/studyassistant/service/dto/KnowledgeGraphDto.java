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
public class KnowledgeGraphDto {
    private Long notebookId;
    private Long documentId;
    private String scopeType;
    private String activeIndexVersion;
    private String generationStatus;
    private String code;
    private List<String> degradationCodes;
    private List<GraphConceptDto> nodes;
    private List<GraphRelationDto> edges;
}
