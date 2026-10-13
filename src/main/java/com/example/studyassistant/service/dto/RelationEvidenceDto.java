package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationEvidenceDto {
    private Long relationId;
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String evidenceText;
    private Double confidence;
}
