package com.example.studyassistant.repository.projection;

public interface RelationEvidenceProjection {
    Long getRelationId();
    Long getChunkId();
    Long getDocumentId();
    String getDocumentTitle();
    String getEvidenceText();
    Double getConfidence();
}
