package com.example.studyassistant.repository.projection;

public interface GraphContextEvidenceProjection {
    Long getRelationId();
    Long getRelationSourceId();
    Long getChunkId();
    Long getDocumentId();
    String getEvidenceText();
    Double getConfidence();
    Integer getPageStart();
    Integer getPageEnd();
}
