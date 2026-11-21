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
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
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
    ai_summary LONGTEXT,
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
    topic_id BIGINT NULL,
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
    topic_id BIGINT NULL,
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

-- ============================================================
-- DU LIEU MAU CHO 9 BANG TRONG SCHEMA NEN
-- 12-15 records/bang; tong 120 records.
-- Tai khoan: demo_admin_01, demo_admin_02, demo_teacher_03,
-- demo_teacher_04, demo_student_05 ... demo_student_12.
-- Mat khau chung (chi dung demo): Demo123! ; luu BCrypt.
-- ID hoc tap dung dai 910001-980015; plan dung ID 1-12.
-- Chi nap mot lan vao database demo moi theo DDL o tren.
-- Khong dung lai truc tiep tren DB da co du lieu/da migration:
-- CREATE TABLE IF NOT EXISTS khong cap nhat schema cu hay schema moi.
-- Khong co TRUNCATE/DELETE va khong tat kiem tra khoa ngoai.
-- File tai lieu chi la duong dan minh hoa, chua co file vat ly,
-- embedding hay active index. UPLOADED khong phai READY cho RAG.
-- 9 goi DEMO_* tat is_active de khong hien ban/checkout.
-- ============================================================
SET NAMES utf8mb4;
START TRANSACTION;

-- users: 12 records
INSERT INTO users (id, full_name, username, email, password, role, created_at) VALUES
(910001, 'Nguyễn Minh Quân', 'demo_admin_01', 'demo_admin_01@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'ADMIN', '2026-10-01 08:00:00'),
(910002, 'Trần Thu Hà', 'demo_admin_02', 'demo_admin_02@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'ADMIN', '2026-10-01 08:00:00'),
(910003, 'Lê Hoàng Nam', 'demo_teacher_03', 'demo_teacher_03@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910004, 'Phạm Ngọc Anh', 'demo_teacher_04', 'demo_teacher_04@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910005, 'Hoàng Đức Minh', 'demo_student_05', 'demo_student_05@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910006, 'Vũ Thảo Linh', 'demo_student_06', 'demo_student_06@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910007, 'Đặng Hải Đăng', 'demo_student_07', 'demo_student_07@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910008, 'Bùi Phương Thảo', 'demo_student_08', 'demo_student_08@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910009, 'Đỗ Gia Huy', 'demo_student_09', 'demo_student_09@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910010, 'Ngô Khánh Vy', 'demo_student_10', 'demo_student_10@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910011, 'Dương Tuấn Kiệt', 'demo_student_11', 'demo_student_11@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00'),
(910012, 'Đinh Bảo Ngọc', 'demo_student_12', 'demo_student_12@example.com', '$2a$10$Cc.Fg4L3kKtDHzpn8JMRoeeflvi93LIwNlO9W5D/QI5VBZvcHLFg2', 'USER', '2026-10-01 08:00:00');

-- documents: 12 records
INSERT INTO documents (id, user_id, file_name, original_name, file_path, file_type, file_size, total_pages, status, ai_summary, created_at) VALUES
(920001, 910005, 'Trí tuệ nhân tạo cơ bản', 'demo_01.pdf', 'storage_private/demo/demo_01.pdf', 'PDF', 108192, 11, 'UPLOADED', 'Trí tuệ nhân tạo là lĩnh vực nghiên cứu xây dựng hệ thống thực hiện tác vụ cần năng lực trí tuệ.', '2026-10-01 09:00:00'),
(920002, 910006, 'Tìm kiếm trên đồ thị', 'demo_02.docx', 'storage_private/demo/demo_02.docx', 'DOCX', 116384, 12, 'UPLOADED', 'BFS mở rộng các đỉnh theo từng mức và có thể dùng hàng đợi FIFO.', '2026-10-02 09:00:00'),
(920003, 910007, 'Tìm kiếm heuristic A sao', 'demo_03.pptx', 'storage_private/demo/demo_03.pptx', 'PPTX', 124576, 13, 'UPLOADED', 'Thuật toán A* đánh giá nút bằng f(n) = g(n) + h(n).', '2026-10-03 09:00:00'),
(920004, 910008, 'Học máy có giám sát', 'demo_04.pdf', 'storage_private/demo/demo_04.pdf', 'PDF', 132768, 14, 'UPLOADED', 'Học có giám sát sử dụng dữ liệu huấn luyện có nhãn.', '2026-10-04 09:00:00'),
(920005, 910009, 'Hồi quy tuyến tính', 'demo_05.docx', 'storage_private/demo/demo_05.docx', 'DOCX', 140960, 15, 'UPLOADED', 'Hồi quy tuyến tính mô hình hóa quan hệ tuyến tính để dự đoán giá trị số.', '2026-10-05 09:00:00'),
(920006, 910010, 'Phân loại và đánh giá', 'demo_06.pptx', 'storage_private/demo/demo_06.pptx', 'PPTX', 149152, 16, 'UPLOADED', 'Precision là tỷ lệ TP trên tổng TP và FP.', '2026-10-06 09:00:00'),
(920007, 910011, 'Cây quyết định', 'demo_07.pdf', 'storage_private/demo/demo_07.pdf', 'PDF', 157344, 17, 'UPLOADED', 'Cây quyết định phân chia dữ liệu bằng các điều kiện trên thuộc tính.', '2026-10-07 09:00:00'),
(920008, 910012, 'Phân cụm K means', 'demo_08.docx', 'storage_private/demo/demo_08.docx', 'DOCX', 165536, 18, 'UPLOADED', 'K-means chia dữ liệu thành K cụm và cập nhật tâm cụm.', '2026-10-08 09:00:00'),
(920009, 910005, 'Mạng nơ ron nhân tạo', 'demo_09.pptx', 'storage_private/demo/demo_09.pptx', 'PPTX', 173728, 19, 'UPLOADED', 'Hàm kích hoạt phi tuyến giúp mạng nơ-ron biểu diễn quan hệ phức tạp.', '2026-10-09 09:00:00'),
(920010, 910006, 'Xử lý ngôn ngữ tự nhiên', 'demo_10.pdf', 'storage_private/demo/demo_10.pdf', 'PDF', 181920, 20, 'UPLOADED', 'Tokenization chia văn bản thành các đơn vị token trước các bước xử lý tiếp theo.', '2026-10-10 09:00:00'),
(920011, 910007, 'Embedding và truy xuất RAG', 'demo_11.docx', 'storage_private/demo/demo_11.docx', 'DOCX', 190112, 21, 'UPLOADED', 'RAG truy xuất bằng chứng liên quan trước khi sinh câu trả lời bằng mô hình ngôn ngữ.', '2026-10-11 09:00:00'),
(920012, 910008, 'Ôn tập giãn cách FSRS', 'demo_12.pptx', 'storage_private/demo/demo_12.pptx', 'PPTX', 198304, 22, 'UPLOADED', 'Ôn tập giãn cách lên lịch nhắc lại nội dung theo thời gian để hỗ trợ ghi nhớ.', '2026-10-12 09:00:00');

-- topics: 15 records
INSERT INTO topics (id, document_id, parent_id, name, description, page_start, page_end, sort_order) VALUES
(930001, 920001, NULL, 'Trí tuệ nhân tạo cơ bản', 'Trí tuệ nhân tạo là lĩnh vực nghiên cứu xây dựng hệ thống thực hiện tác vụ cần năng lực trí tuệ.', 1, 3, 1),
(930002, 920002, NULL, 'Tìm kiếm trên đồ thị', 'BFS mở rộng các đỉnh theo từng mức và có thể dùng hàng đợi FIFO.', 1, 3, 1),
(930003, 920003, NULL, 'Tìm kiếm heuristic A sao', 'Thuật toán A* đánh giá nút bằng f(n) = g(n) + h(n).', 1, 3, 1),
(930004, 920004, NULL, 'Học máy có giám sát', 'Học có giám sát sử dụng dữ liệu huấn luyện có nhãn.', 1, 3, 1),
(930005, 920005, NULL, 'Hồi quy tuyến tính', 'Hồi quy tuyến tính mô hình hóa quan hệ tuyến tính để dự đoán giá trị số.', 1, 3, 1),
(930006, 920006, NULL, 'Phân loại và đánh giá', 'Precision là tỷ lệ TP trên tổng TP và FP.', 1, 3, 1),
(930007, 920007, NULL, 'Cây quyết định', 'Cây quyết định phân chia dữ liệu bằng các điều kiện trên thuộc tính.', 1, 3, 1),
(930008, 920008, NULL, 'Phân cụm K means', 'K-means chia dữ liệu thành K cụm và cập nhật tâm cụm.', 1, 3, 1),
(930009, 920009, NULL, 'Mạng nơ ron nhân tạo', 'Hàm kích hoạt phi tuyến giúp mạng nơ-ron biểu diễn quan hệ phức tạp.', 1, 3, 1),
(930010, 920010, NULL, 'Xử lý ngôn ngữ tự nhiên', 'Tokenization chia văn bản thành các đơn vị token trước các bước xử lý tiếp theo.', 1, 3, 1),
(930011, 920011, NULL, 'Embedding và truy xuất RAG', 'RAG truy xuất bằng chứng liên quan trước khi sinh câu trả lời bằng mô hình ngôn ngữ.', 1, 3, 1),
(930012, 920012, NULL, 'Ôn tập giãn cách FSRS', 'Ôn tập giãn cách lên lịch nhắc lại nội dung theo thời gian để hỗ trợ ghi nhớ.', 1, 3, 1),
(930013, 920001, 930001, 'Ứng dụng của AI', 'Ví dụ ứng dụng AI trong hỗ trợ học tập.', 4, 5, 2),
(930014, 920002, 930002, 'Độ phức tạp BFS', 'Phân tích độ phức tạp BFS trên đồ thị.', 4, 5, 2),
(930015, 920003, 930003, 'Heuristic trong A*', 'Vai trò ước lượng chi phí còn lại của h(n).', 4, 5, 2);

-- flashcards: 15 records
INSERT INTO flashcards (id, topic_id, question, answer) VALUES
(940001, 930001, 'AI là viết tắt của thuật ngữ nào?', 'Artificial Intelligence'),
(940002, 930002, 'Cấu trúc dữ liệu thường dùng cho BFS là gì?', 'Hàng đợi FIFO'),
(940003, 930003, 'Hàm đánh giá của A* là gì?', 'g(n) + h(n)'),
(940004, 930004, 'Đặc điểm của dữ liệu học có giám sát là gì?', 'Có nhãn mục tiêu'),
(940005, 930005, 'Hồi quy tuyến tính thường dự đoán loại đầu ra nào?', 'Giá trị số liên tục'),
(940006, 930006, 'Công thức precision là gì?', 'TP / (TP + FP)'),
(940007, 930007, 'Nút trong cây quyết định thường biểu diễn điều gì?', 'Điều kiện kiểm tra thuộc tính'),
(940008, 930008, 'K trong K-means biểu thị đại lượng nào?', 'Số cụm'),
(940009, 930009, 'Hàm nào thường là hàm kích hoạt phi tuyến?', 'ReLU'),
(940010, 930010, 'Tokenization thực hiện tác vụ nào?', 'Chia văn bản thành token'),
(940011, 930011, 'Vai trò của retrieval trong RAG là gì?', 'Tìm bằng chứng liên quan'),
(940012, 930012, 'Mục đích của lịch ôn tập giãn cách là gì?', 'Hỗ trợ ghi nhớ lâu dài'),
(940013, 930013, 'Nêu một ứng dụng AI trong học tập.', 'Tóm tắt tài liệu và hỏi đáp có nguồn.'),
(940014, 930014, 'Độ phức tạp BFS với danh sách kề là gì?', 'O(V + E), với V là số đỉnh và E là số cạnh.'),
(940015, 930015, 'h(n) trong A* biểu diễn điều gì?', 'Ước lượng chi phí từ nút n đến đích.');

-- quizzes: 12 records
INSERT INTO quizzes (id, topic_id, title, description, created_at) VALUES
(950001, 930001, 'Ôn tập Trí tuệ nhân tạo cơ bản', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-01 10:00:00'),
(950002, 930002, 'Ôn tập Tìm kiếm trên đồ thị', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-02 10:00:00'),
(950003, 930003, 'Ôn tập Tìm kiếm heuristic A sao', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-03 10:00:00'),
(950004, 930004, 'Ôn tập Học máy có giám sát', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-04 10:00:00'),
(950005, 930005, 'Ôn tập Hồi quy tuyến tính', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-05 10:00:00'),
(950006, 930006, 'Ôn tập Phân loại và đánh giá', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-06 10:00:00'),
(950007, 930007, 'Ôn tập Cây quyết định', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-07 10:00:00'),
(950008, 930008, 'Ôn tập Phân cụm K means', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-08 10:00:00'),
(950009, 930009, 'Ôn tập Mạng nơ ron nhân tạo', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-09 10:00:00'),
(950010, 930010, 'Ôn tập Xử lý ngôn ngữ tự nhiên', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-10 10:00:00'),
(950011, 930011, 'Ôn tập Embedding và truy xuất RAG', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-11 10:00:00'),
(950012, 930012, 'Ôn tập Ôn tập giãn cách FSRS', 'Bài kiểm tra dữ liệu mẫu; không phải nội dung do AI sinh trực tiếp.', '2026-10-12 10:00:00');

-- quiz_questions: 15 records
INSERT INTO quiz_questions (id, quiz_id, question, answer_a, answer_b, answer_c, answer_d, correct_answer, explanation) VALUES
(960001, 950001, 'AI là viết tắt của thuật ngữ nào?', 'Artificial Intelligence', 'Automatic Internet', 'Advanced Interface', 'Applied Information', 'A', 'Trí tuệ nhân tạo là lĩnh vực nghiên cứu xây dựng hệ thống thực hiện tác vụ cần năng lực trí tuệ.'),
(960002, 950002, 'Cấu trúc dữ liệu thường dùng cho BFS là gì?', 'Ngăn xếp', 'Hàng đợi FIFO', 'Cây nhị phân', 'Bảng băm', 'B', 'BFS mở rộng các đỉnh theo từng mức và có thể dùng hàng đợi FIFO.'),
(960003, 950003, 'Hàm đánh giá của A* là gì?', 'g(n) - h(n)', 'g(n) nhân h(n)', 'g(n) + h(n)', 'h(n) chia g(n)', 'C', 'Thuật toán A* đánh giá nút bằng f(n) = g(n) + h(n).'),
(960004, 950004, 'Đặc điểm của dữ liệu học có giám sát là gì?', 'Luôn không có nhãn', 'Không có đầu vào', 'Chỉ có âm thanh', 'Có nhãn mục tiêu', 'D', 'Học có giám sát sử dụng dữ liệu huấn luyện có nhãn.'),
(960005, 950005, 'Hồi quy tuyến tính thường dự đoán loại đầu ra nào?', 'Giá trị số liên tục', 'Chỉ nhãn ảnh', 'Chỉ văn bản', 'Chỉ giá trị Boolean', 'A', 'Hồi quy tuyến tính mô hình hóa quan hệ tuyến tính để dự đoán giá trị số.'),
(960006, 950006, 'Công thức precision là gì?', 'TP / (TP + FN)', 'TP / (TP + FP)', 'TN / (TN + FN)', 'FP / (TP + FP)', 'B', 'Precision là tỷ lệ TP trên tổng TP và FP.'),
(960007, 950007, 'Nút trong cây quyết định thường biểu diễn điều gì?', 'Tệp dữ liệu', 'Mật khẩu', 'Điều kiện kiểm tra thuộc tính', 'Vector ngẫu nhiên', 'C', 'Cây quyết định phân chia dữ liệu bằng các điều kiện trên thuộc tính.'),
(960008, 950008, 'K trong K-means biểu thị đại lượng nào?', 'Số nhãn đúng', 'Số tầng mạng', 'Số mẫu kiểm thử', 'Số cụm', 'D', 'K-means chia dữ liệu thành K cụm và cập nhật tâm cụm.'),
(960009, 950009, 'Hàm nào thường là hàm kích hoạt phi tuyến?', 'ReLU', 'Phép cộng hằng số', 'Hàm đồng nhất', 'Phép nhân với một hằng số', 'A', 'Hàm kích hoạt phi tuyến giúp mạng nơ-ron biểu diễn quan hệ phức tạp.'),
(960010, 950010, 'Tokenization thực hiện tác vụ nào?', 'Mã hóa mật khẩu', 'Chia văn bản thành token', 'Xóa cơ sở dữ liệu', 'Vẽ biểu đồ', 'B', 'Tokenization chia văn bản thành các đơn vị token trước các bước xử lý tiếp theo.'),
(960011, 950011, 'Vai trò của retrieval trong RAG là gì?', 'Thay thế mọi dữ liệu', 'Tự huấn luyện mọi mô hình', 'Tìm bằng chứng liên quan', 'Mã hóa phiên đăng nhập', 'C', 'RAG truy xuất bằng chứng liên quan trước khi sinh câu trả lời bằng mô hình ngôn ngữ.'),
(960012, 950012, 'Mục đích của lịch ôn tập giãn cách là gì?', 'Tăng dung lượng file', 'Xóa lịch sử học', 'Tăng số tài khoản', 'Hỗ trợ ghi nhớ lâu dài', 'D', 'Ôn tập giãn cách lên lịch nhắc lại nội dung theo thời gian để hỗ trợ ghi nhớ.'),
(960013, 950001, 'Ứng dụng nào phù hợp với trợ lý học tập AI?', 'Xóa toàn bộ tài liệu', 'Hỏi đáp dựa trên tài liệu', 'Chỉ đổi màu màn hình', 'Tắt máy chủ', 'B', 'Hỏi đáp có nguồn là một ứng dụng AI hỗ trợ học tập.'),
(960014, 950002, 'BFS ưu tiên mở rộng nút theo cách nào?', 'Theo từng mức độ sâu', 'Luôn chọn nút cuối cùng', 'Luôn chọn nút đắt nhất', 'Chọn ngẫu nhiên', 'A', 'Hàng đợi FIFO giúp BFS duyệt theo từng mức.'),
(960015, 950003, 'g(n) trong A* biểu diễn điều gì?', 'Số lớp mạng', 'Số cụm', 'Chi phí đã đi từ điểm bắt đầu đến n', 'Kích thước tệp', 'C', 'A* kết hợp chi phí đã đi g(n) với ước lượng còn lại h(n).');

-- quiz_results: 12 records
INSERT INTO quiz_results (id, user_id, quiz_id, total_questions, correct_answers, score, completed_at) VALUES
(970001, 910005, 950001, 2, 2, 100.0, '2026-10-02 11:00:00'),
(970002, 910006, 950002, 2, 2, 100.0, '2026-10-03 11:00:00'),
(970003, 910007, 950003, 2, 1, 50.0, '2026-10-04 11:00:00'),
(970004, 910008, 950004, 1, 1, 100.0, '2026-10-05 11:00:00'),
(970005, 910009, 950005, 1, 1, 100.0, '2026-10-06 11:00:00'),
(970006, 910010, 950006, 1, 0, 0.0, '2026-10-07 11:00:00'),
(970007, 910011, 950007, 1, 1, 100.0, '2026-10-08 11:00:00'),
(970008, 910012, 950008, 1, 1, 100.0, '2026-10-09 11:00:00'),
(970009, 910005, 950009, 1, 0, 0.0, '2026-10-10 11:00:00'),
(970010, 910006, 950010, 1, 1, 100.0, '2026-10-11 11:00:00'),
(970011, 910007, 950011, 1, 1, 100.0, '2026-10-12 11:00:00'),
(970012, 910008, 950012, 1, 0, 0.0, '2026-10-13 11:00:00');

-- study_progress: 15 records
INSERT INTO study_progress (id, user_id, topic_id, status, progress, last_accessed_at) VALUES
(980001, 910005, 930001, 'NOT_STARTED', 0, NULL),
(980002, 910006, 930002, 'LEARNING', 25, '2026-10-03 12:00:00'),
(980003, 910007, 930003, 'LEARNING', 50, '2026-10-04 12:00:00'),
(980004, 910008, 930004, 'LEARNING', 75, '2026-10-05 12:00:00'),
(980005, 910009, 930005, 'COMPLETED', 100, '2026-10-06 12:00:00'),
(980006, 910010, 930006, 'NOT_STARTED', 0, NULL),
(980007, 910011, 930007, 'LEARNING', 25, '2026-10-08 12:00:00'),
(980008, 910012, 930008, 'LEARNING', 50, '2026-10-09 12:00:00'),
(980009, 910005, 930009, 'LEARNING', 75, '2026-10-10 12:00:00'),
(980010, 910006, 930010, 'COMPLETED', 100, '2026-10-11 12:00:00'),
(980011, 910007, 930011, 'NOT_STARTED', 0, NULL),
(980012, 910008, 930012, 'LEARNING', 25, '2026-10-13 12:00:00'),
(980013, 910005, 930013, 'LEARNING', 50, '2026-10-14 12:00:00'),
(980014, 910006, 930014, 'LEARNING', 75, '2026-10-15 12:00:00'),
(980015, 910007, 930015, 'COMPLETED', 100, '2026-10-16 12:00:00');

-- pricing_plans: 12 records
INSERT INTO pricing_plans (id, plan_code, name, original_price, sale_price, billing_cycle, is_popular, is_active, badge_text, description, features, document_limit) VALUES
(1, 'FREE', 'Gói Miễn Phí (FREE)', 0, 0, 'vĩnh viễn', FALSE, TRUE, NULL, 'Phù hợp cho trải nghiệm ban đầu', 'Tải lên tối đa 3 tài liệu|Sơ đồ kiến thức cơ bản|10 Bài trắc nghiệm AI mỗi tháng', 3),
(2, 'STUDENT', 'Gói Sinh Viên (STUDENT)', 79000, 49000, 'tháng', FALSE, TRUE, 'Giảm 38%', 'Dành cho học sinh sinh viên học tập hàng ngày', 'Tải lên tối đa 50 tài liệu|Sơ đồ kiến thức 3D nâng cao|50 Bài trắc nghiệm AI/tháng|Thẻ Flashcards lật 3D không giới hạn', 50),
(3, 'PRO', 'Gói Chuyên Nghiệp (PRO)', 149000, 99000, 'tháng', TRUE, TRUE, 'Phổ Biến Nhất - GIẢM 33%', 'Dành cho người học tích cực & nghiên cứu chuyên sâu', 'Tải lên không giới hạn tài liệu|Sơ đồ kiến thức 3D không giới hạn|Không giới hạn Thẻ Flashcards & Bài thi AI|Phân tích chuyên sâu tiến độ học tập|Ưu tiên xử lý từ AI Model', 9999),
(4, 'DEMO_BASIC', 'Gói Cơ Bản (minh họa)', 39000, 39000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 10),
(5, 'DEMO_WEEK', 'Gói Một Tuần (minh họa)', 19000, 19000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 5),
(6, 'DEMO_SEMESTER', 'Gói Học Kỳ (minh họa)', 249000, 249000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 100),
(7, 'DEMO_YEAR', 'Gói Một Năm (minh họa)', 499000, 499000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 200),
(8, 'DEMO_TEACHER', 'Gói Giảng Viên (minh họa)', 199000, 199000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 150),
(9, 'DEMO_RESEARCH', 'Gói Nghiên Cứu (minh họa)', 299000, 299000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 300),
(10, 'DEMO_GROUP', 'Gói Nhóm Học (minh họa)', 349000, 349000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 400),
(11, 'DEMO_SCHOOL', 'Gói Trường Học (minh họa)', 999000, 999000, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 1000),
(12, 'DEMO_TRIAL', 'Gói Trải Nghiệm (minh họa)', 0, 0, 'minh họa', FALSE, FALSE, 'Dữ liệu mẫu', 'Không kích hoạt bán; chỉ dùng minh họa quản lý bảng giá.', 'Dữ liệu minh họa|Không cam kết quota hoặc tính năng thực tế', 2);

COMMIT;

-- Kiem tra so ban ghi trong dai du lieu mau:
SELECT 'users' AS table_name, COUNT(*) AS sample_records FROM users WHERE id BETWEEN 910001 AND 910012
UNION ALL SELECT 'documents' AS table_name, COUNT(*) AS sample_records FROM documents WHERE id BETWEEN 920001 AND 920012
UNION ALL SELECT 'topics' AS table_name, COUNT(*) AS sample_records FROM topics WHERE id BETWEEN 930001 AND 930015
UNION ALL SELECT 'flashcards' AS table_name, COUNT(*) AS sample_records FROM flashcards WHERE id BETWEEN 940001 AND 940015
UNION ALL SELECT 'quizzes' AS table_name, COUNT(*) AS sample_records FROM quizzes WHERE id BETWEEN 950001 AND 950012
UNION ALL SELECT 'quiz_questions' AS table_name, COUNT(*) AS sample_records FROM quiz_questions WHERE id BETWEEN 960001 AND 960015
UNION ALL SELECT 'quiz_results' AS table_name, COUNT(*) AS sample_records FROM quiz_results WHERE id BETWEEN 970001 AND 970012
UNION ALL SELECT 'study_progress' AS table_name, COUNT(*) AS sample_records FROM study_progress WHERE id BETWEEN 980001 AND 980015
UNION ALL SELECT 'pricing_plans' AS table_name, COUNT(*) AS sample_records FROM pricing_plans WHERE id BETWEEN 1 AND 12;
