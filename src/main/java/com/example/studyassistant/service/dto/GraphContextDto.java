package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphContextDto {
    private List<GraphSeedConceptDto> seeds;
    private List<GraphContextRelationDto> relations;
}
