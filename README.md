# Study Assistant — Hướng dẫn sử dụng toàn bộ phần mềm

Cập nhật ngày 04/11/2026. Ứng dụng hỗ trợ học từ PDF, DOCX, PPTX bằng hỏi đáp AI, tóm tắt, sơ đồ kiến thức, flashcard, quiz và tạo bài giảng. Hướng dẫn này mô tả thao tác trên giao diện hiện có; phần cuối giữ tài liệu kỹ thuật của dự án để phục vụ báo cáo và cài đặt.

## Mục lục hướng dẫn sử dụng

- [1. Chuẩn bị và mở ứng dụng](#su-dung-1)
- [2. Tài khoản, đăng nhập và quên mật khẩu](#su-dung-2)
- [3. Menu, hồ sơ và notebook](#su-dung-3)
- [4. Tải lên và quản lý tài liệu](#su-dung-4)
- [5. Đọc tài liệu và hỏi đáp AI](#su-dung-5)
- [6. Tóm tắt và sơ đồ kiến thức](#su-dung-6)
- [7. Flashcard và quiz](#su-dung-7)
- [8. Kết quả và tiến độ học tập](#su-dung-8)
- [9. Tạo bài giảng dành cho giảng viên](#su-dung-9)
- [10. Gói FREE, PRO, VIP và đăng ký](#su-dung-10)
- [11. Hướng dẫn quản trị](#su-dung-11)
- [12. Xử lý lỗi và nhật ký](#su-dung-12)
- [13. Kịch bản trình bày trước lớp](#su-dung-13)
- [14. Cài đặt, kiểm thử và tài liệu liên quan](#su-dung-14)

<a id="su-dung-1"></a>
## 1. Chuẩn bị và mở ứng dụng

Người dùng mở địa chỉ do người triển khai cung cấp. Khi chạy trên máy cá nhân với cấu hình mặc định, truy cập **http://localhost:8080/index.html**. Phải khởi động backend trước; không mở tệp HTML bằng cách nhấp đúp trong File Explorer.

Máy chủ cần Java 17, MySQL, Qdrant và cấu hình API AI. Gửi OTP cần cấu hình SMTP. Học viên chỉ cần trình duyệt hiện đại và kết nối tới máy chủ. Một số thư viện giao diện tải từ CDN nên cần Internet khi chưa có trong bộ nhớ đệm.

Các vai trò:

- **STUDENT:** tài liệu, notebook, hỏi đáp AI, tóm tắt, sơ đồ, flashcard, quiz và hồ sơ.
- **TEACHER:** có chức năng học tập và thêm mục **Bài giảng AI**.
- **ADMIN:** quản trị tài khoản, tài liệu, thanh toán, nhật ký; có quyền chức năng giảng viên.

Vai trò và gói dịch vụ là hai khái niệm riêng. PRO/VIP tăng hạn mức, không tự chuyển STUDENT thành TEACHER hoặc ADMIN. Tài khoản mới luôn là STUDENT và FREE.

<a id="su-dung-2"></a>
## 2. Tài khoản, đăng nhập và quên mật khẩu

### 2.1. Tạo tài khoản

1. Tại trang chủ chọn **Tạo tài khoản**.
2. Nhập họ tên, tên đăng nhập chưa có người dùng và email hợp lệ. Dùng email đang truy cập được để khôi phục mật khẩu khi cần.
3. Nhập mật khẩu ít nhất **8 ký tự**, gồm **chữ hoa, chữ thường, chữ số và ký tự đặc biệt**. Danh sách điều kiện đổi trạng thái ngay khi nhập. Backend cũng kiểm tra để ngăn bỏ qua bằng API. Giới hạn kỹ thuật của mật khẩu là 72 byte UTF-8.
4. Nhập lại đúng mật khẩu ở ô xác nhận. Nút biểu tượng mắt cho phép hiện/ẩn mật khẩu.
5. Chọn **Tạo tài khoản mới**. Khi thành công, ứng dụng tạo phiên đăng nhập và chuyển tới bảng điều khiển; nhãn gói là FREE.

### 2.2. Đăng nhập và đăng xuất

Nhập **tên đăng nhập hoặc email** cùng mật khẩu. Email không phân biệt chữ hoa/thường. Chọn **Đăng nhập ngay**. Nếu nhập sai nhiều lần, tài khoản có thể bị khóa tạm thời; tài khoản bị quản trị viên khóa/đình chỉ cần liên hệ quản trị viên. Đăng xuất bằng nút ở cuối thanh menu.

Không cần đổi mật khẩu hiện hữu chỉ để đăng nhập. Chính sách độ mạnh áp dụng khi tạo tài khoản hoặc đặt mật khẩu mới.

### 2.3. Quên mật khẩu bằng OTP

1. Ở cửa sổ đăng nhập, chọn **Quên mật khẩu?**.
2. Nhập email đã đăng ký rồi chọn **Gửi mã OTP**.
3. Kiểm tra hộp thư đến và thư rác. Ứng dụng trả cùng một thông báo cho email đã/chưa đăng ký để bảo vệ thông tin tài khoản.
4. Nhập **OTP 6 chữ số**, mật khẩu mới đủ điều kiện và mật khẩu xác nhận.
5. Chọn **Đổi mật khẩu**, đọc modal thành công và bấm **Đã hiểu**.
6. Đăng nhập lại bằng email/tên đăng nhập và mật khẩu mới. Mật khẩu cũ không còn hợp lệ.

OTP có hiệu lực **10 phút**, dùng **một lần**, tối đa **5 lần nhập sai**. Được gửi lại sau **60 giây**, tối đa **5 email/giờ/tài khoản**. Mã mới thay mã cũ. Có thể chọn **Đổi email** để sửa địa chỉ nhập nhầm. Mã OTP không xuất hiện trong API hay nhật ký ứng dụng. Đặt lại mật khẩu không tự mở trạng thái khóa/đình chỉ do quản trị viên áp dụng.

<a id="su-dung-3"></a>
## 3. Menu, hồ sơ và notebook

Sau khi đăng nhập, menu cung cấp **Bảng điều khiển**, **Kho tài liệu học tập**, **Trợ lý Chat AI & 3D Studio**, **Tiến độ & Thống kê** và **Hồ sơ cá nhân**. Giảng viên có thêm **Bài giảng AI**. Trên điện thoại, bấm **Menu** để mở/thu thanh điều hướng.

Trong **Hồ sơ cá nhân**, cập nhật họ tên/email rồi lưu. Kiểm tra nhãn FREE/PRO/VIP, số lượt đã dùng, hạn mức, ngày làm mới và ngày hết hạn đăng ký. Thông tin gói do server xác định, không dựa vào giá trị người dùng tự sửa trong trình duyệt.

Notebook là nơi nhóm tài liệu theo môn/chủ đề. Khi giao diện yêu cầu chọn notebook, chọn cùng nhóm tài liệu cần học. Backend hỗ trợ tạo, xem, đổi tên và xóa mềm notebook có kiểm tra chủ sở hữu; không phải mọi API notebook đều có màn hình thao tác riêng. FREE tối đa 3 notebook, PRO 50, VIP không giới hạn theo gói.

<a id="su-dung-4"></a>
## 4. Tải lên và quản lý tài liệu

1. Mở **Kho tài liệu học tập** rồi chọn chức năng tải lên.
2. Chọn hoặc kéo thả **PDF, DOCX hoặc PPTX**, tối đa **25 MiB/tệp**. Chọn notebook nếu màn hình có yêu cầu gán.
3. Bắt đầu tải và chờ xử lý. Chỉ sinh nội dung AI khi tài liệu đã **READY**.
4. Mở tài liệu để đọc hoặc chuyển sang Studio.

Trạng thái:

- **PROCESSING:** đang trích xuất/chia đoạn/tạo chỉ mục, hãy chờ.
- **READY:** sẵn sàng đọc và sử dụng AI.
- **FAILED:** xử lý chưa thành công; kiểm tra nguyên nhân và dùng **Thử lại** nếu được cung cấp. Dịch vụ giới hạn số lần retry.
- **DELETED:** tài liệu đã xóa, không tiếp tục dùng làm nguồn hiện hành.

Danh sách tài liệu cho phép mở, tìm/chọn tài liệu và xóa theo nút trên màn hình. Việc xóa chỉ áp dụng với tài liệu có quyền quản lý. Nên giữ bản gốc khi cần dùng lại. PDF scan chỉ chứa ảnh có thể không trích được văn bản vì hệ thống chưa tích hợp OCR đầy đủ. Tệp có mật khẩu, bị hỏng hoặc thiếu văn bản cũng có thể xử lý thất bại.

<a id="su-dung-5"></a>
## 5. Đọc tài liệu và hỏi đáp AI

### 5.1. Đọc tài liệu

PDF hiển thị trong trình đọc PDF; DOCX/PPTX dùng nội dung văn bản đã trích xuất. Chọn đúng tài liệu đang READY. Khi đổi tài liệu, các công cụ AI chuyển sang nguồn đang hoạt động; chờ tải dữ liệu xong trước khi thao tác tiếp.

### 5.2. Chat AI trong Studio

1. Mở **Trợ lý Chat AI & 3D Studio**.
2. Chọn nguồn/tài liệu phù hợp và notebook liên quan.
3. Nhập câu hỏi cụ thể, ví dụ: “Giải thích khái niệm X trong tài liệu và nêu ví dụ”.
4. Đọc câu trả lời và đối chiếu tham chiếu nguồn nếu có.
5. Hỏi tiếp để làm rõ, so sánh khái niệm hoặc kiểm tra kiến thức.

Chat dùng truy xuất tài liệu kết hợp mô hình ngôn ngữ. Với luồng `/api/ai/chat` hiện tại, phạm vi truy xuất dựa trên notebook của tài liệu đầu tiên, có thể bao gồm nguồn khác trong cùng notebook mà người dùng có quyền truy cập. Không trình bày lựa chọn nhiều tài liệu như cơ chế giới hạn tuyệt đối chỉ các tệp đã chọn. Tài liệu chưa gán notebook có thể chưa dùng được luồng này. Khi không đủ bằng chứng, hãy bổ sung nguồn hoặc đặt câu hỏi rõ hơn.

<a id="su-dung-6"></a>
## 6. Tóm tắt và sơ đồ kiến thức

### 6.1. Tóm tắt

Mở tab **Tóm tắt** của tài liệu. Ứng dụng đọc nội dung đã lưu; khi chưa có, luồng giao diện có thể yêu cầu sinh mới. Kết quả gồm tổng quan, ý chính, thuật ngữ/kết luận và nguồn tùy dữ liệu trả về. Dùng **Thử lại** khi báo lỗi. Tóm tắt là nội dung AI hỗ trợ, cần đối chiếu tài liệu gốc.

### 6.2. Sơ đồ kiến thức

Mở tab sơ đồ hoặc trang bản đồ của tài liệu. Các nút biểu diễn khái niệm và cạnh biểu diễn quan hệ. Bấm nút để đọc chi tiết, mở/thu nhánh; dùng điều khiển zoom/fit và kéo để di chuyển sơ đồ. Sơ đồ lớn có phân nhánh/phân trang để dễ xem. Cuộn bình thường để di chuyển trang, Ctrl + cuộn để zoom khi renderer hỗ trợ. Nếu chỉ sinh được một phần, giao diện giữ phần hợp lệ và thông báo để thử bổ sung.

FREE có 3 lượt tóm tắt và 3 lượt sơ đồ/tháng; PRO 100 lượt mỗi loại/tháng; VIP không giới hạn quota sử dụng. Đọc lại kết quả đã lưu khác với yêu cầu sinh mới.

<a id="su-dung-7"></a>
## 7. Flashcard và quiz

### 7.1. Thẻ ghi nhớ

Chọn tài liệu READY và mở **Flashcard**. Nếu chưa có, dùng thao tác tạo từ tài liệu. Bấm thẻ để lật giữa câu hỏi/thuật ngữ và đáp án/định nghĩa, rồi chuyển thẻ trước/sau. Studio có luồng tạo thêm khi được cung cấp.

Hạn mức tính theo **lượt tạo bộ/thẻ từ yêu cầu**, không phải mỗi lần lật hoặc mỗi thẻ được sinh. Backend có hàng đợi review, đánh giá nhớ và lập lịch **FSRS**; trang lật thẻ hiện tại không đồng nghĩa mọi thao tác đều ghi lịch FSRS. Chỉ mô tả lịch ôn cá nhân khi dùng đúng API/luồng review đã tích hợp.

### 7.2. Làm bài kiểm tra

1. Chọn tài liệu READY rồi mở **Quiz** hoặc chức năng tạo quiz trong Studio.
2. Chọn cấu hình mà giao diện cung cấp và tạo bộ câu hỏi.
3. Đọc từng câu, chọn/nhập câu trả lời.
4. Chọn nộp bài và chờ server chấm.
5. Xem điểm, câu đúng/sai và giải thích/đáp án sau khi nộp theo quyền được phép.

Hệ thống lưu lượt làm bài và chấm điểm phía server. Backend hỗ trợ nhiều kiểu câu hỏi/chế độ và cơ chế chống nộp trùng; không mặc định mọi lựa chọn backend đều có nút trên giao diện hiện tại. Hạn mức là số lượt tạo quiz, không phải số câu trong một bài.

<a id="su-dung-8"></a>
## 8. Kết quả và tiến độ học tập

Trang kết quả dùng để xem phản hồi bài làm; dùng thông tin câu sai để đọc lại nguồn và ôn tập. Mục **Tiến độ & Thống kê** là nơi xem các phần tổng hợp đang được giao diện cung cấp.

**Phạm vi hiện tại:** `progress.html` còn biểu đồ/giá trị minh họa tĩnh, không nên lấy các số giờ hiển thị làm số liệu đo thật của tài khoản. Backend có dữ liệu kết quả, learning events, mastery, gợi ý thích nghi, tutor và exam-readiness. Một số năng lực này chưa có màn hình hoàn chỉnh. Chỉ số readiness là chỉ báo hỗ trợ học tập, không phải xác suất đỗ kỳ thi đã được kiểm chứng.

<a id="su-dung-9"></a>
## 9. Tạo bài giảng dành cho giảng viên

Dùng tài khoản TEACHER hoặc ADMIN:

1. Mở **Bài giảng AI** và tạo bộ bài giảng mới.
2. Nhập thông tin, chọn tài liệu nguồn có quyền truy cập.
3. Mở bộ bài giảng, chọn **Tạo dàn ý**, khai báo yêu cầu/ngôn ngữ mà cửa sổ cung cấp.
4. Chờ job, xem các phiên bản và **duyệt dàn ý** phù hợp.
5. Chọn **Sinh slide** từ dàn ý đã duyệt. Theo dõi tiến độ; thử lại slide lỗi khi chức năng cho phép.
6. Mở trình chỉnh sửa để sửa tiêu đề/ghi chú, thêm văn bản, hình khối hoặc ảnh, điều chỉnh thuộc tính/vị trí/kích thước. Lưu thay đổi.
7. Quản lý thư viện ảnh của bộ bài giảng, dùng chức năng xem trước.
8. Chọn **Xuất PPTX** và mở tệp trong PowerPoint để kiểm tra nội dung trước khi trình bày.

Cần kiểm tra lại thông tin AI, trích dẫn và bố cục sau khi xuất. Sinh bài giảng là tác vụ bất đồng bộ, có giới hạn tài nguyên riêng; quyền VIP không bỏ các giới hạn kỹ thuật của một tác vụ.

<a id="su-dung-10"></a>
## 10. Gói FREE, PRO, VIP và đăng ký

### 10.1. Hạn mức mỗi tháng

- **FREE:** 3 tài liệu tải lên, 20 chat, 3 lượt tạo quiz, 3 lượt tạo flashcard. Lưu trữ tối đa 100 MB.
- **PRO:** 50 tài liệu tải lên, 500 chat, 100 lượt tạo quiz, 100 lượt tạo flashcard. Lưu trữ tối đa 2 GB.
- **VIP:** không giới hạn quota sử dụng theo gói; vẫn có giới hạn kỹ thuật từng tệp/yêu cầu.

Quota làm mới đầu tháng theo **giờ Việt Nam**. Nâng cấp giữ số lượt đã dùng và tăng mức trần, không đặt lại bộ đếm. Yêu cầu sinh/tải thất bại hoàn lượt đã giữ. Khi hết quota, backend chặn yêu cầu và giao diện hiện modal nâng cấp FREE sang PRO hoặc PRO sang VIP. Dữ liệu đã tạo không bị xóa khi hết hạn gói.

### 10.2. Giá đăng ký trả trước

- PRO: **50.000đ/1 tháng**; **120.000đ/3 tháng** (40.000đ/tháng); **360.000đ/12 tháng** (30.000đ/tháng).
- VIP: **100.000đ/1 tháng**; **270.000đ/3 tháng** (90.000đ/tháng); **960.000đ/12 tháng** (80.000đ/tháng).

Gói 3 tháng giảm 10.000đ trên mỗi tháng; gói năm giảm 20.000đ trên mỗi tháng. Server tự tính giá. FREE miễn phí, không cần tạo thanh toán.

### 10.3. Đăng ký, duyệt và gia hạn

Chọn gói/thời hạn ở bảng giá hoặc hồ sơ. Trang thanh toán hiển thị tổng tiền, đơn giá tháng và thông tin nhận chuyển khoản nếu quản trị viên đã cấu hình. Gửi yêu cầu tạo trạng thái **PENDING**. Quản trị viên xác nhận thanh toán rồi duyệt mới kích hoạt; tạo yêu cầu chưa đồng nghĩa đã có quyền trả phí.

Gia hạn cùng gói cộng thời hạn còn lại. Đổi sang gói khác thay gói hiện tại ngay sau khi duyệt theo thông báo trên trang thanh toán. Gói trả trước không tự thu tiền gia hạn. Khi hết hạn, tài khoản trở về FREE. Nếu chưa có thông tin ngân hàng, liên hệ quản trị viên; chưa có cơ chế đối soát ngân hàng tự động hoàn chỉnh.

<a id="su-dung-11"></a>
## 11. Hướng dẫn quản trị

Đăng nhập ADMIN và sử dụng menu quản trị:

- **Tổng quan:** xem dữ liệu tổng hợp quản trị do backend trả về.
- **Người dùng:** tìm/xem, tạo hoặc cập nhật thông tin, đổi vai trò, khóa/mở/đình chỉ hoặc xóa tài khoản theo quyền. Chọn TEACHER nếu cần cấp quyền tạo bài giảng. Cấp gói trả phí qua đăng ký đã duyệt để có thời hạn hợp lệ, không chỉ sửa nhãn gói.
- **Tài liệu hệ thống:** xem và xóa tài liệu cần quản lý. Kiểm tra đúng chủ sở hữu/tài liệu trước khi xác nhận.
- **Thanh toán:** xem yêu cầu PENDING, đối chiếu tiền thực nhận rồi duyệt hoặc từ chối. Sau khi duyệt, kiểm tra gói và ngày hết hạn ở tài khoản thử nghiệm.
- **Bảng giá:** trang/API quản lý bản ghi bảng giá vẫn tồn tại. Giá công khai/checkout chuẩn FREE–PRO–VIP hiện lấy từ `PlanPolicy` phía server; không coi việc sửa một bản ghi cũ là đã đổi chính sách thanh toán.
- **Audit:** xem nhật ký nghiệp vụ để truy lại thao tác quản trị/thanh toán mà ứng dụng ghi nhận.

ADMIN có quyền VIP nội bộ, không đại diện trạng thái thuê bao trả phí của học viên. Không dùng tài khoản quản trị để minh họa quota FREE.

<a id="su-dung-12"></a>
## 12. Xử lý lỗi và nhật ký

- Không mở được trang: kiểm tra backend đang chạy, URL/cổng đúng và kết nối tới máy chủ.
- Không đăng nhập: kiểm tra email/tên đăng nhập, mật khẩu và trạng thái tài khoản; dùng OTP nếu quên.
- Không nhận OTP: kiểm tra email đã đăng ký, thư rác, chờ hết đếm ngược; quản trị kiểm tra SMTP và mật khẩu ứng dụng. Không gửi yêu cầu liên tục.
- Không tải được tệp: kiểm tra định dạng, dung lượng 25 MiB, quota tháng, dung lượng lưu trữ và tệp có hỏng/khóa hay không.
- AI báo chưa sẵn sàng: chờ READY, kiểm tra notebook/phạm vi nguồn; nếu thất bại, quản trị kiểm tra provider AI, Qdrant và chỉ mục.
- Hết hạn mức: xem hồ sơ và nâng cấp đúng gói. Đọc lại kết quả đã lưu khi phù hợp.
- Giao diện cũ: sau khi triển khai lại, nhấn Ctrl + F5. Trên điện thoại dùng Menu để mở thanh điều hướng.

Thông báo alert đã chuyển sang modal có nút **Đã hiểu**. Các lỗi nghiệp vụ trả phản hồi để giao diện tiếp tục xử lý. Lỗi hệ thống ghi ở **logs/error.log**, có xoay vòng theo cấu hình Logback; lỗi JavaScript gửi metadata giới hạn về `/api/client-errors`. Nhật ký không bảo đảm chương trình tiếp tục chạy trong mọi sự cố hạ tầng, hết bộ nhớ hay dừng tiến trình.

<a id="su-dung-13"></a>
## 13. Kịch bản trình bày trước lớp

Chuẩn bị trước một tài liệu ngắn có văn bản rõ, notebook đã gán, dữ liệu READY, tài khoản STUDENT/FREE, TEACHER và ADMIN thử nghiệm, cùng một deck đã sinh để dự phòng thời gian chờ AI.

1. Giới thiệu bài toán và ba vai trò, mở trang chủ.
2. Minh họa điều kiện mật khẩu, đăng nhập bằng email và giới thiệu luồng OTP.
3. Mở kho tài liệu, tải tệp hoặc dùng tệp READY chuẩn bị sẵn.
4. Hỏi một câu có đáp án rõ trong nguồn, mở tham chiếu để đối chiếu.
5. Mở tóm tắt và sơ đồ, chọn một khái niệm.
6. Lật vài flashcard, làm quiz ngắn và xem kết quả.
7. Giới thiệu bảng gói và tổng tiền 3/12 tháng; giải thích duyệt thủ công.
8. Chuyển TEACHER, xem dàn ý, slide đã sinh, sửa một phần tử và xuất PPTX.
9. Chuyển ADMIN, giới thiệu quản lý người dùng, tài liệu, yêu cầu thanh toán và audit.
10. Nêu giới hạn OCR, độ tin cậy AI, phần thống kê minh họa và hướng phát triển.

Bản trình chiếu đi kèm có ghi chú cho người thuyết trình. Không cần chạy toàn bộ tác vụ sinh AI dài trong lúc báo cáo.

<a id="su-dung-14"></a>
## 14. Cài đặt, kiểm thử và tài liệu liên quan

Khởi động tại thư mục dự án bằng `.\gradlew.bat bootRun`; xem phần kỹ thuật bên dưới để cấu hình MySQL/Qdrant/provider. SMTP có các biến MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD. Ngân hàng có BILLING_BANK_NAME, BILLING_BANK_ACCOUNT, BILLING_BANK_HOLDER. Dùng giá trị của môi trường triển khai; không đưa khóa/mật khẩu thật vào tài liệu hoặc slide.

Tài liệu chi tiết: [gói dịch vụ](docs/SUBSCRIPTIONS.md), [mật khẩu và OTP](docs/PASSWORD-RECOVERY.md). SQL V14/V15 phục vụ các thay đổi gói và OTP; dự án chưa dùng Flyway để tự chạy mọi migration. Kiểm tra schema trước khi áp dụng thủ công.

Kiểm thử Java mặc định: `.\gradlew.bat test`. JavaScript: `node --test src/test/js/*.test.cjs`. Các tệp `*-browser.cjs` chạy riêng theo môi trường QA của chúng, không gộp vào lệnh unit test. MySQL integration dùng database thử nghiệm riêng.

Kết quả được ghi nhận theo đợt, không cộng thành một lần chạy mới: 01/11/2026 có 968 Java mặc định, 416 MySQL integration và 212 JavaScript đạt cho phiên bản lúc đó; 04/11/2026 có 21 kiểm thử backend OTP, 227 JavaScript, 3 nhóm kiểm tra giao diện và 4 nhóm luồng trình duyệt OTP đạt. SMTP Gmail đã xác thực. Kiểm thử SMTP giả lập không chứng minh thư thật luôn vào hộp thư đến; chất lượng AI và hiệu quả học tập cần đánh giá riêng.

---

# Tài liệu kỹ thuật và phân tích đề tài

## 1. Giới thiệu đề tài

### 1.1. Bài toán

Sinh viên thường phải học từ nhiều tài liệu dài, khó tổng hợp ý chính và xác định nội dung chưa nắm vững. Việc tự viết câu hỏi, chuẩn bị thẻ ghi nhớ và xây dựng sơ đồ kiến thức mất nhiều thời gian. Giảng viên cũng cần công cụ chuyển tài liệu thành dàn ý và bài giảng.

Study Assistant là ứng dụng web kết hợp quản lý tài liệu với mô hình ngôn ngữ lớn, truy xuất thông tin và theo dõi hoạt động học tập. Người dùng tải tài liệu lên, đọc nội dung, hỏi đáp có nguồn, tạo tóm tắt, sơ đồ kiến thức, flashcard và bài kiểm tra. Dữ liệu học tập được dùng để ước lượng mức độ nắm vững khái niệm và đề xuất nội dung nên học tiếp.

### 1.2. Mục tiêu

- Tổ chức tài liệu theo người dùng và notebook.
- Trích xuất PDF, DOCX, PPTX để phục vụ xử lý AI.
- Sinh nội dung học tập có liên kết tới bằng chứng trong tài liệu.
- Hỗ trợ tự kiểm tra, ôn tập giãn cách và theo dõi kiến thức còn yếu.
- Hỗ trợ giảng viên tạo, duyệt, chỉnh sửa và xuất bài giảng PowerPoint.
- Cung cấp công cụ quản trị người dùng, tài liệu và gói dịch vụ.

### 1.3. Phạm vi và đóng góp

Đây là hệ thống tích hợp AI ở cấp ứng dụng. Mã nguồn sử dụng mô hình ngôn ngữ và embedding qua API, không thể hiện quy trình tự huấn luyện hoặc fine-tune mô hình nền tảng. Đóng góp triển khai tập trung vào pipeline tài liệu, hybrid retrieval, kiểm tra nguồn, đồ thị kiến thức, quy tắc cá nhân hóa, lịch ôn và quy trình nghiệp vụ.

## 2. Tác nhân và chức năng

### 2.1. Học viên — STUDENT

- Đăng ký, đăng nhập bằng username hoặc email, đăng xuất.
- Tạo và xem notebook; quản lý tài liệu của tài khoản.
- Tải PDF, DOCX, PPTX; theo dõi trạng thái và thử xử lý lại khi lỗi.
- Đọc PDF bằng PDF.js; đọc văn bản trích xuất của DOCX/PPTX.
- Sinh tóm tắt gồm tổng quan, ý chính, thuật ngữ, kết luận và nguồn.
- Hỏi đáp dựa trên tài liệu trong notebook, có tham chiếu nguồn khi được xác thực.
- Xem sơ đồ kiến thức, thu/mở nhánh, chi tiết nút, zoom và pan.
- Sinh và học flashcard; backend có review và lập lịch FSRS.
- Sinh quiz, tạo lượt làm bài, nộp bài và nhận điểm từ server.
- Xem tiến độ; backend cung cấp mastery, đề xuất học thích nghi và exam readiness.

Các API cá nhân hóa và tutor tồn tại ở backend. Cần kiểm tra từng màn hình khi đánh giá mức độ tích hợp; không mặc định mọi API đều có một trang giao diện riêng.

### 2.2. Giảng viên — TEACHER

Giảng viên sử dụng các chức năng tài liệu dùng chung và có thêm luồng bài giảng:

1. Tạo bộ bài giảng từ nguồn được chọn.
2. Yêu cầu AI sinh dàn ý theo job bất đồng bộ.
3. Xem các phiên bản và duyệt dàn ý phù hợp.
4. Sinh slide từ dàn ý đã duyệt, theo dõi job, thử lại slide lỗi.
5. Chỉnh sửa slide/phần tử, quản lý hình ảnh của bộ bài giảng.
6. Xuất PPTX bằng Apache POI hoặc lưu trữ bộ bài giảng.

`TeacherAuthorizationService` cho phép TEACHER hoặc ADMIN sử dụng năng lực giảng viên; các dịch vụ tiếp tục kiểm tra quyền sở hữu tài nguyên.

### 2.3. Quản trị viên — ADMIN

- Xem dashboard tổng quan.
- Tạo, sửa, xóa người dùng; thay đổi vai trò, quản lý trạng thái; cấp gói có thời hạn qua đăng ký được duyệt.
- Khóa/mở hoặc đình chỉ tài khoản.
- Xem và xóa tài liệu hệ thống.
- Quản lý bảng giá qua API gói dịch vụ.
- Xem audit log và dữ liệu thanh toán theo chức năng đã triển khai.

### 2.4. Quota và thanh toán

Chính sách hiện tại dùng FREE, PRO, VIP. Hạn mức tháng, giá 1/3/12 tháng, cách gia hạn và hết hạn được mô tả tại mục 10 của hướng dẫn sử dụng. Bộ đếm `monthly_feature_usage` khóa ghi khi giữ/hoàn lượt; `SubscriptionService` xác định quyền từ đăng ký ACTIVE còn hạn. Legacy STUDENT/BASIC và PRO/PREMIUM được chuyển đổi một lần theo `BillingPolicyInitializer`.

Thanh toán tạo PENDING và cần phê duyệt/xác nhận hợp lệ để kích hoạt. Chưa mô tả đây là đối soát ngân hàng tự động hoàn chỉnh.

## 3. Công nghệ sử dụng

Các phiên bản sau lấy từ repository, không phải thông tin phiên bản mới nhất bên ngoài.

- **Java:** toolchain khai báo Java 17.
- **Spring Boot:** plugin 4.1.1 trong `build.gradle`.
- **Spring Web MVC:** API HTTP và phục vụ giao diện tĩnh.
- **Spring Data JPA/Hibernate:** ánh xạ và truy cập dữ liệu.
- **Bean Validation:** kiểm tra ràng buộc dữ liệu.
- **Spring Mail:** gửi OTP qua SMTP/STARTTLS.
- **Spring Security Crypto:** BCrypt; không có căn cứ mô tả hệ thống dùng JWT hoặc đầy đủ Spring Security filter chain.
- **MySQL:** dữ liệu nghiệp vụ và FULLTEXT; nên chuẩn bị MySQL 8.x khi triển khai.
- **Qdrant:** lưu và tìm kiếm vector embedding.
- **Gradle Wrapper:** distribution 9.5.1.
- **Apache PDFBox 3.0.3:** trích xuất PDF.
- **Apache POI 5.3.0:** đọc DOCX/PPTX và xuất PowerPoint.
- **FSRS Java 1.0.0:** lập lịch ôn flashcard.
- **Jackson, Lombok:** JSON và giảm mã lặp.
- **Frontend:** HTML, CSS, JavaScript thuần, Tailwind CSS CDN, Lucide Icons, PDF.js; sơ đồ dùng SVG renderer chung.
- **AI provider:** mặc định `gpt-4o-mini`; embedding cấu hình `text-embedding-3-small`, 1536 chiều.
- **Kiểm thử:** JUnit Platform, công cụ test Spring Boot, H2 và Node.js test runner.

Spring Boot phục vụ frontend trực tiếp; không có quy trình build React/Vue hoặc yêu cầu `npm install` trong cấu hình hiện có. Một số tài nguyên giao diện được tải từ CDN.

## 4. Kiến trúc và luồng xử lý

### 4.1. Kiến trúc tổng thể

Ứng dụng nguyên khối có phân lớp; flashcard, quiz, summary và presentation được tách thành các package nghiệp vụ.

```text
Trình duyệt: HTML / CSS / JavaScript
                |
        HTTP API + session
                |
       Controller / Authorization
                |
  Service / Domain / Retrieval / LLM Provider
        |              |              |
     MySQL          Qdrant      Dịch vụ AI ngoài
        |
  Metadata, chunks, nguồn, lịch sử học

Tài liệu và media -> storage_private trên máy chủ
```

- Controller nhận request và lấy người dùng từ session.
- Service/domain kiểm tra nghiệp vụ, quota, quyền và giao dịch.
- Repository truy vấn JPA hoặc SQL cần thiết.
- Retrieval phụ trách parse, chunk, index và truy xuất bằng chứng.
- Provider tách phần gọi AI khỏi nghiệp vụ.
- MySQL lưu dữ liệu nghiệp vụ và văn bản; Qdrant lưu vector phục vụ tìm kiếm ngữ nghĩa.

### 4.2. Luồng tài liệu

1. Xác thực phiên và kiểm tra quyền notebook nếu được gán.
2. Kiểm tra quota, tên file, phần mở rộng, MIME và kích thước.
3. Lưu file có UUID tại `storage_private/uploads`.
4. Trích xuất văn bản, lưu metadata và trạng thái PROCESSING.
5. Executor đọc tài liệu, chia chunk và tính SHA-256.
6. Lưu chunk theo phiên bản index ứng viên trong MySQL.
7. Gọi embedding provider và ghi vector vào Qdrant.
8. Kích hoạt index trong giao dịch, chuyển READY.
9. Thử sinh đồ thị kiến thức; lỗi graph không làm mất index đã kích hoạt thành công.

Giới hạn nghiệp vụ là **25 MiB/file**. Multipart được cấu hình 30MB/file và 32MB/request; không lấy ngưỡng multipart làm giới hạn upload được quảng bá.

Trạng thái chính gồm PROCESSING, READY, FAILED, DELETED. Index lần đầu lỗi dẫn đến FAILED; service cho thử lại tối đa ba lần. Re-index lỗi được thiết kế giữ phiên bản cũ. Xóa tài liệu đánh dấu DELETED, bỏ active index và dọn file/vector sau commit; chunk lịch sử được giữ để bảo toàn nguồn tham chiếu.

### 4.3. Tác vụ bất đồng bộ

`PresentationGenerationConfig` bật `@EnableAsync` và khai báo:

- `documentTaskExecutor`: core 2, tối đa 4 luồng, hàng đợi 100.
- `presentationTaskExecutor`: core 2, tối đa 4 luồng, hàng đợi 50.

Frontend polling trạng thái tài liệu/job. Helper dùng chung xử lý request trùng và phản hồi cũ khi người dùng chuyển tài liệu hoặc active index.

## 5. Các phương pháp AI và thuật toán

### 5.1. RAG và hybrid retrieval

RAG đưa bằng chứng được truy xuất từ tài liệu vào đầu vào mô hình ngôn ngữ.

```text
Tài liệu -> Parse -> Chunk -> Embedding -> Index
Câu hỏi -> Dense search + Keyword search -> RRF
       -> Chọn bằng chứng -> Bổ sung ngữ cảnh đồ thị
       -> LLM -> Kiểm tra citation -> Phản hồi
```

**Parse:** PDF giữ metadata theo trang; PPTX theo số slide; DOCX theo đoạn/bảng và tiêu đề nhận diện được. Quy trình này không cung cấp số trang in đáng tin cậy cho DOCX.

**Chunking:** mục tiêu mặc định khoảng 600 token/chunk, overlap 80 token và ngưỡng xử lý câu quá dài 1000 token. Đây là tham số thuật toán; `SimpleTokenCounter` chỉ ước lượng `ceil(số từ × 1.33)`, không đếm bằng tokenizer thực của model.

**Truy xuất:** dense search dùng vector, keyword search dùng MySQL FULLTEXT, mặc định tối đa 30 kết quả mỗi nhánh. Phạm vi dữ liệu được lọc theo người dùng, notebook, READY và active index.

**RRF:** kết hợp thứ hạng của cùng một chunk từ hai nhánh:

```text
RRF(chunk) = Σ 1 / (k + rank_i(chunk))
k mặc định = 60
```

Sau fusion giữ tối đa 15 ứng viên, chọn tối đa 6 chunk theo ngân sách ngữ cảnh cấu hình 6000 token và hạn chế chunk liền kề trùng lặp.

**Điều kiện bằng chứng:** dense similarity đạt ngưỡng mặc định 0.3, hoặc keyword rank trong top 5, hoặc chunk xuất hiện ở cả hai nhánh. Đây là quy tắc lọc, không phải cam kết độ chính xác.

**Citation:** prompt yêu cầu nguồn `[S1]`, `[S2]`; hệ thống đối chiếu với các nguồn đã cấp. Thiếu bằng chứng thì trả thông báo thiếu thông tin. Nguồn hợp lệ chưa chứng minh mọi phát biểu đều đúng về ngữ nghĩa.

### 5.2. Tóm tắt theo Map–Reduce

`DocumentSummaryService` đọc chunk của active index, chia nhóm theo ngân sách, tóm tắt từng nhóm rồi gộp nhiều cấp nếu cần. Đầu ra gồm `overview`, `keyPoints`, `importantTerms`, `conclusion` và nguồn.

Backend kiểm tra schema, alias nguồn và lấy metadata trang/tài liệu từ dữ liệu đã lưu. Cache gắn với phiên bản index/content hash để tránh dùng kết quả cũ sau khi tài liệu thay đổi.

### 5.3. Đồ thị kiến thức và mind map

`AIConceptExtractor` và `AIConceptRelationExtractor` trích xuất khái niệm, quan hệ từ chunk. Các loại quan hệ gồm:

- PREREQUISITE_OF: kiến thức tiên quyết.
- PART_OF: bộ phận–tổng thể.
- IS_A: phân loại.
- RELATED_TO: liên quan.

Khái niệm/quan hệ gắn với bằng chứng; truy vấn phân biệt phạm vi document/notebook và phiên bản index. Giao diện dùng graph để hiển thị sơ đồ. `GraphRagContextService` có thể bổ sung ngữ cảnh đồ thị trong ngân sách token cho phép.

Graph lưu bằng bảng quan hệ MySQL, không có căn cứ mô tả dự án sử dụng Neo4j.

### 5.4. Quiz và chấm điểm server

Backend khai báo hai chế độ PRACTICE/EXAM và các kiểu MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE, SHORT_ANSWER. Cần kiểm tra giao diện khi đánh giá khả năng thao tác từng loại.

Luồng chính: sinh từ chunk hoạt động → tạo attempt → lưu snapshot câu hỏi/lựa chọn → nộp bằng submission token → chấm điểm → lưu kết quả và bằng chứng học. EXAM kiểm tra hạn giờ nếu được cấu hình. Đáp án ngắn được chuẩn hóa Unicode NFKC, khoảng trắng và chữ thường trước khi so với đáp án chấp nhận.

Điểm bằng điểm đạt được/tổng điểm có thể đạt, quy về thang 100. Idempotency giúp request nộp lại không tạo kết quả/cập nhật mastery trùng. Route submit legacy đã bị vô hiệu hóa; dùng attempt V2.

### 5.5. Flashcard và FSRS

Flashcard được sinh từ chunk active index, kiểm tra dữ liệu và lưu nguồn. Review nhận AGAIN, HARD, GOOD, EASY; thư viện FSRS tính trạng thái tiếp theo, stability, difficulty và thời điểm đến hạn.

Adapter ánh xạ các trạng thái LEARNING, REVIEW, RELEARNING. Desired retention mặc định 0.90 là tham số lập lịch, không phải kết quả thực nghiệm về tỷ lệ ghi nhớ.

### 5.6. Mastery

`MasteryHeuristicCalculator` dùng công thức:

```text
Bằng chứng đầu tiên: mastery = normalizedScore
Các lần sau: mastery_mới = 0.70 × mastery_cũ + 0.30 × normalizedScore
```

Điểm thuộc [0, 1]. `MasteryUpdateEngine` kiểm tra quyền, nguồn hoạt động, khóa ghi và idempotency trước khi lưu event/snapshot. Đây là heuristic, chưa phải mô hình knowledge tracing được huấn luyện và hiệu chuẩn bằng dữ liệu thực nghiệm.

### 5.7. Đề xuất học thích nghi và tutor

`AdaptiveRecommendationService` loại khái niệm đã nắm vững, kiểm tra tiên quyết rồi xếp ưu tiên. Mặc định điều kiện nắm vững là mastery ít nhất 0.80 và có ít nhất hai bằng chứng. Nhóm đã học nhưng chưa vững được ưu tiên trước nhóm chưa học; trong nhóm đã học, mastery thấp được ưu tiên hơn.

Tarjan tìm thành phần liên thông mạnh để vòng phụ thuộc nội bộ không chặn toàn bộ nhóm. Tutor hỗ trợ AUTO, ASK_QUESTION, GIVE_HINT, EXPLAIN và có thể chuyển trọng tâm sang tiên quyết chưa đạt.

### 5.8. Exam readiness

`ExamReadinessCalculator` tính:

```text
Readiness = round(100 × (0.50 × ExamPerformance
                      + 0.35 × MeanMastery
                      + 0.15 × Coverage))
```

ExamPerformance dùng tối đa ba lượt EXAM hợp lệ gần nhất, trọng số 0.50/0.30/0.20 và chuẩn hóa theo số lượt có. Không có lượt thi hợp lệ hoặc không có concept đủ điều kiện thì trả thiếu bằng chứng, không trả điểm readiness.

Chỉ số này hỗ trợ học tập, chưa được chứng minh là xác suất đạt kỳ thi thực tế.

## 6. Tổ chức mã nguồn

```text
study-assistant/
├── build.gradle, settings.gradle
├── gradlew, gradlew.bat, gradle/wrapper/
├── study_assistant.sql
├── docs/                              # Báo cáo kỹ thuật, SQL bổ trợ
├── src/main/java/com/example/studyassistant/
│   ├── StudyAssistantApplication.java # Điểm khởi động
│   ├── config/                        # AI, policy, khởi tạo dữ liệu
│   ├── controller/                    # API nghiệp vụ và tutor
│   ├── domain/
│   │   ├── summary/                   # Tóm tắt có nguồn
│   │   ├── flashcard/                 # Sinh thẻ, hàng đợi, FSRS
│   │   ├── quiz/                      # Sinh quiz, attempt, submission
│   │   ├── examreadiness/             # Chỉ số sẵn sàng thi
│   │   └── presentation/              # Dàn ý, slide, media, PPTX
│   ├── dto/                           # Dữ liệu trao đổi
│   ├── entity/                        # Thực thể JPA
│   ├── repository/                    # Truy vấn dữ liệu
│   ├── retrieval/                     # Parse, chunk, embedding, RAG
│   ├── llm/                           # Abstraction và provider LLM
│   ├── security/                      # Kiểm tra năng lực giảng viên
│   ├── service/                       # Graph, mastery, quota, tutor...
│   ├── exception/
│   └── util/
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/                  # SQL V1–V13
│   └── static/                        # HTML, CSS, JavaScript
├── src/test/java/                     # Unit/integration Java
├── src/test/js/                       # Kiểm thử .cjs
├── src/test/resources/                # H2/MySQL profile và SQL
├── storage_private/                   # Tài liệu và media riêng tư
├── uploads/                           # Dữ liệu upload cũ
└── build/                             # Sản phẩm build
```

Số lượng tệp thay đổi theo các lần bổ sung; tham khảo trực tiếp cây mã nguồn thay vì dùng số đếm cũ làm số chức năng.

Các trang tiêu biểu: `index.html`, `dashboard.html`, `documents.html`, `upload.html`, `detail.html`, `viewer.html`, `reader.html`, `map.html`, `flashcards.html`, `quiz.html`, `result.html`, `progress.html`, `admin.html`, các trang `admin-*.html`, `teacher-presentations.html`, `teacher-deck.html`, `teacher-slide-editor.html`.

## 7. Thiết kế dữ liệu

### 7.1. Các nhóm thực thể

- **Tài khoản:** users, notebooks, plan_features, user_daily_usage.
- **Tài liệu/nguồn:** documents, document_chunks, document_summaries, document_summary_sources.
- **Graph:** concepts, concept_sources, concept_relations, concept_relation_sources.
- **Hội thoại:** conversations, chat_messages, tutor_sessions.
- **Ôn tập:** flashcards, flashcard_sources, flashcard_review_states, flashcard_review_events.
- **Quiz:** quizzes, quiz_questions, quiz_question_options, quiz_question_accepted_answers, quiz_question_concepts, quiz_question_sources, quiz_attempts, quiz_attempt_answers và các bảng snapshot liên quan.
- **Học tập:** study_progress, student_learning_events, student_concept_mastery.
- **Bài giảng:** presentation_decks, presentation_outline_revisions, presentation_generation_jobs, presentation_slides, presentation_slide_elements, presentation_slide_sources, presentation_media_assets.
- **Dịch vụ/quản trị:** pricing_plans, payments, subscriptions, audit_logs, system_configs.

### 7.2. Quan hệ chính

Một user sở hữu nhiều notebook/document. Document có thể chưa gán notebook, nhưng tính năng dựa theo notebook cần điều kiện phù hợp. Document có nhiều chunk và phiên bản index; active index xác định phiên bản được dùng khi truy xuất.

Concept gắn chunk qua concept_sources; quan hệ giữa concept có nguồn riêng. Quiz/flashcard/slide cũng có dữ liệu nguồn để truy vết. Attempt lưu lịch sử làm bài; mastery và learning event gắn người dùng với khái niệm.

### 7.3. SQL và migration

`study_assistant.sql` là script nền cũ, chưa phản ánh đầy đủ mô hình hiện tại. Các script V1–V13 bổ sung RAG, graph, provenance, mastery, quiz idempotency, tutor, FSRS, exam intelligence, presentation, summary và graph theo tài liệu.

V14 bổ sung chính sách gói; V15 bổ sung OTP. **Build hiện không khai báo Flyway.** Không mặc định V1–V13 tự chạy khi khởi động. `spring.jpa.hibernate.ddl-auto=update` không thay thế toàn bộ ràng buộc, chuyển đổi dữ liệu và FULLTEXT index trong SQL.

Không chạy toàn bộ migration tùy tiện lên database có sẵn: nhiều ALTER chỉ nên áp dụng một lần và có điều kiện tiên quyết. V13 là script sửa tương thích có ghi chú cần kiểm tra trên host.

## 8. Các API tiêu biểu

Các route được đối chiếu từ annotation controller; danh sách này không thay thế đặc tả đầy đủ request/response.

### 8.1. Tài khoản, notebook, tài liệu

- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`.
- `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `GET /api/auth/me`, `PATCH /api/auth/profile`.
- `GET /api/notebooks`, `POST /api/notebooks`, `GET /api/notebooks/{id}`.
- `POST /api/documents/upload`, `GET /api/documents`.
- `GET /api/documents/{id}`, `GET /api/documents/{id}/status`.
- `POST /api/documents/{id}/retry`, `DELETE /api/documents/{id}`.
- `GET /api/documents/{id}/view`, `GET /api/documents/{id}/content`.
- `POST /api/documents/{id}/notebook`.

### 8.2. AI và kiến thức

- `POST /api/ai/chat`.
- `GET /api/documents/{docId}/summary` — đọc cache.
- `POST /api/documents/{docId}/summary/generate` — sinh tóm tắt.
- `GET /api/documents/{docId}/mind-map`.
- `POST /api/documents/{docId}/mind-map/generate`.
- `GET /api/notebooks/{notebookId}/knowledge-graph`.
- `GET /api/notebooks/{notebookId}/mastery`.
- `GET /api/notebooks/{notebookId}/recommendations`.
- `GET /api/notebooks/{notebookId}/exam-readiness`.
- `POST /api/tutor/conversations/{conversationId}/messages`.

### 8.3. Flashcard và quiz

- `POST /api/documents/{documentId}/flashcards/generate`.
- `GET /api/notebooks/{notebookId}/flashcards/queue`.
- `POST /api/flashcards/{flashcardId}/reviews`.
- `POST /api/quizzes/generate`.
- `GET /api/quizzes/document/{docId}` — đọc quiz đã lưu.
- `POST /api/quizzes/{quizId}/attempts`.
- `POST /api/quiz-attempts/{attemptId}/submit`.
- `GET /api/quizzes/{quizId}/answers` — kiểm tra đã nộp bài.

### 8.4. Bài giảng

Prefix chung: `/api/teacher/presentations`.

- `POST`/`GET` tại prefix: tạo/liệt kê deck.
- `GET`/`PATCH /{deckId}`: đọc/sửa thông tin.
- `POST /{deckId}/outlines/generate`.
- `GET /{deckId}/outlines`, `POST /{deckId}/outlines/{revisionId}/approve`.
- `POST /{deckId}/slides/generate`.
- `GET /{deckId}/jobs/{jobId}`.
- `GET /{deckId}/slides`, `PATCH /{deckId}/slides/{slideId}`.
- `POST`/`PATCH`/`DELETE` tại các route phần tử bên dưới slide.
- `POST /{deckId}/slides/{slideId}/retry`.
- `GET /{deckId}/export/pptx`.
- `POST`/`GET /{deckId}/media`; route con đọc, tải nội dung và xóa media.

### 8.5. Quản trị và dịch vụ

- `GET /api/admin/dashboard`, `GET`/`POST /api/admin/users`.
- `PUT`/`DELETE /api/admin/users/{userId}`.
- `POST /api/admin/users/{userId}/status`.
- `GET /api/admin/documents`, `DELETE /api/admin/documents/{id}`.
- `GET /api/admin/audit-logs`.
- `GET /api/plans`, `GET /api/plans/quota`.
- `POST /api/payments/checkout`, `POST /api/payments/webhook/verify`.
- `GET /api/payments/history`, `POST /api/subscription/cancel-auto-renew`.

Xác thực dùng HTTP session, `userId` lưu phía server. Khi thử API cần giữ cookie đăng nhập; header tự đặt `X-User-Id` không thay thế phiên xác thực.

## 9. Cài đặt và chạy dự án

### 9.1. Chuẩn bị

- JDK phù hợp toolchain Java 17 khai báo trong dự án.
- MySQL và database riêng cho ứng dụng.
- Qdrant tại cổng HTTP 6333 hoặc URL tương ứng.
- API key cho provider AI đang dùng.
- Internet để tải dependency Gradle, gọi AI và tải CDN.
- Node.js nếu chạy test JavaScript.

Các lệnh sau là hướng dẫn cài đặt; cấu hình thực tế phụ thuộc môi trường triển khai.

### 9.2. MySQL

Với môi trường thử nghiệm mới, tạo database trống:

```sql
CREATE DATABASE study_assistant
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Cấu hình `ddl-auto=update` cho phép Hibernate tạo/cập nhật bảng ánh xạ khi khởi động. Khi đã có bảng `document_chunks`, kiểm tra FULLTEXT:

```sql
USE study_assistant;
SHOW INDEX FROM document_chunks;
```

Nếu chưa có FULLTEXT trên content, bổ sung một lần:

```sql
CREATE FULLTEXT INDEX ft_chunk_content ON document_chunks (content);
```

Nhánh keyword retrieval cần index này. Database đã tồn tại phải đối chiếu với entity và migration trước khi sử dụng. Quy trình trên không chứng nhận đã áp dụng mọi ràng buộc SQL mới; script nền cũ không phải bản schema hoàn chỉnh.

### 9.3. Cấu hình PowerShell

Chạy tại thư mục gốc dự án:

```powershell
Set-Location -LiteralPath 'D:\DU LIEU\DO AN TRI TUE NHAN TAO\study-assistant'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/study_assistant?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh'
$env:SPRING_DATASOURCE_USERNAME = 'study_user'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-database>'
$env:OPENAI_API_KEY = '<api-key-cua-ban>'
$env:QDRANT_URL = 'http://localhost:6333'
$env:PAYMENT_WEBHOOK_SECRET = '<chuoi-bi-mat-rieng>'
```

Nếu Qdrant yêu cầu key, đặt thêm `QDRANT_API_KEY`. `PRESENTATION_MEDIA_STORAGE_ROOT` đổi vị trí lưu media. Thay các giá trị `<...>` bằng cấu hình thực. Không đưa key/mật khẩu thật vào bản báo cáo công khai.

### 9.4. Qdrant

Nếu đã có Docker, có thể tạo Qdrant thử nghiệm:

```powershell
docker run -d --name study-assistant-qdrant -p 6333:6333 -v study_assistant_qdrant:/qdrant/storage qdrant/qdrant
```

Ứng dụng có bước bảo đảm collection phù hợp embedding dimension. Khi đổi model/số chiều, cần kiểm tra collection và lập lại index, tránh dùng lẫn vector khác cấu hình.

### 9.5. Build và khởi động

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Đóng gói bằng ` .\gradlew.bat bootJar`; JAR nằm trong `build/libs`. Chạy `java -jar` với tên JAR thực tế và giữ các biến môi trường trong phiên chạy.

Linux/macOS dùng `./gradlew` thay `gradlew.bat`. Nếu lỗi công cụ, kiểm tra `java -version`, `gradlew --version` và khả năng tương thích của môi trường với phiên bản khai báo.

### 9.6. Truy cập và tài khoản

Mở `http://localhost:8080/` hoặc `http://localhost:8080/index.html`. Đăng nhập trước khi mở tài liệu hoặc trang quản trị. Một số trang cần ID từ luồng điều hướng; nên đi từ dashboard/danh sách.

`DataInitializer` tạo tài khoản nếu chưa có username admin:

- Username: `admin`.
- Email: `admin@study.ai`.
- Mật khẩu khởi tạo: `Admin123!`.
- Vai trò ADMIN, quyền VIP nội bộ.

Chỉ dùng mặc định trong môi trường thử nghiệm và thay mật khẩu trước triển khai. Học viên tự đăng ký; quản trị viên có thể tạo TEACHER để demo bài giảng.

### 9.7. Lỗi thường gặp

- Không kết nối MySQL: kiểm tra service, database, thông tin và quyền tài khoản.
- Tài liệu FAILED: kiểm tra văn bản trích xuất, embedding provider, API key, Qdrant và log.
- Không sinh AI: cần READY và active index; kiểm tra quota, mạng và provider.
- Chat thiếu notebook: gán tài liệu vào notebook rồi thử lại.
- Keyword retrieval lỗi: kiểm tra FULLTEXT bằng `SHOW INDEX FROM document_chunks`.
- 401/403: kiểm tra cookie, vai trò và quyền sở hữu.
- Icon/PDF viewer tải lỗi: kiểm tra CDN tham chiếu trong HTML.

## 10. Kịch bản trình diễn

### 10.1. Học viên

1. Đăng ký, đăng nhập và tạo notebook.
2. Upload PDF có lớp văn bản, nội dung ngắn và dễ kiểm chứng.
3. Chờ READY, mở trang đọc và đối chiếu nội dung.
4. Sinh tóm tắt; kiểm tra các ý chính với nguồn gốc.
5. Mở mind map, thử thu/mở nhánh và xem khái niệm.
6. Đặt câu hỏi có đáp án trong tài liệu, kiểm tra citation.
7. Đặt câu hỏi ngoài tài liệu, quan sát xử lý thiếu bằng chứng.
8. Sinh flashcard, lật thẻ và review.
9. Sinh quiz, làm bài, nộp và xem điểm. Kiểm tra nộp trùng trong môi trường thử nghiệm.
10. Xem tiến độ; chuẩn bị đủ concept và lượt thi hợp lệ nếu minh họa mastery/readiness.

### 10.2. Giảng viên

TEACHER đăng nhập → tạo deck → chọn nguồn → sinh outline → duyệt outline → sinh slide → chỉnh sửa → xuất PPTX. Mở PPTX để kiểm tra nội dung, hình ảnh và bố cục thực tế.

### 10.3. Quản trị

ADMIN đăng nhập → xem dashboard → tạo/sửa tài khoản → đổi vai trò/khóa tài khoản thử nghiệm → kiểm tra quyền và audit log tương ứng.

## 11. Kiểm thử và đánh giá

### 11.1. Kết quả xác nhận theo đợt

Xem mục 14 của hướng dẫn sử dụng để biết kết quả ngày 01/11 và 04/11/2026. Các lần chạy sử dụng phạm vi và môi trường khác nhau. Không cộng số test lịch sử thành một lượt xác nhận mới. Không có số liệu độ chính xác AI hoặc hiệu quả học tập đã được thực nghiệm để công bố.

### 11.2. Kiểm thử backend

```powershell
.\gradlew.bat test
.\gradlew.bat mysqlIntegrationTest
```

Task `test` loại tag Integration và E2E. Task `mysqlIntegrationTest` chạy tag Integration với profile integration, database `studyassistant_rag_test`, `ddl-auto=none`; cần chuẩn bị schema riêng trước. Các biến cấu hình gồm `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, `TEST_QDRANT_API_KEY`.

Không dùng database quan trọng để chạy test. H2 hỗ trợ kiểm thử cục bộ nhưng không xác nhận MySQL FULLTEXT, khóa hoặc cạnh tranh ghi trên MySQL thật.

### 11.3. Nội dung nên đo trong báo cáo

- Thời gian xử lý theo loại file, dung lượng và số trang.
- Độ trễ hỏi đáp, tóm tắt và sinh nội dung.
- Retrieval Recall@k với câu hỏi có đánh dấu đoạn nguồn đúng.
- Tỷ lệ citation đúng nguồn và tỷ lệ phát biểu được bằng chứng hỗ trợ.
- Chất lượng quiz/flashcard qua đánh giá chuyên môn.
- Quyền truy cập chéo tài khoản, retry và request trùng.
- Chi phí AI trên mỗi tài liệu/lượt hỏi nếu thu được dữ liệu sử dụng.

README không đưa số liệu độ chính xác, chi phí hoặc hiệu quả học tập chưa được đo. Khi bổ sung, cần ghi tập dữ liệu, môi trường, phương pháp và số mẫu.

## 12. Giới hạn và hướng phát triển

### 12.1. Giới hạn từ mã nguồn

1. **Phạm vi chat:** `/api/ai/chat` lấy notebook từ tài liệu đầu tiên trong docIds; chưa truyền cả danh sách chọn để giới hạn retrieval. Câu trả lời có thể dùng các tài liệu khác được phép truy cập trong cùng notebook. Tài liệu chưa gán notebook chưa dùng được luồng này.
2. **OCR:** parser đọc lớp văn bản; chưa có pipeline OCR cho PDF scan/ảnh.
3. **Số trang:** `DocumentService` còn gán totalPages ngẫu nhiên; không dùng trường này làm số liệu thực nghiệm. Metadata trang chunk PDF/PPTX được lấy từ parser, khác với totalPages.
4. **Quên mật khẩu:** đã có SMTP OTP, hash mã, expiry, một lần sử dụng và throttling; khả năng nhận thư thật phụ thuộc cấu hình SMTP/hộp thư.
5. **Bảo mật:** có BCrypt, session, kiểm tra quyền và xử lý nội dung AI ở nhiều luồng; vẫn cần đánh giá session/cookie, CSRF, CORS, DTO và lỗi trả về trước triển khai. Cấu hình còn chứa thông tin bí mật mẫu và tài khoản mặc định.
6. **Thanh toán:** callback so header với shared secret, chưa phải chữ ký HMAC của nội dung. Chưa đủ cơ sở khẳng định đối soát ngân hàng/cổng thanh toán thật.
7. **Schema:** script nền, JPA update và migration SQL cùng tồn tại; build chưa khai báo cơ chế quản lý migration tự động.
8. **Quota upload:** hiện dùng bộ đếm theo tháng Việt Nam, có giữ/hoàn lượt. Dung lượng lưu trữ là giới hạn riêng.
9. **Độ tin cậy AI:** schema/citation kiểm soát cấu trúc và nguồn, không loại bỏ mọi suy diễn sai. Token counter chỉ ước lượng theo từ.
10. **Vận hành:** tệp lưu cục bộ và UI dùng CDN; cần bổ sung sao lưu, triển khai nhiều instance và quản lý chi phí nếu mở rộng.

### 12.2. Hướng phát triển

- Giới hạn retrieval đúng tài liệu đã chọn; hỗ trợ chat tài liệu chưa gán notebook.
- Bổ sung OCR và tính tổng số trang thực từ parser.
- Tự động hóa migration, chuẩn hóa quy trình khởi tạo môi trường.
- Cải thiện vận hành email, bảo mật phiên và thanh toán xác minh nội dung callback.
- Dùng tokenizer phù hợp, theo dõi token/cost, kiểm soát timeout khi gọi AI.
- Đánh giá retrieval và tính đúng của phát biểu; cân nhắc reranking sau khi đo.
- Kiểm định mastery/readiness bằng dữ liệu học thực tế.
- Bổ sung kiểm thử trình duyệt, tải và PPTX trên nhiều phần mềm.

## 13. Gợi ý triển khai báo cáo cuối kỳ

### Chương 1. Tổng quan

Trình bày lý do chọn đề tài, bài toán, tác nhân, mục tiêu và phạm vi. Dùng mục 1–2 làm cơ sở; bổ sung trường, môn học, giảng viên và thành viên nhóm theo thực tế.

### Chương 2. Cơ sở lý thuyết

Giải thích LLM, embedding, RAG, hybrid retrieval, RRF, knowledge graph và FSRS. Phân biệt phần dùng thư viện với thuật toán/quy trình nhóm trực tiếp thiết kế. Tài liệu học thuật và tài liệu chính thức cần được tìm, đọc, trích dẫn riêng; README không thay thế danh mục tham khảo.

### Chương 3. Phân tích và thiết kế

Xây dựng use case STUDENT/TEACHER/ADMIN và yêu cầu phi chức năng. Vẽ ERD từ entity/schema thật, sơ đồ kiến trúc và sequence diagram cho upload–index, RAG, nộp quiz, sinh bài giảng. Dùng mục 4, 6, 7 và 8 để xác định thành phần.

### Chương 4. Cài đặt

Mô tả môi trường, cấu trúc code và pipeline ở mục 5. Bổ sung ảnh chụp thật của từng vai trò, đầu vào, thao tác và kết quả. Không dùng mockup làm bằng chứng ứng dụng đã chạy.

### Chương 5. Thử nghiệm và đánh giá

Chuẩn bị tài liệu/câu hỏi có đáp án và nguồn kiểm chứng. Ghi test thực chạy, chất lượng, thời gian, chi phí và ca lỗi. Phân biệt mock/H2, integration dịch vụ thật và kiểm thử trình duyệt.

### Chương 6. Kết luận

Trình bày chức năng đã xây dựng, phần đã kiểm chứng và hạn chế. Gắn hướng phát triển với vấn đề cụ thể; không khẳng định hiệu quả giáo dục khi chưa có thử nghiệm người học.

### Đoạn giới thiệu tham khảo

> Đề tài xây dựng hệ thống hỗ trợ học tập trên nền web, kết hợp quản lý tài liệu với mô hình ngôn ngữ lớn và kỹ thuật truy xuất tăng cường sinh. Tài liệu PDF, DOCX và PPTX được trích xuất, chia thành các đoạn nội dung và lập chỉ mục trong cơ sở dữ liệu quan hệ cùng kho vector. Hệ thống hỗ trợ hỏi đáp có nguồn, tóm tắt, biểu diễn kiến thức, sinh câu hỏi và flashcard. Cơ chế theo dõi bằng chứng học tập, ước lượng mức độ nắm vững và ôn tập giãn cách tạo nền tảng hỗ trợ học tập cá nhân hóa. Với giảng viên, ứng dụng cung cấp quy trình tạo dàn ý, sinh/chỉnh sửa slide và xuất bài giảng PowerPoint.

## 14. Các tệp mã nguồn tham chiếu

Đường dẫn Java viết ngắn dưới đây nằm trong `src/main/java/com/example/studyassistant/`.

- **Công nghệ/build:** `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`.
- **Cấu hình:** `src/main/resources/application.properties`.
- **Upload/trạng thái:** `service/DocumentService.java`.
- **Parse/chunk/index:** `retrieval/service/DocumentParsingService.java`, `ChunkingService.java`, `DocumentIndexingService.java`.
- **Hybrid search:** `retrieval/service/RetrievalService.java`, `retrieval/repository/DocumentChunkSearchRepository.java`.
- **Câu trả lời/nguồn:** `retrieval/service/GroundedAnswerService.java`, `GroundedContextService.java`, `GroundedCitationService.java`.
- **Provider:** `llm/OpenAiLlmProvider.java`, `retrieval/service/OpenAiEmbeddingProvider.java`, `QdrantVectorStore.java`.
- **Summary:** `domain/summary/DocumentSummaryService.java`.
- **Graph:** `service/DocumentKnowledgeGraphGenerationService.java`, `AIConceptExtractor.java`, `AIConceptRelationExtractor.java`, `KnowledgeGraphQueryService.java`.
- **Mastery:** `service/MasteryHeuristicCalculator.java`, `MasteryUpdateEngine.java`.
- **Adaptive/tutor:** `service/adaptive/AdaptiveRecommendationService.java`, `service/tutor/TutorPolicyService.java`.
- **FSRS:** `domain/flashcard/FsrsFlashcardScheduler.java`, `FlashcardReviewService.java`.
- **Quiz:** `domain/quiz/generation/GroundedQuizGenerationService.java`, `domain/quiz/attempt/QuizAttemptStartService.java`, `domain/quiz/submission/QuizAttemptSubmissionService.java`.
- **Readiness:** `domain/examreadiness/ExamReadinessCalculator.java`.
- **Bài giảng:** `domain/presentation/generation/`, `media/`, `export/`.
- **UI dùng chung:** `src/main/resources/static/js/`, `auth-interceptor.js`, `citations.js`.
- **Schema:** `src/main/resources/db/migration/` và entity tương ứng.
- **Kiểm thử:** `src/test/java/`, `src/test/js/`, `src/test/resources/`.
- **Báo cáo cũ:** `docs/phase11-frontend-report.md`, `docs/phase10-2-mind-map-v2-report.md`, `docs/quiz-submission-host-fix-report.md`, `docs/slide-fixed-layout-verification.md`.

Thông tin thành viên, phân công, ảnh minh họa, số liệu thực nghiệm và tài liệu tham khảo cần bổ sung theo thực tế của nhóm trước khi nộp báo cáo.
