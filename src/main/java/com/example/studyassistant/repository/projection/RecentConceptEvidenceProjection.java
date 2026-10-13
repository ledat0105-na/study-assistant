package com.example.studyassistant.repository.projection;

public interface RecentConceptEvidenceProjection {
    Long getConceptId();
    Long getEvidenceCount();
    Long getCorrectCount();
}