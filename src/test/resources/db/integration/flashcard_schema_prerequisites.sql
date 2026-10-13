-- ============================================================
-- TEST-ONLY PREREQUISITE
-- Reconstructs the pre-V8 legacy `flashcards` table schema
-- because the test DB `studyassistant_rag_test` lacks it.
-- This does NOT contain V8 FSRS fields or tables.
-- ============================================================

CREATE TABLE IF NOT EXISTS flashcards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    topic_id BIGINT NULL,
    document_id BIGINT NULL,
    CONSTRAINT fk_flashcard_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_flashcard_document FOREIGN KEY (document_id) REFERENCES documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
