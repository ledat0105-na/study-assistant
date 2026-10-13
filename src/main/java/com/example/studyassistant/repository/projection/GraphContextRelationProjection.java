package com.example.studyassistant.repository.projection;

import com.example.studyassistant.entity.RelationType;

public interface GraphContextRelationProjection {
    Long getRelationId();
    Long getSourceConceptId();
    String getSourceCanonicalName();
    Long getTargetConceptId();
    String getTargetCanonicalName();
    RelationType getRelationType();
    Double getRelationConfidence();
    Long getActiveEvidenceCount();
}
