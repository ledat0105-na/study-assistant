package com.example.studyassistant.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphConceptDto {
    private Long id;
    private String canonicalName;
    private String conceptType;
    private String description;
    private Long sourceCount;
    public Long getConceptId() { return id; }
    public String getName() { return canonicalName; }
}
