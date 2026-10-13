-- ============================================================
-- V1__rag_foundation.sql
-- RAG V1 Checkpoint 1: Data model foundation
--
-- Purpose: Additive schema changes only.
-- No existing tables/columns are modified destructively.
-- Safe to run against an existing database.
-- ============================================================

-- 1. Add RAG metadata columns to documents table (additive, nullable)
-- Note: 'IF NOT EXISTS' for ADD COLUMN is not supported in MySQL < 8.0.28.
-- Ensure this script is only run ONCE.
ALTER TABLE documents
    ADD COLUMN content_hash       VARCHAR(64)  DEFAULT NULL,
    ADD COLUMN active_index_version VARCHAR(128) DEFAULT NULL,
    ADD COLUMN embedding_model    VARCHAR(128) DEFAULT NULL;

-- 2. Create document_chunks table
CREATE TABLE IF NOT EXISTS document_chunks (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    document_id     BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    content         LONGTEXT     NOT NULL,
    page_start      INT          DEFAULT NULL,
    page_end        INT          DEFAULT NULL,
    section_title   VARCHAR(512) DEFAULT NULL,
    token_count     INT          NOT NULL,
    content_hash    VARCHAR(64)  DEFAULT NULL,
    index_version   VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128) DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_chunk_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_doc_idx_version
        UNIQUE (document_id, chunk_index, index_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Indexes for retrieval performance
CREATE INDEX idx_chunk_document_id
    ON document_chunks (document_id);

-- 4. FULLTEXT index for hybrid keyword search
--    NOTE: MySQL FULLTEXT cannot be expressed via JPA @Index annotation.
--    This is the authoritative creation point.
CREATE FULLTEXT INDEX ft_chunk_content 
    ON document_chunks (content);
