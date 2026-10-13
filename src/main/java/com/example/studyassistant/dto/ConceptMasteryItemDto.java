package com.example.studyassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptMasteryItemDto {
    private Long conceptId;
    private String canonicalName;
    private String conceptType;
    private MasteryState state;
    private Double masteryEstimate;
    private Integer evidenceCount;
    private LocalDateTime lastEvidenceAt;
}
