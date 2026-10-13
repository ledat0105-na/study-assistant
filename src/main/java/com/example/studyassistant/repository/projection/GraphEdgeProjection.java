package com.example.studyassistant.repository.projection;

import com.example.studyassistant.entity.RelationType;

public interface GraphEdgeProjection {
    Long getRelationId();
    Long getSourceConceptId();
    Long getTargetConceptId();
    RelationType getRelationType();
    Double getEdgeConfidence();
    Long getEvidenceCount();
}
