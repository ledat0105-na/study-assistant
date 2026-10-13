package com.example.studyassistant.repository.projection;

public interface GraphSeedCandidateProjection {
    Long getConceptId();
    String getCanonicalName();
    String getConceptType();
    Long getChunkId();
    Double getConceptSourceConfidence();
}
