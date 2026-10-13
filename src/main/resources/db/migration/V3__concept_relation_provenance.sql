-- ============================================================
-- V3__concept_relation_provenance.sql
-- KG4 Checkpoint 2: Concept Relation Provenance Foundation
-- ============================================================

CREATE TABLE IF NOT EXISTS concept_relation_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    concept_relation_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    evidence_text TEXT NOT NULL,
    confidence DOUBLE DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_crs_relation
        FOREIGN KEY (concept_relation_id)
        REFERENCES concept_relations(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_crs_chunk
        FOREIGN KEY (document_chunk_id)
        REFERENCES document_chunks(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_relation_chunk
        UNIQUE (concept_relation_id, document_chunk_id)
)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_crs_chunk_id ON concept_relation_sources (document_chunk_id);
