-- V7__tutor_session_foundation.sql
CREATE TABLE IF NOT EXISTS tutor_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    current_concept_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversation_id UNIQUE (conversation_id),
    CONSTRAINT fk_tutor_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tutor_concept
        FOREIGN KEY (current_concept_id) REFERENCES concepts (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
