CREATE TABLE IF NOT EXISTS document_summaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    index_version VARCHAR(128) NOT NULL,
    document_content_hash VARCHAR(64) NULL,
    summary_text LONGTEXT NOT NULL,
    summary_json JSON NOT NULL,
    model_identifier VARCHAR(128) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_summary_version UNIQUE (document_id, index_version),
    CONSTRAINT fk_document_summaries_document FOREIGN KEY (document_id)
        REFERENCES documents(id) ON DELETE CASCADE,
    INDEX idx_document_summaries_version (document_id, index_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_summary_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    summary_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    source_alias VARCHAR(32) NOT NULL,
    source_order INT NOT NULL,
    page_start INT NULL,
    page_end INT NULL,
    section_title VARCHAR(512) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_summary_source_alias UNIQUE (summary_id, source_alias),
    CONSTRAINT uk_document_summary_source_order UNIQUE (summary_id, source_order),
    CONSTRAINT fk_document_summary_sources_summary FOREIGN KEY (summary_id)
        REFERENCES document_summaries(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_summary_sources_chunk FOREIGN KEY (document_chunk_id)
        REFERENCES document_chunks(id) ON DELETE RESTRICT,
    INDEX idx_document_summary_sources_chunk (document_chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
