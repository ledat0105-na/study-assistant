-- ============================================================
-- V2__knowledge_graph_foundation.sql
-- Phase 2 Checkpoint 1: Knowledge Graph Data Model Foundation
-- ============================================================

CREATE TABLE IF NOT EXISTS concepts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notebook_id BIGINT NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    concept_type VARCHAR(50) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_concept_notebook
        FOREIGN KEY (notebook_id) REFERENCES notebooks (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_notebook_normalized_name
        UNIQUE (notebook_id, normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concept_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    concept_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    evidence_text TEXT NOT NULL,
    confidence DOUBLE DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_cs_concept
        FOREIGN KEY (concept_id) REFERENCES concepts (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cs_chunk
        FOREIGN KEY (document_chunk_id) REFERENCES document_chunks (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_concept_chunk
        UNIQUE (concept_id, document_chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concept_relations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_concept_id BIGINT NOT NULL,
    target_concept_id BIGINT NOT NULL,
    relation_type VARCHAR(50) NOT NULL,
    confidence DOUBLE DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_cr_source
        FOREIGN KEY (source_concept_id) REFERENCES concepts (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cr_target
        FOREIGN KEY (target_concept_id) REFERENCES concepts (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_source_target_type
        UNIQUE (source_concept_id, target_concept_id, relation_type),
    CONSTRAINT chk_no_self_relation
        CHECK (source_concept_id <> target_concept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for performance
CREATE INDEX idx_concept_notebook_id ON concepts (notebook_id);
CREATE INDEX idx_cs_concept_id ON concept_sources (concept_id);
CREATE INDEX idx_cs_chunk_id ON concept_sources (document_chunk_id);
CREATE INDEX idx_cr_source_id ON concept_relations (source_concept_id);
CREATE INDEX idx_cr_target_id ON concept_relations (target_concept_id);
