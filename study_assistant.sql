CREATE DATABASE IF NOT EXISTS study_assistant
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE study_assistant;

-- =========================
-- 1. USERS
-- =========================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'STUDENT',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

-- =========================
-- 2. DOCUMENTS
-- =========================
CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(30),
    file_size BIGINT,
    total_pages INT,
    status VARCHAR(30) DEFAULT 'UPLOADED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documents_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =========================
-- 3. TOPICS
-- =========================
CREATE TABLE topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,

    -- Topic cha để tạo cây Knowledge Map
    parent_id BIGINT NULL,

    name VARCHAR(255) NOT NULL,
    description TEXT,

    -- Vị trí trong tài liệu
    page_start INT,
    page_end INT,

    -- Dùng để sắp xếp các node
    sort_order INT DEFAULT 0,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_topics_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_topics_parent
        FOREIGN KEY (parent_id)
        REFERENCES topics(id)
        ON DELETE CASCADE
);

-- =========================
-- 4. FLASHCARDS
-- =========================
CREATE TABLE flashcards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_flashcards_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id)
        ON DELETE CASCADE
);

-- =========================
-- 5. QUIZZES
-- =========================
CREATE TABLE quizzes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quizzes_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id)
        ON DELETE CASCADE
);

-- =========================
-- 6. QUIZ QUESTIONS
-- =========================
CREATE TABLE quiz_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    question TEXT NOT NULL,

    answer_a VARCHAR(500) NOT NULL,
    answer_b VARCHAR(500) NOT NULL,
    answer_c VARCHAR(500) NOT NULL,
    answer_d VARCHAR(500) NOT NULL,

    correct_answer CHAR(1) NOT NULL,

    explanation TEXT,

    CONSTRAINT fk_quiz_questions_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES quizzes(id)
        ON DELETE CASCADE
);

-- =========================
-- 7. QUIZ RESULTS
-- =========================
CREATE TABLE quiz_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,

    total_questions INT,
    correct_answers INT,
    score DOUBLE,

    completed_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quiz_results_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_quiz_results_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES quizzes(id)
        ON DELETE CASCADE
);

-- =========================
-- 8. STUDY PROGRESS
-- =========================
CREATE TABLE study_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,

    status VARCHAR(30) DEFAULT 'NOT_STARTED',
    progress DOUBLE DEFAULT 0,

    last_accessed_at DATETIME,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_study_progress_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_study_progress_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_user_topic_progress
        UNIQUE (user_id, topic_id)
);