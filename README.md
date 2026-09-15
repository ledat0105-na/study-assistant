# 🎓 AI Study Assistant — Trợ Lý Học Tập Thông Minh 🚀

**AI Study Assistant** là hệ thống trợ lý học tập cá nhân hóa được xây dựng trên nền tảng **Spring Boot (Java 17)** và giao diện web hiện đại. Hệ thống tích hợp Trí tuệ Nhân tạo (AI) giúp tự động hóa quá trình tổng hợp tài liệu, tạo sơ đồ tư duy (Mindmap), tự động sinh bộ câu hỏi trắc nghiệm (Quiz), thẻ ghi nhớ (Flashcards 3D), trò chuyện với AI và quản lý tiến độ học tập.

---

## 🌟 Tính Năng Nổi Bật

### 👨‍🎓 Dành cho Học sinh / Sinh viên (Student)
- 📄 **Tải & Phân Tích Tài Liệu (AI Document Processing)**: Hỗ trợ upload tài liệu PDF, DOCX, TXT. AI tự động tóm tắt nội dung, rút ra các từ khóa và ý chính.
- 💬 **Trợ Lý Trò Chuyện AI (AI Study Chatbot)**: Hỏi đáp, giải thích bài tập, làm rõ khái niệm trực tiếp dựa trên nội dung tài liệu.
- 🧠 **Sơ Đồ Kiến Thức (Interactive Mindmap)**: Trực quan hóa kiến thức dưới dạng sơ đồ mạng (Graph/Tree) giúp ghi nhớ hệ thống.
- 🎴 **Thẻ Ghi Nhớ 3D (3D Flashcards)**: Tạo tự động từ tài liệu với hiệu ứng lật 3D mượt mà, hỗ trợ ghi nhớ nhanh.
- ✍️ **Luyện Trắc Nghiệm AI (AI Quiz & Testing)**: Tự động khởi tạo bộ câu hỏi trắc nghiệm theo mức độ khó, chấm điểm và xem giải thích chi tiết.
- 📊 **Theo Dõi Tiến Độ (Learning Progress & Analytics)**: Biểu đồ thống kê thời gian học, số bài test đã hoàn thành, điểm trung bình và streak học tập.
- 💎 **Quản Lý Gói Dịch Vụ (Pricing & Subscriptions)**: Đăng ký & nâng cấp gói dịch vụ (`FREE`, `STUDENT`, `PRO`) với giới hạn tính năng linh hoạt.

### 🛡️ Dành cho Quản trị viên (Admin Dashboard)
- 👥 **Quản lý Tài khoản (User Management)**: Phân quyền (`ADMIN`, `STUDENT`), thay đổi gói dịch vụ, khóa/mở khóa tài khoản.
- 🏷️ **Quản lý Bảng Giá & Khuyến Mãi (Pricing Plan Management)**: Thêm, sửa, ẩn/mở gói dịch vụ, cài đặt giảm giá và tính năng đi kèm.
- 💳 **Quản lý Giao Dịch & Thanh Toán (Payment Audit)**: Theo dõi lịch sử thanh toán nâng cấp gói.
- 📁 **Quản lý Tài Liệu Hệ Thống (Documents Audit)**: Xem danh sách và kiểm duyệt tất cả tài liệu được tải lên.
- 📜 **Nhật Ký Hệ Thống (Audit Logs)**: Ghi vết toàn bộ hành vi quan trọng của người dùng và hệ thống.

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

### Backend
- **Language**: Java 17 (LTS)
- **Framework**: Spring Boot 4.1.1 (Spring Web, Spring Data JPA, Spring Validation)
- **Security**: Spring Security Crypto (Mã hóa mật khẩu BCrypt)
- **Database**: MySQL 8.0+
- **Build Tool**: Gradle 8.x

### Frontend
- **HTML5 & CSS3**: Vanilla CSS với phong cách Glassmorphic modern design.
- **UI Framework**: Tailwind CSS (CDN).
- **Icons**: Lucide Icons.
- **Data Visualization**: Chart.js (Biểu đồ tiến độ) & Vis.js / D3.js (Sơ đồ tư duy Mindmap).

### AI Core & Integrations
- **AI Engine**: Integration với OpenAI API / Large Language Models (LLMs) cho tác vụ xử lý ngôn ngữ tự nhiên.

---

## 📁 Cấu Trúc Dự Án (Project Structure)

```text
study-assistant/
├── src/
│   ├── main/
│   │   ├── java/com/example/studyassistant/
│   │   │   ├── controller/      # REST API Controllers (Admin, Auth, Chat, Document, Quiz, Payment...)
│   │   │   ├── entity/          # JPA Entities (User, Document, Quiz, PricingPlan, AuditLog...)
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── service/         # Business Logic & Quota Services
│   │   │   └── util/            # Utility classes (BCrypt, HashUtil...)
│   │   └── resources/
│   │       ├── static/          # Giao diện Web (HTML, CSS, JS)
│   │       │   ├── index.html           # Trang chủ & Bảng giá
│   │       │   ├── upload.html          # Trang tải tài liệu & Phân tích AI
│   │       │   ├── chat.html            # Trang Chatbot AI
│   │       │   ├── quiz.html            # Trang Thi trắc nghiệm AI
│   │       │   ├── flashcards.html      # Trang Thẻ ghi nhớ 3D
│   │       │   ├── mindmap.html         # Trang Sơ đồ tư duy
│   │       │   ├── progress.html        # Trang Báo cáo tiến độ
│   │       │   ├── admin.html           # Trang Tổng quan Admin
│   │       │   ├── admin-users.html     # Trang Quản lý người dùng
│   │       │   └── admin-pricing.html   # Trang Quản lý gói cước
│   │       └── application.properties   # Cấu hình Spring Boot & Database
├── study_assistant.sql          # File khởi tạo CSDL MySQL mẫu
├── build.gradle                 # Cấu hình phụ thuộc & biên dịch Gradle
└── README.md                    # Tài liệu hướng dẫn dự án
```

---

## ⚙️ Hướng Dẫn Cài Đặt & Chạy Dự Án

### 1. Yêu Cầu Tiền Đề (Prerequisites)
- **Java Development Kit (JDK)**: phiên bản **17** trở lên.
- **MySQL Server**: phiên bản **8.0** trở lên.
- **Gradle**: (Đã bao gồm `gradlew` wrapper sẵn trong dự án).

### 2. Cấu Hình Cơ Sở Dữ Liệu MySQL
1. Mở MySQL Workbench hoặc MySQL CLI và tạo cơ sở dữ liệu mới:
   ```sql
   CREATE DATABASE study_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Import dữ liệu ban đầu từ file `study_assistant.sql`:
   ```bash
   mysql -u root -p study_assistant < study_assistant.sql
   ```

### 3. Cấu Hình Ứng Dụng (`application.properties`)
Mở file `src/main/resources/application.properties` và chỉnh sửa các thông số kết nối MySQL & API Key:
```properties
spring.application.name=study-assistant

# MySQL Connection
spring.datasource.url=jdbc:mysql://localhost:3306/study_assistant?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh
spring.datasource.username=root
spring.datasource.password=Tên_Mật_Khẩu_MySQL_Của_Bạn

# JPA Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Servlet Encoding UTF-8
server.port=8080
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.enabled=true
server.servlet.encoding.force=true

# OpenAI API Key (Dùng cho tính năng AI)
openai.api.key=sk-your-openai-api-key-here
```

### 4. Biên Dịch & Chạy Ứng Dụng

#### Trên Windows (PowerShell / Command Prompt):
```powershell
# Biên dịch dự án
.\gradlew build -x test

# Khởi chạy server Spring Boot
.\gradlew bootRun
```

#### Trên Linux / macOS:
```bash
chmod +x gradlew
./gradlew build -x test
./gradlew bootRun
```

---

## 🌐 Truy Cập Giao Diện Ứng Dụng

Sau khi server khởi chạy thành công tại port `8080`, hãy mở trình duyệt web và truy cập:

- **Trang chủ & Bảng giá**: [http://localhost:8080/index.html](http://localhost:8080/index.html)
- **Tải lên & Phân tích tài liệu**: [http://localhost:8080/upload.html](http://localhost:8080/upload.html)
- **Trợ lý Chatbot AI**: [http://localhost:8080/chat.html](http://localhost:8080/chat.html)
- **Thẻ ghi nhớ 3D**: [http://localhost:8080/flashcards.html](http://localhost:8080/flashcards.html)
- **Luyện thi trắc nghiệm**: [http://localhost:8080/quiz.html](http://localhost:8080/quiz.html)
- **Sơ đồ tư duy Mindmap**: [http://localhost:8080/mindmap.html](http://localhost:8080/mindmap.html)
- **Báo cáo tiến độ học tập**: [http://localhost:8080/progress.html](http://localhost:8080/progress.html)
- **Trang Admin Dashboard**: [http://localhost:8080/admin.html](http://localhost:8080/admin.html)

---

## 🔑 Tài Khoản Mặc Định (Mẫu)

| Vai Trò (Role) | Email | Mật Khẩu (Password) | Gói Dịch Vụ |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@study.com` | `admin123` | `PRO` |
| **Student** | `student@study.com` | `student123` | `FREE` |

---

## 📄 Giấy Phép (License)

Dự án được phát triển phục vụ cho Đồ Án Trí Tuệ Nhân Tạo / Nghiên cứu học tập.