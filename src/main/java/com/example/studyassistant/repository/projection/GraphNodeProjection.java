package com.example.studyassistant.repository.projection;

public interface GraphNodeProjection {
    Long getConceptId();
    String getCanonicalName();
    String getConceptType();
    String getDescription();
    Long getSourceCount();
}
