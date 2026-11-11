# Chạy kiểm thử và xem lỗi

Chạy PowerShell tại thư mục dự án:

```powershell
.\run-checks.ps1
```

Lệnh này chạy test Java (JUnit/Mockito/H2) và test JavaScript bằng Node.js. Test có tag Integration/E2E không nằm trong lệnh mặc định. Báo cáo Java: `build/reports/tests/test/index.html`.

## Kiểm tra trình duyệt

Cần Chrome và package `playwright` có thể được Node.js resolve. Nếu máy chưa có package:

```powershell
npm install --prefix build/qa-tools --no-save playwright docx
$env:NODE_PATH = "$PWD/build/qa-tools/node_modules"
.\run-checks.ps1 -Browser
```

Browser smoke kiểm tra trang chủ, mở form đăng nhập/đăng ký ở 1440px và 390px, tiếng Việt, lỗi JavaScript, tràn ngang và nhãn giá đè tên gói. Ảnh nằm trong `build/ui-smoke/`. Máy chủ fixture trả 503 cho API để kiểm tra fallback giao diện; test này không xác nhận đăng nhập hay thanh toán thật.

## File lỗi

Ứng dụng ghi lỗi backend và báo cáo lỗi JavaScript vào `logs/error.log` khi đang chạy. Có thể đổi thư mục qua biến môi trường `APP_LOG_DIR`. Log dùng UTF-8, xoay theo ngày hoặc khi đạt 10 MB; lưu tối đa 14 ngày, tổng dung lượng 200 MB. Log không được commit vào Git. Log do kiểm thử Java được tách riêng tại `build/test-logs/error.log`.

```powershell
Get-Content .\logs\error.log -Tail 80 -Wait
```

Lỗi JavaScript được gửi về `/api/client-errors`, chỉ gồm loại lỗi, đường dẫn file, dòng và cột; không gửi nội dung lỗi, query string, mật khẩu hay key. Giới hạn 5 báo cáo/phút trên trình duyệt và session. Khi mất mạng, không thể gửi báo cáo về server.

Lỗi API chưa được xử lý sẽ được ghi stack trace và trả HTTP 500 kèm thông báo chung. Lỗi dữ liệu đầu vào vẫn trả 400, sai phương thức trả 405, đường dẫn không có trả 404. Các handler riêng giữ nguyên hợp đồng hiện tại. Lỗi không được biến thành HTTP 200. Ghi log không khắc phục được JVM hết RAM, mất điện, CSDL không khởi động hoặc tiến trình bị tắt.

## Kiểm thử tích hợp trên môi trường riêng

Chỉ dùng CSDL test `studyassistant_rag_test`, tuyệt đối không trỏ test vào CSDL dữ liệu thật. Xem `src/test/resources/application-integration.properties` và các SQL prerequisite trong `src/test/resources/db/integration/` trước khi chạy:

```powershell
.\gradlew.bat mysqlIntegrationTest --console=plain
```

Có thể dùng `./run-checks.ps1 -Integration -E2E` để chạy thêm toàn bộ hai nhóm. Test E2E dùng MySQL/Qdrant thật và AI fixture xác định, không gọi OpenAI thật.

Cờ `-ResetQaDatabase` của script (hoặc `-PresetQaDatabase=true` khi gọi Gradle) **xóa dữ liệu fixture trong studyassistant_rag_test trước mỗi lớp test** để cô lập các lớp. Chỉ bật cờ này với CSDL test riêng có thể xóa; không sử dụng CSDL test chứa dữ liệu cần giữ. Extension kiểm tra chính xác tên CSDL trước khi thao tác. Các lớp H2 được cô lập bằng database/context riêng.

Cần chuẩn bị đúng schema/migration, tài khoản TEST_DB_USERNAME/TEST_DB_PASSWORD và dịch vụ Qdrant nếu test yêu cầu. Các test đang Disabled hoặc có điều kiện môi trường vẫn có thể bị bỏ qua. E2E gọi OpenAI thật cần môi trường riêng và có thể phát sinh phí.

## Ca kiểm thử thủ công trước khi bàn giao

Dùng tài khoản và tài liệu test riêng; với mỗi ca, kiểm tra kết quả mong đợi, Console/Network và error.log:

- Tài khoản: đăng ký hợp lệ/trùng email/thiếu dữ liệu; đăng nhập đúng/sai/bị khóa; đăng xuất rồi truy cập lại trang cần đăng nhập. Tài khoản thường không được dùng API admin.
- Notebook và tài liệu: tạo/đổi tên/xóa; tên rỗng hoặc dài; upload PDF/DOCX hợp lệ, file sai loại, quá 30 MB; tài liệu đang xử lý/thất bại; người khác không được đọc/xóa. Xóa không làm vượt/sai quota.
- AI: tóm tắt, bản đồ, chat có trích nguồn; trạng thái đang xử lý, hết quota, timeout provider; bấm tạo nhiều lần hoặc chuyển tài liệu trong lúc chờ không ghi đè kết quả tài liệu mới.
- Flashcard và quiz: tạo, xem, ôn, chọn đáp án, nộp lặp; chấm điểm, tiến độ, nội dung tiếng Việt chính xác và không mất dữ liệu sau tải lại.
- Gia sư/mastery/đề xuất: dữ liệu theo đúng chủ tài khoản, lịch sử và trạng thái sau trả lời đúng/sai; không hiện nội dung nội bộ khi provider lỗi.
- Bài giảng: tạo outline/slides, sửa đồng thời, upload ảnh, thử file ảnh lỗi; xuất PPTX và mở kiểm tra font, ảnh, xuống dòng, nội dung không bị cắt.
- Hồ sơ và quản trị: lưu tên, mật khẩu; phân quyền đọc/sửa; danh sách người dùng/tài liệu, nhật ký; dùng cổng thanh toán sandbox để thử thành công/thất bại/webhook lặp/sai chữ ký.
- Giao diện: kiểm tra từng trang ở 390/768/1440 px, zoom 200%, bàn phím Tab; font tiếng Việt có dấu, thông báo rỗng/lỗi, bảng dài, modal và nút không bị che. Xem trực tiếp các trang cần đăng nhập với API thật.
- Phục hồi: cố ý gây lỗi một request trên môi trường test, xác nhận error.log có lỗi và request tiếp theo vẫn chạy. Tắt Qdrant/provider rồi bật lại để kiểm tra retry; không thử tắt các dịch vụ đang phục vụ dữ liệu thật.

Các ca thủ công trên là checklist cần thực hiện, không đồng nghĩa đã chạy thành công toàn bộ hệ thống.


## Các lỗi đã bổ sung kiểm thử hồi quy

- Trang hồ sơ phải gọi PATCH để lưu tên/email và đọc lại dữ liệu từ server sau khi tải lại; API không trả password/reset token.
- Dashboard dùng `/api/progress/dashboard`, xử lý số flashcard bằng 0 và không thêm ký hiệu phần trăm hai lần.
- Xem bảng giá không được ghi đè nội dung quản trị viên đã chỉnh; checkout từ chối gói không tồn tại/ngừng hoạt động.
- Trang quản trị thanh toán có API liệt kê/duyệt/từ chối, kiểm tra quyền ADMIN; webhook kiểm tra dữ liệu và khóa giao dịch khi xử lý đồng thời.
- Xóa vector sử dụng dimension đã cấu hình. Mô hình JPA phản ánh cascade xóa evidence của quan hệ khái niệm.
- Điều hướng viewer trên điện thoại không được tràn ngang hoặc che tiêu đề; các nút bản đồ xuống hàng, tiêu đề và trích nguồn có độ tương phản trên nền trắng.
- PRO/PREMIUM và STUDENT/BASIC nhận cùng quota notebook, tránh bị áp giới hạn FREE sau nâng gói.


## Trình duyệt có đăng nhập và các API thật

`node src/test/js/browser-authenticated.cjs` chạy kiểm tra 22 trang với 3 kích thước, lưu hồ sơ, tạo notebook, upload DOCX, lập chỉ mục bằng OpenAI/Qdrant, tóm tắt, bản đồ, flashcard, chat, quiz/nộp lặp, phân quyền, thanh toán nội bộ và xóa tài liệu. Đây là bài kiểm thử chủ động tạo dữ liệu và gọi OpenAI thật.

Chỉ chạy trên server QA `http://127.0.0.1:18080`, dùng CSDL riêng `studyassistant_browser_test`, thư mục upload/media riêng và Qdrant test. QA phải cấu hình `PAYMENT_WEBHOOK_SECRET=qa-local-test-only` và có tài khoản admin mặc định của môi trường test. Không cấu hình secret này cho môi trường thật. Cần Chrome, Node.js và các package `playwright`, `docx` có thể resolve; kết quả/ảnh nằm tại `build/qa-authenticated/`.

Các luồng thanh toán kiểm tra API nội bộ/giả lập callback; không chuyển tiền và không xác nhận tích hợp cổng thanh toán bên ngoài.
