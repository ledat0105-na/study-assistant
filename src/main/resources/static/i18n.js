// Vietnamese Translation dictionary
const translations = {
  // Brand
  brand_sub: "Trung tâm Học tập",
  // Navigation
  nav_dashboard: "Bảng điều khiển",
  nav_documents: "Tài liệu của tôi",
  nav_map: "Bản đồ Kiến thức",
  nav_flashcards: "Thẻ ghi nhớ (Flashcard)",
  nav_quiz: "Trắc nghiệm AI",
  nav_progress: "Tiến độ học tập",
  nav_profile: "Thông tin cá nhân",
  nav_admin: "Quản trị viên",
  nav_logout: "Đăng xuất",
  nav_features: "Tính năng",
  nav_pricing: "Bảng giá",
  nav_faq: "Hỏi đáp",
  nav_signin: "Đăng nhập",
  nav_getstarted: "Đăng ký",
  
  // Landing page
  hero_title: "Chuyển Đổi Tài Liệu Phức Tạp Thành Sơ Đồ Kiến Thức Trực Quan",
  hero_desc: "Tải lên tài liệu PDF hoặc DOCX của bạn. AI tự động phân tích khái niệm, xây dựng sơ đồ chủ đề, tạo thẻ ghi nhớ và chuẩn bị các bài trắc nghiệm tùy chỉnh cho riêng bạn.",
  features_title: "Mọi thứ bạn cần để học tập thông minh hơn",
  features_desc: "Các mô-đun tối ưu bằng AI giúp tăng tốc độ ghi nhớ kiến thức của bạn",
  pricing_title: "Bảng giá đơn giản, minh bạch",
  pricing_sub: "Nhận quyền truy cập các công cụ học tập cao cấp ngay hôm nay",
  pricing_free_desc: "Phù hợp cho mục đích trải nghiệm",
  pricing_free_1: "Tải lên tối đa 3 tài liệu",
  pricing_free_2: "Sơ đồ kiến thức cơ bản",
  pricing_free_3: "10 bài trắc nghiệm mỗi tháng",
  pricing_pro_desc: "Tốt nhất cho người học tích cực",
  pricing_pro_1: "Tải lên tài liệu không giới hạn",
  pricing_pro_2: "Sơ đồ khái niệm phân cấp trực quan",
  pricing_pro_3: "Không giới hạn flashcard & quiz AI",
  pricing_pro_4: "Báo cáo phân tích tiến độ nâng cao",
  faq_title: "Câu hỏi thường gặp",
  faq_sub: "Mọi câu hỏi liên quan đến hệ thống học tập Study AI",
  faq_q_1: "Sơ đồ kiến thức liên kết với PDF như thế nào?",
  faq_a_1: "Mỗi chủ đề do AI trích xuất có một chỉ số trang cụ thể. Khi nhấp vào nút chủ đề ở sơ đồ cây bên trái, trình xem PDF bên phải tự động cuộn đến trang đó để đọc ngay lập tức.",
  faq_q_2: "Hệ thống hỗ trợ những định dạng tệp nào?",
  faq_a_2: "Chúng tôi hỗ trợ tệp định dạng PDF, DOCX và PPTX. AI phân tích bố cục hình ảnh và văn bản để trích xuất các chương mục chính xác nhất.",

  // Auth
  auth_welcome: "Chào mừng trở lại",
  auth_create: "Tạo tài khoản mới",
  auth_have_acc: "Đã có tài khoản?",
  auth_name: "Họ và tên",
  auth_username: "Tên đăng nhập",
  auth_email: "Địa chỉ Email",
  auth_password: "Mật khẩu",
  auth_confirm: "Xác nhận mật khẩu",
  auth_role: "Vai trò",
  auth_student: "Học sinh",
  auth_educator: "Giảng viên",
  auth_submit_login: "Đăng nhập",
  auth_submit_register: "Đăng ký",
  
  // Dashboard & Docs
  db_welcome: "Xin chào,",
  db_ready: "Hôm nay bạn muốn học gì mới?",
  db_upload_btn: "Tải lên tài liệu",
  db_total_docs: "Tổng số tài liệu",
  db_topics: "Chủ đề Kiến thức",
  db_flashcards: "Tổng số Flashcard",
  db_accuracy: "Tỉ lệ đúng trung bình",
  db_recent: "Tài liệu gần đây",
  db_view_all: "Xem tất cả",
  db_insights: "Gợi ý từ AI",
  db_tip_title: "Mẹo nhỏ hàng ngày:",
  db_tip_desc: "Tự kiểm tra bằng thẻ ghi nhớ (flashcard) hiệu quả hơn 150% so với việc chỉ đọc lại văn bản. Hãy thử tạo flashcard cho tài liệu của bạn ngay!",
  db_target: "Mục tiêu tuần này:",
  db_target_desc: "Đã học 10 trên 15 giờ tuần này",
  db_analyze: "Phân tích tiến độ",
  
  docs_title: "Tài liệu của tôi",
  docs_sub: "Quản lý và truy cập tất cả tài liệu nghiên cứu đã tải lên",
  docs_search: "Tìm kiếm tài liệu...",
  docs_upload_btn: "Tải tài liệu mới",
  docs_details: "Chi tiết",
  docs_read: "Đọc PDF",
  docs_map: "Bản đồ",
  docs_delete: "Xóa tài liệu",
  
  // Upload & Details
  upload_title: "Tải lên tài liệu",
  upload_sub: "Thêm tệp PDF, DOCX hoặc PPTX để tạo trung tâm học tập AI",
  upload_drag: "Kéo và thả tệp của bạn vào đây",
  upload_limit: "Hỗ trợ định dạng PDF, DOCX, hoặc PPTX tối đa 32MB",
  upload_select: "Chọn tệp tin",
  uploading: "Đang tải tệp lên...",
  upload_done: "Tải lên hoàn tất!",
  upload_done_sub: "AI của chúng tôi đã phân tích các chương và xây dựng Sơ đồ kiến thức của bạn!",
  upload_done_btn: "Đi tới Tài liệu",
  upload_ocr: "Nhận dạng chữ (OCR) & Phân tích",
  upload_ocr_desc: "Quét tài liệu học tập hoặc slide bài giảng. Trình phân tích AI của chúng tôi sẽ chuyển đổi cấu trúc thành văn bản có thể lập chỉ mục.",
  upload_auto: "Tự động xử lý",
  upload_auto_desc: "Hệ thống tự động tạo flashcard, bản tóm tắt, bộ câu hỏi trắc nghiệm và sơ đồ liên kết khái niệm.",
  
  // Detail
  detail_back: "Quay lại danh sách",
  detail_loading: "Đang tải tài liệu...",
  detail_summary: "Tóm tắt từ AI",
  detail_concepts: "Các chương & Chủ đề đã trích xuất",
  detail_toolkit: "Bộ công cụ học tập",
  detail_tool_read: "Đọc PDF & Trợ lý",
  detail_tool_read_sub: "Đọc song song với trợ lý AI hỗ trợ giải đáp",
  detail_tool_map: "Bản đồ Kiến thức",
  detail_tool_map_sub: "Khám phá mối liên kết trực quan giữa các khái niệm",
  detail_tool_flash: "Học Flashcard",
  detail_tool_flash_sub: "Kiểm tra trí nhớ với phương pháp gợi nhớ chủ động",
  detail_tool_quiz: "Trắc nghiệm AI",
  detail_tool_quiz_sub: "Kiểm tra kiến thức và xem lại câu trả lời",
  
  // Map
  map_title: "Bản đồ kiến thức & Đọc tài liệu",
  map_sub: "Điều hướng các trang tài liệu tự động thông qua bản đồ khái niệm",
  map_hierarchy: "Cấu trúc chủ đề",
  map_starts: "Bắt đầu ở Trang",
  map_concept_detail: "Chi tiết khái niệm hoạt động",
  map_select: "Chọn một chủ đề để bắt đầu",
  map_select_desc: "Nhấp vào bất kỳ chủ đề nào ở danh sách bên trái để di chuyển trình xem PDF đến vị trí trang cụ thể.",
  
  // Flashcard
  flash_title: "Học Flashcard",
  flash_sub: "Phương pháp gợi nhớ chủ động giúp tăng khả năng ghi nhớ kiến thức",
  flash_progress: "Tiến trình thẻ học",
  flash_front: "Thẻ khái niệm",
  flash_back: "Định nghĩa",
  flash_flip: "Nhấp để lật mặt thẻ",
  flash_flip_back: "Nhấp để lật lại",
  flash_prev: "Thẻ trước",
  flash_next: "Thẻ sau",
  
  // Quiz & Result
  quiz_title: "Bộ câu hỏi Trắc nghiệm AI",
  quiz_sub: "Kiểm tra mức độ thành thạo khái niệm dựa trên tài liệu đã tải lên",
  quiz_question: "Câu hỏi",
  quiz_prev: "Câu trước",
  quiz_next: "Câu sau",
  quiz_submit: "Nộp bài",
  
  result_title: "Đã hoàn thành Quiz!",
  result_sub: "Dưới đây là kết quả bài kiểm tra của bạn",
  result_score: "Điểm của bạn",
  result_correct: "Bạn đã trả lời đúng",
  result_total: "trên tổng số",
  result_breakdown: "Chi tiết câu hỏi",
  result_retry: "Làm lại Quiz",
  result_back: "Quay lại chi tiết",
  
  // Progress
  progress_title: "Tiến độ học tập",
  progress_sub: "Theo dõi chuỗi ngày học tập, cột mốc và kết quả làm trắc nghiệm",
  progress_hours: "Số giờ học (Tuần này)",
  progress_breakdown: "Chi tiết tiến độ từng chủ đề",
  progress_achievements: "Thành tựu đạt được",
  
  // Profile
  profile_title: "Hồ sơ cá nhân",
  profile_sub: "Quản lý thông tin đăng nhập, thông tin chi tiết và cài đặt ứng dụng",
  profile_info: "Thông tin cá nhân",
  profile_name: "Họ và tên",
  profile_email: "Địa chỉ Email",
  profile_role: "Vai trò người dùng",
  profile_save: "Lưu thay đổi",
  profile_change_pwd: "Đổi mật khẩu",
  profile_curr_pwd: "Mật khẩu hiện tại",
  profile_new_pwd: "Mật khẩu mới",
  
  // Admin
  admin_title: "Bảng quản trị",
  admin_sub: "Quản lý người dùng nền tảng, hạn ngạch tài liệu và kiểm duyệt hệ thống",
  admin_users: "Người dùng đăng ký",
  admin_docs: "Tệp tài liệu",
  admin_banned: "Người dùng bị khóa",
  admin_action: "Hành động",
  
  // Limits
  limit_modal_title: "Giới hạn gói Miễn phí đã hết!",
  limit_modal_desc: "Tài khoản của bạn đã đạt giới hạn tối đa tải lên tài liệu. Vui lòng nâng cấp lên gói Pro để mở khóa dung lượng không giới hạn.",
  limit_modal_btn: "Nâng cấp gói tài khoản",
  limit_modal_close: "Đóng lại"
};

function getTranslation(key) {
  return translations[key] || key;
}

function updateDOMTranslations() {
  document.querySelectorAll("[data-i18n]").forEach(elem => {
    const key = elem.getAttribute("data-i18n");
    elem.textContent = getTranslation(key);
  });
  
  document.querySelectorAll("[data-i18n-placeholder]").forEach(elem => {
    const key = elem.getAttribute("data-i18n-placeholder");
    elem.placeholder = getTranslation(key);
  });
}

// Automatically update on DOM Load
window.addEventListener("DOMContentLoaded", () => {
  updateDOMTranslations();
});
