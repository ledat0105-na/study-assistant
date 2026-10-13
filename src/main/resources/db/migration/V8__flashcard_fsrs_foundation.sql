-- V8 Flashcard FSRS Foundation

ALTER TABLE flashcards
    ADD COLUMN concept_id BIGINT NULL,
    ADD COLUMN content_hash VARCHAR(64) NULL,
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL;

UPDATE flashcards SET created_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;

ALTER TABLE flashcards
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE flashcards
    ADD CONSTRAINT fk_flashcards_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE SET NULL;

CREATE TABLE flashcard_review_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    flashcard_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    step INT NULL,
    difficulty DOUBLE NOT NULL,
    stability DOUBLE NOT NULL,
    due_at DATETIME(6) NOT NULL,
    last_review_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_flashcard_review_state_user_card UNIQUE (user_id, flashcard_id),
    CONSTRAINT fk_fc_rev_state_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fc_rev_state_card FOREIGN KEY (flashcard_id) REFERENCES flashcards(id) ON DELETE RESTRICT,
    INDEX idx_flashcard_review_state_due (user_id, due_at, flashcard_id)
);

CREATE TABLE flashcard_review_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    flashcard_id BIGINT NOT NULL,
    review_token VARCHAR(36) NOT NULL,
    rating VARCHAR(10) NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    state_before VARCHAR(20) NULL,
    state_after VARCHAR(20) NOT NULL,
    step_before INT NULL,
    step_after INT NULL,
    difficulty_before DOUBLE NULL,
    difficulty_after DOUBLE NOT NULL,
    stability_before DOUBLE NULL,
    stability_after DOUBLE NOT NULL,
    due_before DATETIME(6) NULL,
    due_after DATETIME(6) NOT NULL,
    duration_ms INT NULL,
    algorithm_version VARCHAR(20) NOT NULL,
    scheduler_version VARCHAR(20) NOT NULL,
    scheduler_config_version VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_flashcard_review_event_user_token UNIQUE (user_id, review_token),
    CONSTRAINT fk_fc_rev_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fc_rev_event_card FOREIGN KEY (flashcard_id) REFERENCES flashcards(id) ON DELETE RESTRICT,
    INDEX idx_flashcard_review_event_card_time (user_id, flashcard_id, reviewed_at)
);

CREATE TABLE flashcard_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flashcard_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    source_order INT NOT NULL,
    CONSTRAINT uq_flashcard_source_chunk UNIQUE (flashcard_id, document_chunk_id),
    CONSTRAINT uq_flashcard_source_order UNIQUE (flashcard_id, source_order),
    CONSTRAINT fk_fc_source_card FOREIGN KEY (flashcard_id) REFERENCES flashcards(id) ON DELETE CASCADE,
    CONSTRAINT fk_fc_source_chunk FOREIGN KEY (document_chunk_id) REFERENCES document_chunks(id) ON DELETE RESTRICT
);
