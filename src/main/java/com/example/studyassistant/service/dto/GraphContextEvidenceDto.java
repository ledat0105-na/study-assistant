package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphContextEvidenceDto {
    private Long relationId;
    private Long relationSourceId;
    private Long chunkId;
    private Long documentId;
    private String evidenceText;
    private Double confidence;
    private Integer pageStart;
    private Integer pageEnd;
}
