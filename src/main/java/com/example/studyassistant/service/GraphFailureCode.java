package com.example.studyassistant.service;

/** Public codes and messages; never serialize provider or database exception text. */
public enum GraphFailureCode {
    DOCUMENT_NOT_READY(409, "Tài liệu đang được xử lý hoặc chưa sẵn sàng."),
    NO_ACTIVE_INDEX(409, "Tài liệu chưa có chỉ mục hoạt động. Vui lòng xử lý lại tài liệu."),
    NO_ACTIVE_EVIDENCE(409, "Chỉ mục hiện tại chưa có đoạn tài liệu làm bằng chứng."),
    STALE_INDEX(409, "Tài liệu đã thay đổi. Vui lòng nạp lại sơ đồ."),
    NOT_FOUND(404, "Không tìm thấy tài liệu hoặc bạn không có quyền truy cập."),
    UNAUTHENTICATED(401, "Vui lòng đăng nhập lại."),
    GRAPH_TOO_LARGE(413, "Sơ đồ vượt giới hạn hiển thị. Hãy dùng tài liệu nhỏ hơn."),
    AI_SCHEMA_ERROR(422, "AI trả dữ liệu sơ đồ không hợp lệ. Vui lòng thử lại."),
    NO_CONCEPTS(422, "Chưa tìm thấy khái niệm có bằng chứng trong tài liệu này."),
    AI_PROVIDER_ERROR(502, "Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau."),
    GRAPH_PERSISTENCE_ERROR(500, "Không thể lưu sơ đồ. Cần kiểm tra cấu hình dữ liệu trên máy chủ."),
    PARTIAL_GRAPH(200, "Đã hiển thị các khái niệm có bằng chứng; một phần khái niệm hoặc quan hệ chưa tạo được.");

    public final int httpStatus;
    public final String message;
    GraphFailureCode(int httpStatus, String message) { this.httpStatus = httpStatus; this.message = message; }
}
