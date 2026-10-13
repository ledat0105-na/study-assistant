# Phase 11 — Frontend consolidation + UX polish

Phạm vi: frontend vanilla HTML/JS. Giữ giao diện Aurora và các luồng đang hoạt động. Không sửa backend, migration, hoặc kết nối MySQL/OpenAI thật trong phase này.

## FILES_CHANGED

- `src/main/resources/static/{viewer,reader,map,documents,upload,detail,flashcards,quiz}.html`
- `src/main/resources/static/auth-interceptor.js`
- `src/main/resources/static/citations.js`
- `src/main/resources/static/css/feature-ui.css` (mới)
- `src/main/resources/static/js/{api-client,ui-errors,document-summary,document-mind-map,mind-map-renderer}.js`
- `src/main/resources/static/js/{document-ui,document-features,summary-view}.js` (mới)
- `src/test/js/{viewer-generation,document-delete,document-summary}.test.cjs`
- `src/test/js/phase11-core.test.cjs` (mới)
- Báo cáo này.

## LEGACY_FRONTEND_PATHS_REMOVED

- Bỏ `/api/topics/document/{id}` khỏi trang chi tiết; đọc Knowledge Graph V2.
- Không còn `/api/ai/summary` trong frontend.
- Bỏ nộp quiz bằng `/api/quizzes/{id}/submit`; sử dụng API attempt V2 và điểm do server trả.
- Các GET `/api/flashcards/document/{id}` và `/api/quizzes/document/{id}` chỉ còn trong helper chung để đọc dữ liệu đã lưu. Đây là các API đọc tương thích hiện có của backend; không sinh dữ liệu bằng GET. Không tạo route GET mới không tồn tại.

## SUMMARY_UI

GET summary cache; POST generate khi chưa có cache và tài liệu READY với active index. Render Tổng quan, Ý chính, Thuật ngữ quan trọng, Kết luận và Nguồn tham chiếu; fallback `summary`. Khóa request theo tài liệu/phiên bản; kết quả phiên bản hoặc lựa chọn cũ không ghi đè UI mới.

## MIND_MAP_UI

Viewer và trang map dùng cùng SVG renderer và loader V2. Quan hệ PART_OF/IS_A đặt đúng hướng cha/con; có thu/mở nhánh, chi tiết nút, zoom/pan và retry. Hiển thị nguồn/trang nếu API có metadata. DTO hiện chưa có vị trí nguồn cho từng nút: hiển thị số nguồn và thông báo thiếu trang, không giả định trang 1.

## CHAT_UI

Chỉ dùng `/api/ai/chat`, giữ cơ chế grounded hiện có; thêm chỉ báo nguồn đang chọn, trạng thái đang trả lời, retry và citations an toàn. Thiếu bằng chứng hoặc không có citations thì hiển thị thông báo thiếu bằng chứng.

Giới hạn backend hiện tại: endpoint này cần notebook của tài liệu đầu tiên và retrieval trong notebook đó; không giới hạn retrieval chính xác theo mọi document được chọn. Tài liệu không thuộc notebook nhận thông báo phù hợp. Không fallback sang chat không có căn cứ. Muốn chat document-scoped thực sự cần một phase backend riêng.

## FLASHCARD_UI

Đọc dữ liệu lưu trước; nếu rỗng và READY thì POST canonical document generation với quickStart. Dùng chung loader, chống gửi trùng, kiểm tra phiên bản, retry và thông báo trạng thái. Giữ lật thẻ, chuyển thẻ và review.

## QUIZ_UI

Đọc quiz lưu trước; nếu rỗng thì POST grounded generation. Viewer và trang quiz tạo attempt V2, dùng questionOrder/optionOrder từ server, nộp bằng submissionToken ổn định khi retry và hiển thị điểm server. Bỏ chấm giả trên client và route submit đã bị backend vô hiệu hóa.

## DOCUMENT_STATE_UX

Shared DocumentUI phân biệt PROCESSING, READY + active index, FAILED, MISSING/FORBIDDEN và thiếu index. Chặn mọi lời gọi sinh AI trước READY + index. Danh sách và viewer cập nhật trạng thái qua polling; reader/map chờ xử lý; các lỗi có thông báo dễ hiểu và retry. Nút xử lý lại được mở lại khi request lỗi.

## SECURITY_RENDERING

Nội dung AI và structured summary sử dụng textContent/DOM nodes; citations xây URL nội bộ bằng document ID hợp lệ và page metadata. Không in stack trace/backend exception vào UI. Auth interceptor chỉ xử lý API cùng origin và redirect 401 một lần.

Ngoại lệ innerHTML an toàn: khung giao diện/icon tĩnh; metadata hoặc lựa chọn quiz đã escape HTML; highlight văn bản reader đã escape trước khi thêm thẻ mark tĩnh. Không đưa raw AI HTML vào DOM.

## STUDENT_TEACHER_SHARED

Các chức năng tài liệu dùng cùng trang và helper, không phân nhánh/nhân đôi theo role. Backend tiếp tục kiểm tra ownership/session. Teacher presentation tiếp tục dùng ApiClient/UIErrors chung; không sửa luồng deck/slide.

## Responsive

Giữ màu và phong cách Aurora, bổ sung spacing/typography/focus/disabled/loading và xử lý văn bản dài. Reader chuyển sang bố cục dọc ở màn nhỏ; canvas PDF và text layer dùng cùng viewport vừa khung để tránh lệch highlight.

## FINAL_AUDIT

```text
LEGACY_SUMMARY_FRONTEND_CALLS: 0
LEGACY_TOPIC_FRONTEND_CALLS: 0
LEGACY_FLASHCARD_GENERATION_GET: 0
LEGACY_QUIZ_GENERATION_GET: 0
RAW_AI_INNERHTML_RENDERING: 0 (safe exceptions described above)
PROCESSING_STATE_HANDLED: PASS
READY_STATE_HANDLED: PASS
FAILED_STATE_HANDLED: PASS
STUDENT_TEACHER_SHARED_CORE: PASS
STANDARD_TEST: PASS
UNRELATED_FILES_CHANGED: NONE (for this phase; pre-existing dirty changes preserved)
READY_FOR_FINAL_BROWSER_E2E: YES
```

## Validation

- `.\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1`: PASS, 926 Java tests, 0 failures/errors/skipped. H2/mock test configuration; process-local JVM memory limits and Java 17. Log: `build/phase11-standard.log`.
- `node --test src/test/js/*.cjs`: PASS, 96 tests. Log: `build/phase11-js.log`.
- Tests cover non-READY blocking, generation deduplication, stale index/selection, canonical POST flow, structured summary/citation injection, friendly HTTP errors, same-origin auth and actual V2 attempt payload/score flow. Inline scripts of changed pages parse successfully.
- Final browser E2E on the running host has not been performed. These automated checks do not prove real-browser layout, real account permissions or live AI availability.

Browser handoff: check STUDENT and TEACHER on the same READY document; switch documents while requests are pending; exercise PROCESSING/FAILED/missing access, empty-cache generation and retry; verify citations, map collapse/scroll, flashcard flip/review and actual quiz submission; check desktop/tablet/mobile widths. Notebook-less chat is the known backend limitation above.
