CREATE DATABASE IF NOT EXISTS study_assistant
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE study_assistant;

-- =========================
-- 1. USERS
-- =========================
CREATE TABLE IF NOT EXISTS users (
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
CREATE TABLE IF NOT EXISTS documents (
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
CREATE TABLE IF NOT EXISTS topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,

    parent_id BIGINT NULL,

    name VARCHAR(255) NOT NULL,
    description TEXT,

    page_start INT,
    page_end INT,

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
CREATE TABLE IF NOT EXISTS flashcards (
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
CREATE TABLE IF NOT EXISTS quizzes (
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
CREATE TABLE IF NOT EXISTS quiz_questions (
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
CREATE TABLE IF NOT EXISTS quiz_results (
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
CREATE TABLE IF NOT EXISTS study_progress (
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

-- =========================
-- 9. PRICING PLANS (Bảng Giá Dịch Vụ - SALE & CRUD)
-- =========================
CREATE TABLE IF NOT EXISTS pricing_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    original_price DOUBLE NOT NULL DEFAULT 0,
    sale_price DOUBLE NOT NULL DEFAULT 0,
    billing_cycle VARCHAR(50) DEFAULT 'tháng',
    is_popular BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    badge_text VARCHAR(100) DEFAULT NULL,
    description TEXT,
    features TEXT,
    document_limit INT DEFAULT 3,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Nạp dữ liệu mẫu khởi tạo bảng giá chuẩn UTF-8
TRUNCATE TABLE pricing_plans;

INSERT INTO pricing_plans (id, plan_code, name, original_price, sale_price, billing_cycle, is_popular, is_active, badge_text, description, features, document_limit) VALUES
(1, 'FREE', 'Gói Miễn Phí (FREE)', 0, 0, 'vĩnh viễn', FALSE, TRUE, NULL, 'Phù hợp cho trải nghiệm ban đầu', 'Tải lên tối đa 3 tài liệu|Sơ đồ kiến thức cơ bản|10 Bài trắc nghiệm AI mỗi tháng', 3),
(2, 'STUDENT', 'Gói Sinh Viên (STUDENT)', 79000, 49000, 'tháng', FALSE, TRUE, 'Giảm 38%', 'Dành cho học sinh sinh viên học tập hàng ngày', 'Tải lên tối đa 50 tài liệu|Sơ đồ kiến thức 3D nâng cao|50 Bài trắc nghiệm AI/tháng|Thẻ Flashcards lật 3D không giới hạn', 50),
(3, 'PRO', 'Gói Chuyên Nghiệp (PRO)', 149000, 99000, 'tháng', TRUE, TRUE, 'Phổ Biến Nhất - GIẢM 33%', 'Dành cho người học tích cực & nghiên cứu chuyên sâu', 'Tải lên không giới hạn tài liệu|Sơ đồ kiến thức 3D không giới hạn|Không giới hạn Thẻ Flashcards & Bài thi AI|Phân tích chuyên sâu tiến độ học tập|Ưu tiên xử lý từ AI Model', 9999);