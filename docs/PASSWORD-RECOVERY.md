# Mật khẩu và khôi phục bằng OTP

Đã bổ sung kiểm tra mật khẩu khi đăng ký và đặt lại: tối thiểu 8 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt. Backend áp dụng cùng chính sách và giới hạn 72 byte UTF-8 của BCrypt. Mật khẩu xác nhận được đối chiếu ngay khi nhập. Mật khẩu cũ của tài khoản hiện hữu vẫn được phép đăng nhập; chính sách mới áp dụng khi tạo hoặc đổi mật khẩu.

Tại cửa sổ đăng nhập, chọn Quên mật khẩu, nhập email đã đăng ký, nhận OTP rồi nhập mã cùng mật khẩu mới. OTP có 6 chữ số, hiệu lực 10 phút, chỉ dùng một lần, tối đa 5 lần nhập sai. Gửi lại cách nhau ít nhất 60 giây và tối đa 5 email mỗi giờ cho một tài khoản. Các API khôi phục còn giới hạn theo địa chỉ kết nối. Phản hồi yêu cầu gửi mã không tiết lộ email có tồn tại hay không. Mã OTP được lưu dạng BCrypt, không gửi ngược về API và không ghi vào log.

Có thể đăng nhập bằng tên đăng nhập hoặc email; email không phân biệt chữ hoa/thường. Sau khi đặt lại, đăng nhập bằng mật khẩu mới; mật khẩu cũ không còn hợp lệ. Việc đặt lại không tự mở tài khoản bị quản trị viên khóa hoặc đình chỉ.

Địa chỉ gửi đã cấu hình: datlnt05@gmail.com. Thông tin đăng nhập SMTP được lưu trong application.properties ở thư mục gốc dự án, tệp này đã có trong .gitignore. Không đưa tệp riêng này vào kho mã nguồn hoặc gửi kèm bản bàn giao công khai. Các môi trường khác có thể dùng MAIL_USERNAME, MAIL_PASSWORD, MAIL_HOST, MAIL_PORT. Mật khẩu Gmail là mật khẩu ứng dụng, không phải mật khẩu đăng nhập tài khoản Google.

Cấu hình gửi thư sử dụng Spring JavaMailSender và STARTTLS, có thời gian chờ kết nối/gửi/đọc để tránh treo yêu cầu. Tham khảo: https://docs.spring.io/spring-boot/reference/io/email.html

Khởi động lại ứng dụng để nạp cấu hình. Với ddl-auto=update hiện tại, bảng password_reset_challenges được thêm tự động. V15__password_reset_otp.sql dành cho môi trường quản lý schema thủ công.

Các tệp kiểm thử mới gồm PasswordRecoveryIntegrationTest.java (API, cơ sở dữ liệu, quota gửi, OTP hết hạn, nhập sai, dùng lại và cạnh tranh), password-policy.test.cjs (quy tắc trình duyệt), auth-ui-browser.cjs (giao diện với API giả lập) và otp-browser.cjs (luồng đầy đủ trên máy chủ QA cùng SMTP giả lập). Không trả OTP ra API của ứng dụng; hộp thư giả lập chỉ thuộc bộ kiểm thử localhost riêng.

Kết quả xác minh: biên dịch toàn bộ mã Java thành công; 21 kiểm thử backend OTP đạt; 227 kiểm thử JavaScript đạt; 3 nhóm kiểm tra giao diện giả lập và 4 nhóm luồng trình duyệt đầy đủ qua SMTP giả lập đều đạt. Gmail SMTP đã xác thực thành công; không gửi thư thử thật trong quá trình xác minh. Máy chủ QA dùng H2 và dữ liệu tổng hợp riêng, không thay đổi mật khẩu người dùng trong cơ sở dữ liệu thật.
