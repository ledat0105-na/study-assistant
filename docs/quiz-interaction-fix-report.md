# Fix quiz interaction

## ROOT_CAUSE

Code audit xác nhận các lỗi sau; chưa chạy lại trên trình duyệt host thật:

- Viewer vô hiệu hóa lựa chọn trước khi tạo attempt và chỉ gắn aria-pressed sau khi chọn, không có màu/indicator rõ ràng.
- Chọn đáp án chưa kiểm tra câu bỏ trống trước submit; lỗi HTTP dùng thông báo chung.
- QuizAttemptStartService chỉ xác thực qua notebook, từ chối quiz DOCUMENT không có notebook dù người dùng sở hữu tài liệu.
- QuizMasteryEvidenceAdapter luôn resolve notebook, kể cả không có concept evidence; quiz document-only bị chặn lúc nộp.
- Kết quả V2 chỉ có questionOrder/pointsEarned/isCorrect, nên frontend không có dữ liệu authoritative để hiển thị đáp án đúng và giải thích.

## BACKEND_SUBMIT_CONTRACT

- Bắt đầu: POST `/api/quizzes/{quizId}/attempts`. Không cần client userId; lấy session. Trả attemptId và câu hỏi snapshot với questionId, questionOrder, options `{optionOrder, optionText}`; không trả đáp án đúng.
- Nộp: POST `/api/quiz-attempts/{attemptId}/submit` với `{userId, attemptId, submissionToken, answers:[{questionOrder, selectedOptionOrders:[integer], shortAnswerText:null}]}` cho MCQ_SINGLE. Client gửi userId=0 vì DTO primitive; controller luôn ghi đè identity/attemptId bằng session/path.
- POST `/api/quizzes/{quizId}/submit` là legacy, trả 405. QuizSubmissionService cũ không phải luồng submit V2.
- Kết quả: score theo thang 100, totalQuestions, correctAnswers và perQuestionResults. Thêm hai field chỉ sau chấm: correctOptionOrders và explanation từ snapshot.

## FRONTEND_SELECTION_FIX

Toàn bộ option là button clickable, có cursor, border/background nổi bật, dấu tròn và aria-pressed. State lưu theo questionId, mỗi câu một lựa chọn. Re-render giữ đáp án và vị trí cuộn container. Đọc lại cùng quiz không reset state; tạo mới/đổi tài liệu reset state.

## SUBMIT_PAYLOAD_FIX

Kiểm tra toàn bộ câu cần trả lời; thông báo số câu thiếu và focus/scroll câu đầu tiên, không gọi backend. Khi nộp lần đầu, tạo attempt; ánh xạ lựa chọn preview sang optionOrder thật bằng questionId, thứ tự và đối chiếu text. Nếu snapshot khác, dừng thay vì đoán đáp án. Chống submit trùng khi đang chờ. Retry giữ nguyên attempt, token và payload; khóa chỉnh đáp án sau lần gửi để tránh retry với dữ liệu khác. Bấm Tạo bài mới để bắt đầu bài khác.

## GRADING_UI

Giữ toàn bộ câu hỏi. Mỗi câu hiển thị lựa chọn đã chọn, ĐÚNG/SAI từ server; lựa chọn sai đỏ, đáp án đúng xanh. Hiển thị Điểm X/100, tỷ lệ đúng, số đúng/sai. Không tự chấm bằng dữ liệu sinh quiz.

## EXPLANATION_UI

Giải thích lấy từ explanationSnapshot sau successful grading. Dùng textContent cho câu hỏi/options/giải thích; không render HTML AI. Host chạy backend cũ chưa có field feedback sẽ có thông báo thiếu dữ liệu, không bịa đáp án.

## FILES_CHANGED

- `src/main/resources/static/viewer.html`
- `src/main/resources/static/js/quiz-interaction.js` (mới)
- `src/main/resources/static/css/feature-ui.css`
- `src/main/java/com/example/studyassistant/domain/quiz/attempt/QuizAttemptStartService.java`
- `src/main/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptPerQuestionResult.java`
- `src/main/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptSubmissionService.java`
- `src/main/java/com/example/studyassistant/service/QuizMasteryEvidenceAdapter.java`
- `src/test/java/com/example/studyassistant/domain/quiz/attempt/QuizAttemptStartServiceTest.java`
- `src/test/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptSubmissionServiceTest.java`
- `src/test/java/com/example/studyassistant/service/QuizMasteryEvidenceAdapterTest.java`
- `src/test/js/quiz-interaction.test.cjs` (mới)
- `src/test/js/viewer-generation.test.cjs` (fixture identity initialization)
- Báo cáo này.

## Verification

```text
BACKEND_GENERATION_CHANGED: NO
DATABASE_CHANGED: NO
FOCUSED_TEST: PASS
STANDARD_TEST: PASS
READY_FOR_HOST_TEST: YES
```

JavaScript: 102 tests PASS, gồm 6 test mới dùng chính hàm viewer và renderer thực, kiểm tra chọn A rồi C, nhiều câu, thiếu đáp án, ánh xạ order, màu kết quả, HTML literal an toàn, retry token/payload, submit đồng thời, stale document và reset bài mới. Java: bộ chuẩn `.\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1` chạy với Java 17/JVM giới hạn bộ nhớ, H2/mock; không kết nối MySQL/OpenAI thật. Các regression bổ sung kiểm tra document-only owner, foreign document bị từ chối, mastery không có concepts và feedback/replay từ snapshot.

Logs: `build/quiz-interaction-js.log`, `build/quiz-interaction-standard.log`. Chưa xác nhận A–J trên browser host thật. Cần khởi động lại backend để nhận sửa attempt/feedback, refresh frontend rồi chọn một quiz V2. Quiz legacy chưa PUBLISHED/cấu trúc V2 cần bấm Tạo bài mới; không chuyển đổi hoặc sửa dữ liệu cũ trong fix này.

Kết quả cuối: 929 test Java, 0 failures/errors; 35 test trong ba suite attempt-start, submission và mastery adapter đều PASS. 102 test JavaScript, 0 failures.
