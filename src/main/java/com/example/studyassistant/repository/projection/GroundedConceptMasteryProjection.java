package com.example.studyassistant.repository.projection;

import java.time.LocalDateTime;

public interface GroundedConceptMasteryProjection {
    Long getConceptId();
    String getCanonicalName();
    String getConceptType();

    Long getMasteryId();
    Double getMasteryEstimate();
    Integer getEvidenceCount();
    LocalDateTime getLastEvidenceAt();
}
