-- ============================================================
-- V9__exam_intelligence_foundation.sql
-- EI1.2D Final Schema Freeze Implementation
-- ============================================================

-- 1. ALTER quizzes
ALTER TABLE quizzes
    ADD COLUMN notebook_id BIGINT NULL DEFAULT NULL,
    ADD COLUMN scope_type VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN scope_concept_id BIGINT NULL DEFAULT NULL,
    ADD COLUMN mode VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN status VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN time_limit_seconds INT NULL DEFAULT NULL,
    ADD COLUMN pass_threshold DECIMAL(5,2) NULL DEFAULT NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL,
    ADD CONSTRAINT fk_quiz_notebook FOREIGN KEY (notebook_id) REFERENCES notebooks(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_quiz_scope_concept FOREIGN KEY (scope_concept_id) REFERENCES concepts(id) ON DELETE RESTRICT;

CREATE INDEX idx_quizzes_notebook_scope_status ON quizzes (notebook_id, scope_type, status);

-- 2. ALTER quiz_questions
ALTER TABLE quiz_questions
    MODIFY answer_a VARCHAR(255) NULL DEFAULT NULL,
    MODIFY answer_b VARCHAR(255) NULL DEFAULT NULL,
    MODIFY answer_c VARCHAR(255) NULL DEFAULT NULL,
    MODIFY answer_d VARCHAR(255) NULL DEFAULT NULL,
    MODIFY correct_answer VARCHAR(255) NULL DEFAULT NULL,
    ADD COLUMN question_type VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN content_hash CHAR(64) NULL DEFAULT NULL,
    ADD COLUMN question_order INT NULL DEFAULT NULL,
    ADD COLUMN points DECIMAL(5,2) NULL DEFAULT NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL,
    ADD CONSTRAINT uk_quiz_questions_quiz_order UNIQUE (quiz_id, question_order);

CREATE INDEX idx_quiz_questions_content_hash ON quiz_questions (content_hash);

-- 3. ALTER quiz_attempts
ALTER TABLE quiz_attempts
    ADD COLUMN submission_token VARCHAR(36) NULL DEFAULT NULL,
    ADD COLUMN submission_hash CHAR(64) NULL DEFAULT NULL,
    ADD COLUMN scoring_version VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN mode_snapshot VARCHAR(50) NULL DEFAULT NULL,
    ADD COLUMN time_limit_seconds_snapshot INT NULL DEFAULT NULL,
    ADD COLUMN pass_threshold_snapshot DECIMAL(5,2) NULL DEFAULT NULL,
    ADD COLUMN mastery_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT uk_quiz_attempts_user_token UNIQUE (user_id, submission_token);

CREATE INDEX idx_quiz_attempts_user_quiz_status_completed ON quiz_attempts (user_id, quiz_id, status, completed_at);
CREATE INDEX idx_quiz_attempts_user_mode_status_completed ON quiz_attempts (user_id, mode_snapshot, status, completed_at);

UPDATE quiz_attempts SET mastery_applied = TRUE WHERE status = 'COMPLETED';

-- 4. CREATE NEW TABLES
CREATE TABLE quiz_question_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_order INT NOT NULL,
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qq_options_order UNIQUE (question_id, option_order),
    CONSTRAINT fk_qq_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_question_accepted_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    accepted_order INT NOT NULL,
    accepted_text TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qq_accepted_answers_order UNIQUE (question_id, accepted_order),
    CONSTRAINT fk_qq_accepted_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_question_concepts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    concept_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qq_concepts_concept UNIQUE (question_id, concept_id),
    CONSTRAINT fk_qq_concepts_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_qq_concepts_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_quiz_question_concepts_concept ON quiz_question_concepts (concept_id);

CREATE TABLE quiz_question_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    document_chunk_id BIGINT NOT NULL,
    source_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qq_sources_order UNIQUE (question_id, source_order),
    CONSTRAINT uk_qq_sources_chunk UNIQUE (question_id, document_chunk_id),
    CONSTRAINT fk_qq_sources_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_qq_sources_chunk FOREIGN KEY (document_chunk_id) REFERENCES document_chunks(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_quiz_question_sources_chunk ON quiz_question_sources (document_chunk_id);

CREATE TABLE quiz_attempt_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_order INT NOT NULL,
    question_text_snapshot TEXT NOT NULL,
    question_type_snapshot VARCHAR(50) NOT NULL,
    points_possible DECIMAL(5,2) NOT NULL,
    student_answer TEXT NULL DEFAULT NULL,
    points_earned DECIMAL(5,2) NULL DEFAULT NULL,
    is_correct BOOLEAN NULL DEFAULT NULL,
    explanation_snapshot TEXT NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    graded_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qa_answers_question UNIQUE (attempt_id, question_id),
    CONSTRAINT uk_qa_answers_order UNIQUE (attempt_id, question_order),
    CONSTRAINT fk_qa_answers_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_attempt_answer_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_answer_id BIGINT NOT NULL,
    option_order INT NOT NULL,
    option_text_snapshot TEXT NOT NULL,
    is_correct_snapshot BOOLEAN NOT NULL,
    student_selected BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qaa_options_order UNIQUE (attempt_answer_id, option_order),
    CONSTRAINT fk_qaa_options_answer FOREIGN KEY (attempt_answer_id) REFERENCES quiz_attempt_answers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_attempt_answer_accepted_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_answer_id BIGINT NOT NULL,
    accepted_order INT NOT NULL,
    accepted_text_snapshot TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qaa_accepted_answers_order UNIQUE (attempt_answer_id, accepted_order),
    CONSTRAINT fk_qaa_accepted_answers_answer FOREIGN KEY (attempt_answer_id) REFERENCES quiz_attempt_answers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_attempt_answer_concepts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_answer_id BIGINT NOT NULL,
    concept_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_qaa_concepts_concept UNIQUE (attempt_answer_id, concept_id),
    CONSTRAINT fk_qaa_concepts_answer FOREIGN KEY (attempt_answer_id) REFERENCES quiz_attempt_answers(id) ON DELETE CASCADE,
    CONSTRAINT fk_qaa_concepts_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_attempt_answer_concepts_concept ON quiz_attempt_answer_concepts (concept_id);
