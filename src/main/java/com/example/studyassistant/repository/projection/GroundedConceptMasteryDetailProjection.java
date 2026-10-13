package com.example.studyassistant.repository.projection;

import java.time.LocalDateTime;

public interface GroundedConceptMasteryDetailProjection {
    Long getConceptId();
    String getCanonicalName();
    String getConceptType();
    String getDescription();

    Long getMasteryId();
    Double getMasteryEstimate();
    Integer getEvidenceCount();
    LocalDateTime getLastEvidenceAt();
}
