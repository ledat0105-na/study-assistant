-- ============================================================
-- V4__student_knowledge_model_foundation.sql
-- SKM V1 Checkpoint: Data model foundation
--
-- Purpose: Additive schema changes for Student Knowledge Model.
-- No existing tables/columns are modified destructively.
-- ============================================================

-- 1. Create student_concept_mastery table
CREATE TABLE IF NOT EXISTS student_concept_mastery (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    user_id          BIGINT   NOT NULL,
    concept_id       BIGINT   NOT NULL,
    mastery_estimate DOUBLE   NOT NULL DEFAULT 0.0,
    evidence_count   INT      NOT NULL DEFAULT 0,
    last_evidence_at DATETIME DEFAULT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uk_mastery_user_concept UNIQUE (user_id, concept_id),
    CONSTRAINT fk_mastery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_mastery_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE CASCADE,
    CONSTRAINT chk_mastery_estimate_range CHECK (mastery_estimate >= 0.0 AND mastery_estimate <= 1.0),
    CONSTRAINT chk_mastery_counts CHECK (evidence_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create student_learning_events table
CREATE TABLE IF NOT EXISTS student_learning_events (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    concept_id       BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    normalized_score DOUBLE       NOT NULL,
    source_type      VARCHAR(50)  NOT NULL,
    source_id        VARCHAR(128) NOT NULL,
    occurred_at      DATETIME     NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uk_event_idempotency UNIQUE (user_id, idempotency_key, concept_id),
    CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE CASCADE,
    CONSTRAINT chk_event_score_range CHECK (normalized_score >= 0.0 AND normalized_score <= 1.0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Indexes for queries
-- idx_mastery_user is redundant due to uk_mastery_user_concept (user_id is left prefix).
CREATE INDEX idx_mastery_concept ON student_concept_mastery(concept_id);
CREATE INDEX idx_event_user_concept_time ON student_learning_events(user_id, concept_id, occurred_at);
