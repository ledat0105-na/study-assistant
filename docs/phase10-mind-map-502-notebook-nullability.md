Nguyên nhân đã xác nhận từ log host cổng 8081, tài liệu 3710:

```text
insert into concepts (... document_id ... notebook_id ...) values (...)
SQLIntegrityConstraintViolationException: Column 'notebook_id' cannot be null
KG chunk 5208 incomplete for document 3710: concepts=FAILED, relations=NO_CANDIDATES
```

Ứng dụng đã đi đến bước lưu khái niệm trích xuất. Lỗi được xác nhận ở MySQL, không phải ở bước xác thực API key. Tài liệu không có notebook dùng document scope: concept có document_id và notebook_id rỗng. Cấu trúc bảng concepts hiện tại trên host vẫn bắt buộc notebook_id NOT NULL nên không lưu được các khái niệm, rồi request trả 502.

Mã Concept hiện cho phép notebook rỗng, đồng thời yêu cầu đúng một phạm vi notebook hoặc document. Migration V12 cũng có phần đổi nullability. Tuy nhiên build.gradle chưa tích hợp Flyway nên chỉ có tệp V12 không có nghĩa migration đã được chạy. Các kiểm thử H2 tạo bảng từ entity, vì vậy không phát hiện cấu trúc MySQL cũ còn NOT NULL.

Log cho thấy document_id đã tồn tại trên host. Không chạy nguyên V12 một cách mù quáng: nó còn ADD COLUMN document_id và thêm các ràng buộc, có thể trùng với các thay đổi đã có.

Phần sửa tối thiểu cần duyệt: đổi duy nhất concepts.notebook_id sang cho phép NULL, giữ nguyên kiểu số, signedness, default và các khóa hiện có. Không tạo notebook giả, không gán lại tài liệu, không xóa dữ liệu. Đầu tiên phải xác nhận đúng DB mà backend cổng 8081 sử dụng và đọc SHOW CREATE TABLE concepts.

Đã chuẩn bị [SQL chỉ đọc để kiểm tra và sinh câu ALTER cần duyệt](D:/study-assistant/docs/sql/phase10-mind-map-notebook-nullability-review.sql). Tệp này không chạy ALTER. Nó giữ nguyên COLUMN_TYPE hiện tại, dừng nếu có thuộc tính cột bất thường và không đề nghị sửa nếu cột đã nullable.

Chưa kết nối, thay đổi hoặc áp dụng migration trên MySQL thật. Yêu cầu trước đó của người dùng là “No MySQL mutation. No production DB.”; cần được cho phép rõ ràng trước khi sửa cấu trúc DB này.

Kiểm chứng đã chạy trước khi nhận log: 40 kiểm thử backend mind-map/H2/readiness và 18 kiểm thử JavaScript đều PASS. Kết quả đó xác nhận mã làm việc với schema đúng, không xác nhận schema MySQL host đã được sửa.

Sau khi sửa DB được duyệt: kiểm tra IS_NULLABLE=YES, giữ backend trên đúng cổng 8081 và thử lại việc tạo sơ đồ tài liệu 3710. Chỉ xác nhận host PASS khi POST thành công và GET trả các nodes/edges có nguồn hợp lệ. Nếu còn lỗi, tiếp tục theo response và log mới thay vì suy đoán từ thông báo 502 chung.
