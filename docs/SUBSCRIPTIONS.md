# Hướng dẫn gói sử dụng Study Assistant

## Giá và thời hạn

- FREE: miễn phí, mặc định khi tạo tài khoản mới.
- PRO: 50.000đ/1 tháng; 120.000đ/3 tháng (40.000đ/tháng); 360.000đ/12 tháng (30.000đ/tháng).
- VIP: 100.000đ/1 tháng; 270.000đ/3 tháng (90.000đ/tháng); 960.000đ/12 tháng (80.000đ/tháng).

Giảm giá được trừ trên mỗi tháng. Backend tự tính số tiền từ gói và thời hạn; số tiền do trình duyệt gửi không quyết định giá thanh toán.

## Hạn mức

- FREE mỗi tháng: 3 tài liệu tải lên, 20 lượt chat, 3 lượt tạo quiz, 3 lượt tạo flashcard.
- PRO mỗi tháng: 50 tài liệu tải lên, 500 lượt chat, 100 lượt tạo quiz, 100 lượt tạo flashcard.
- VIP không giới hạn quota sử dụng; các giới hạn kỹ thuật của file và yêu cầu vẫn áp dụng.
- Tóm tắt và sơ đồ tư duy: FREE 3 lượt/tháng, PRO 100 lượt/tháng, VIP không giới hạn.
- Sổ tay: FREE tối đa 3, PRO tối đa 50; dung lượng lưu trữ tương ứng 100 MB và 2 GB. VIP không giới hạn theo gói.

Quota được đặt lại vào đầu tháng theo giờ Việt Nam, không theo ngày mua gói. Nâng cấp giữ số lượt đã dùng trong tháng và tăng mức trần. Yêu cầu thất bại hoàn lại lượt đã giữ. Backend kiểm tra hạn mức, kể cả khi gọi API trực tiếp; khi hết quota trả HTTP 429 và giao diện hiện yêu cầu nâng cấp. Trang hồ sơ hiển thị nhãn gói, số lượt dùng, ngày đặt lại và ngày hết hạn.

## Đăng ký, duyệt và hết hạn

Đăng ký tạo yêu cầu PENDING. Quản trị viên xác nhận thanh toán rồi duyệt để kích hoạt. Đây là gói trả trước, không tự động thu tiền gia hạn. Gia hạn cùng gói cộng thêm số tháng vào thời hạn còn lại; đổi sang gói khác thay gói hiện tại ngay sau khi được duyệt. Trang thanh toán có thông báo này.

Hết hạn tự trở về FREE; dữ liệu đã tạo không bị xóa. Tài khoản quản trị có quyền VIP nội bộ. Tài khoản người dùng không có đăng ký ACTIVE còn hạn được tính là FREE.

Thông tin nhận chuyển khoản cần cấu hình các biến môi trường BILLING_BANK_NAME, BILLING_BANK_ACCOUNT, BILLING_BANK_HOLDER. Khi chưa cấu hình, giao diện yêu cầu liên hệ quản trị viên và không hiển thị tài khoản ngân hàng giả. Không có giao dịch tiền thật nào được thực hiện trong kiểm thử.

## Cơ sở dữ liệu

Với cấu hình ddl-auto=update hiện tại, ứng dụng tự bổ sung schema khi khởi động và chạy chuyển đổi dữ liệu một lần, đánh dấu tại billing_policy_versions. STUDENT/BASIC cũ chuyển thành PRO; PRO/PREMIUM cũ chuyển thành VIP để giữ cấp quyền tương ứng. Thời hạn hợp lệ được giữ lại. Tài liệu đã tải trong tháng được đưa vào bộ đếm ban đầu.

V14__free_pro_vip_billing.sql là phương án migration thủ công cho môi trường quản lý schema riêng; không chạy lại sau khi cơ chế tự động đã chuyển đổi dữ liệu. Sao lưu cơ sở dữ liệu trước khi triển khai production. Giá chuẩn và hạn mức được định nghĩa phía server trong PlanPolicy.

## Kiểm thử đã thực hiện

- 968 kiểm thử Java mặc định: đạt, gồm kiểm thử quota, cạnh tranh đồng thời, hoàn lượt, thời hạn, chống sửa giá và chống vượt quota qua URL.
- 416 kiểm thử tích hợp MySQL: đạt.
- 212 kiểm thử JavaScript: đạt.
- 8 luồng trình duyệt trên cơ sở dữ liệu QA riêng: đăng ký FREE, hết hạn mức, đủ 6 mức giá, giao diện di động, duyệt PRO/VIP, VIP không giới hạn và hết hạn về FREE.

Chạy kiểm thử mặc định bằng gradlew.bat test --offline --no-daemon và node --test src/test/js/*.test.cjs. Bộ kiểm thử trình duyệt nằm tại src/test/js/billing-browser.cjs, cần máy chủ QA localhost cổng 18082 và cơ sở dữ liệu studyassistant_browser_test. scripts/billing-qa-seed.py chỉ cập nhật tài khoản tổng hợp billing_browser_<timestamp> trong cơ sở dữ liệu QA đó.

Khởi động lại ứng dụng để nạp mã nguồn và cấu hình mới. Các kết quả trên phản ánh bộ kiểm thử đã chạy; không phải cam kết mọi lỗi có thể phát sinh đều đã được loại bỏ.
