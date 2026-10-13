-- ============================================================
-- V10__presentation_foundation.sql
-- T1.3 Presentation Foundation
-- Additive schema only. Apply manually to the intended integration DB.
-- ============================================================

CREATE TABLE IF NOT EXISTS presentation_decks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    notebook_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    aspect_ratio VARCHAR(8) NOT NULL DEFAULT '16:9',
    theme_key VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    approved_outline_revision_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_decks_id_owner UNIQUE (id, owner_id),
    CONSTRAINT fk_presentation_decks_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_decks_notebook FOREIGN KEY (notebook_id) REFERENCES notebooks(id) ON DELETE RESTRICT,
    INDEX idx_presentation_decks_owner_status_updated (owner_id, status, updated_at, id),
    INDEX idx_presentation_decks_notebook_status_updated (notebook_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_outline_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    generation_scope_snapshot JSON NOT NULL,
    outline_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_outline_deck_revision UNIQUE (deck_id, revision_number),
    CONSTRAINT uk_presentation_outline_deck_id UNIQUE (deck_id, id),
    CONSTRAINT fk_presentation_outline_deck FOREIGN KEY (deck_id) REFERENCES presentation_decks(id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_outline_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_presentation_outline_deck_status_revision (deck_id, status, revision_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_slides (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    slide_order INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    layout_type VARCHAR(32) NOT NULL,
    speaker_notes TEXT NULL,
    background_payload JSON NULL,
    generation_state VARCHAR(16) NOT NULL DEFAULT 'EMPTY',
    generated_from_outline_revision_id BIGINT NULL,
    generation_key VARCHAR(128) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_slides_deck_order UNIQUE (deck_id, slide_order),
    CONSTRAINT uk_presentation_slides_deck_id UNIQUE (deck_id, id),
    CONSTRAINT uk_presentation_slides_generation_key UNIQUE (deck_id, generated_from_outline_revision_id, generation_key),
    CONSTRAINT fk_presentation_slides_deck FOREIGN KEY (deck_id) REFERENCES presentation_decks(id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_slides_outline FOREIGN KEY (deck_id, generated_from_outline_revision_id)
        REFERENCES presentation_outline_revisions(deck_id, id) ON DELETE RESTRICT,
    INDEX idx_presentation_slides_deck_state_order (deck_id, generation_state, slide_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_media_assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    deck_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    creator VARCHAR(255) NULL,
    license_status VARCHAR(24) NOT NULL,
    source_url VARCHAR(2048) NULL,
    source_document_id BIGINT NULL,
    source_page INT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_media_storage_key UNIQUE (stored_file_key),
    CONSTRAINT uk_presentation_media_deck_id UNIQUE (deck_id, id),
    CONSTRAINT fk_presentation_media_deck_owner FOREIGN KEY (deck_id, owner_id)
        REFERENCES presentation_decks(id, owner_id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_media_source_document FOREIGN KEY (source_document_id)
        REFERENCES documents(id) ON DELETE RESTRICT,
    INDEX idx_presentation_media_owner_deck (owner_id, deck_id),
    INDEX idx_presentation_media_deck_source (deck_id, source_type),
    INDEX idx_presentation_media_source_document (source_document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_slide_elements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,
    element_type VARCHAR(16) NOT NULL,
    z_index INT NOT NULL,
    x DECIMAL(7,2) NOT NULL,
    y DECIMAL(7,2) NOT NULL,
    width DECIMAL(7,2) NOT NULL,
    height DECIMAL(7,2) NOT NULL,
    rotation DECIMAL(6,2) NOT NULL DEFAULT 0,
    payload_json JSON NOT NULL,
    style_json JSON NOT NULL,
    media_asset_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_presentation_elements_slide_deck FOREIGN KEY (deck_id, slide_id)
        REFERENCES presentation_slides(deck_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_elements_media_deck FOREIGN KEY (deck_id, media_asset_id)
        REFERENCES presentation_media_assets(deck_id, id) ON DELETE RESTRICT,
    INDEX idx_presentation_elements_slide_z (slide_id, z_index),
    INDEX idx_presentation_elements_deck_media (deck_id, media_asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_slide_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slide_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    source_order INT NOT NULL,
    evidence_excerpt TEXT NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    page_start INT NULL,
    page_end INT NULL,
    section_title VARCHAR(512) NULL,
    source_alias VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_slide_sources_order UNIQUE (slide_id, source_order),
    CONSTRAINT uk_presentation_slide_sources_chunk UNIQUE (slide_id, document_chunk_id),
    CONSTRAINT fk_presentation_slide_sources_slide FOREIGN KEY (slide_id) REFERENCES presentation_slides(id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_slide_sources_chunk FOREIGN KEY (document_chunk_id) REFERENCES document_chunks(id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_slide_sources_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE RESTRICT,
    INDEX idx_presentation_slide_sources_chunk (document_chunk_id),
    INDEX idx_presentation_slide_sources_document_page (document_id, page_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presentation_generation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    deck_id BIGINT NOT NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    outline_revision_id BIGINT NULL,
    target_slide_id BIGINT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    progress_current INT NOT NULL DEFAULT 0,
    progress_total INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(1000) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_presentation_jobs_idempotency UNIQUE (owner_id, deck_id, job_type, idempotency_key),
    CONSTRAINT fk_presentation_jobs_deck_owner FOREIGN KEY (deck_id, owner_id)
        REFERENCES presentation_decks(id, owner_id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_jobs_outline_deck FOREIGN KEY (deck_id, outline_revision_id)
        REFERENCES presentation_outline_revisions(deck_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_presentation_jobs_slide_deck FOREIGN KEY (deck_id, target_slide_id)
        REFERENCES presentation_slides(deck_id, id) ON DELETE RESTRICT,
    INDEX idx_presentation_jobs_deck_status_created (deck_id, status, created_at),
    INDEX idx_presentation_jobs_owner_status_created (owner_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add the cyclic deck -> approved outline reference after both sides exist.
ALTER TABLE presentation_decks
    ADD CONSTRAINT fk_presentation_decks_approved_outline
        FOREIGN KEY (id, approved_outline_revision_id)
        REFERENCES presentation_outline_revisions(deck_id, id) ON DELETE RESTRICT;

-- Presentation quota configuration is nullable until plans are configured.
ALTER TABLE plan_features
    ADD COLUMN max_active_presentation_decks INT NULL DEFAULT NULL,
    ADD COLUMN daily_presentation_outline_limit INT NULL DEFAULT NULL,
    ADD COLUMN daily_presentation_slide_limit INT NULL DEFAULT NULL,
    ADD COLUMN max_presentation_media_storage_bytes BIGINT NULL DEFAULT NULL;

ALTER TABLE user_daily_usage
    ADD COLUMN presentation_outline_count INT NOT NULL DEFAULT 0,
    ADD COLUMN presentation_slide_count INT NOT NULL DEFAULT 0;
