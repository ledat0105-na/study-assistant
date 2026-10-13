# Quiz submission — Phase 10 host E2E fix

## ROOT_CAUSE

Source xác nhận mâu thuẫn trạng thái: `GroundedQuizGenerationService` lưu quiz `DRAFT`, không có bước publish hoặc API publish nào; `QuizAttemptStartService` chỉ bắt đầu quiz `PUBLISHED`. GET vẫn trả câu hỏi của draft, nên người dùng chọn được đáp án; lúc nộp, frontend gọi tạo attempt trước và nhận 400 `Quiz is not PUBLISHED`. Thông báo cũ gom lỗi start và submit thành lỗi dữ liệu đáp án. Test trước chủ yếu tạo sẵn quiz PUBLISHED nên bỏ sót sự nối luồng này.

Đây là nguyên nhân được chứng minh từ source và regression test. Chưa có Response request lỗi mới trên host để xác nhận host đang chạy đúng bản code này; không tuyên bố đã kiểm thử browser host.

## FRONTEND_STATE_BUG

Không tìm thấy việc mất quizId hay dùng array index thay database questionId trong luồng thực. Thiếu preflight phân biệt state không hợp lệ và thiếu chuẩn hóa alias `id`/`questionId`. Đã chuẩn hóa questionId từ ID backend, giữ quizId generation hoặc stored response bằng nullish fallback và bổ sung validation.

Biến thực trong code:

- `currentQuizId`: ID từ `generation.quizId` hoặc GET stored `saved.quizId`.
- `currentQuizQuestions`: câu hỏi GET stored, chuẩn hóa `questionId = questionId ?? id`; sau start là câu hỏi snapshot của attempt.
- `quizUserAnswers`: state chọn đáp án theo database questionId, không có biến `selectedQuizAnswers`. Preview dùng option index của ordered options; sau start đối chiếu text và đổi sang optionOrder thật.
- `inlineAttempt.attemptId`: ID lượt làm bài trả từ start; trước start là null, không có biến `quizAttemptId` độc lập.
- `inlineSubmittedAnswers`: snapshot payload đã gửi; retry giữ nguyên state/token.

Không có số ID runtime trên host được thu thập trong lượt sửa này. Các ID 9/21/55/7/30 trong test là fixture, không phải dữ liệu host.

## BACKEND_EXPECTED_PAYLOAD

`QuizSubmissionRequest` cũ chứa `{submissionToken, answers: Map<Long,String>}` cho chữ A/B/C/D. Nhưng route `/api/quizzes/{quizId}/submit` hiện trả 405, không gọi `QuizSubmissionService` cũ. Không dùng lại route này.

Luồng hiện tại:

1. POST `/api/quizzes/{quizId}/attempts`, body `{}`; session quyết định user.
2. POST `/api/quiz-attempts/{attemptId}/submit`, HTTP DTO mới `QuizAttemptSubmitRequest`:

```json
{
  "submissionToken": "210cc85e-eb31-482f-8703-a26ab56b6b55",
  "answers": [
    {"questionOrder": 7, "selectedOptionOrders": [30], "shortAnswerText": null}
  ]
}
```

questionOrder/optionOrder lấy từ snapshot server, không dùng nhãn hiển thị `A. ...`. Controller tạo internal `QuizAttemptSubmitCommand` từ session userId và path attemptId. Ownership, token/hash replay, immutable snapshot, deadline và grading giữ nguyên.

## OLD_FRONTEND_PAYLOAD

`{userId:0, attemptId:<id>, submissionToken, answers:[...]}` gửi vào internal command DTO. userId đã được controller ghi đè, nhưng frontend không nên gửi identity. Bỏ hai trường này khỏi HTTP body; internal service command giữ nguyên.

## FIXED_FRONTEND_PAYLOAD

Chỉ `{submissionToken, answers:[{questionOrder,selectedOptionOrders,shortAnswerText}]}`. Không gửi userId, correctAnswer hoặc explanation. MockMvc kiểm chứng JSON này được deserialize và chuyển thành command có đúng session/path identity.

## Fix

- Sau khi toàn bộ câu hỏi/options/accepted answers/sources/concepts đã được lưu thành công trong transaction hiện có, chuyển quiz sang PUBLISHED và save. Draft vẫn bị attempt validation từ chối; không thay validation hoặc authorization để cho draft vượt qua.
- Nếu persistence lỗi, không publish. Không chạy update SQL/backfill hoặc reset database.
- Validation frontend phân biệt thiếu quiz, thiếu questions, invalid/duplicate question ID, invalid option value và unanswered. Câu bỏ trống: “Bạn cần trả lời tất cả câu hỏi trước khi nộp bài.”
- Lỗi 400 ở start hướng dẫn Tạo bài mới; lỗi 400 submit có thông báo khác. Không hiển thị exception/stack trace.

## Audit results

```text
QUIZ_ID_PRESERVED: YES
QUESTION_IDS_PRESERVED: YES
ANSWER_STATE_KEY: database questionId
BACKEND_CHANGED: YES — publish completed validated quiz; separate HTTP input DTO
DATABASE_CHANGED: NO
FOCUSED_TEST: PASS
STANDARD_TEST: PASS
READY_FOR_HOST_RETEST: YES
```

## FILES_CHANGED

- `src/main/resources/static/viewer.html`
- `src/main/resources/static/js/document-features.js`
- `src/main/resources/static/js/quiz-interaction.js`
- `src/main/java/com/example/studyassistant/controller/QuizAttemptController.java`
- `src/main/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptSubmitRequest.java` (new)
- `src/main/java/com/example/studyassistant/domain/quiz/generation/GroundedQuizGenerationService.java` (publish finalization only)
- `src/test/java/com/example/studyassistant/controller/QuizAttemptSubmitContractTest.java` (new)
- `src/test/java/com/example/studyassistant/domain/quiz/generation/GroundedQuizGenerationServiceTest.java`
- `src/test/java/com/example/studyassistant/domain/quiz/generation/QuizBatchGenerationTest.java` (assert same quiz draft/final save)
- `src/test/js/phase11-core.test.cjs`
- `src/test/js/quiz-interaction.test.cjs`
- `src/test/js/viewer-generation.test.cjs` (assert generated quiz/question IDs)
- Báo cáo này.

## Verification and host handoff

Frontend tests verify generated/stored quiz IDs, stable DB question IDs, visible selection, validation without fetch, actual optionOrder payload, backend result rendering, retry/stale state and new-quiz reset. New Java tests verify successful publication order, failed persistence does not publish, HTTP input without identity fields, session requirement and inaccessible attempt response. Existing ownership/submission tests remain in standard suite.

Run `.\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1` with Java 17/process-local memory limits and H2/mock. No production DB, no `studyassistant_rag_test` reset, no live OpenAI. Logs: `build/quiz-submission-js.log`, `build/quiz-submission-standard.log`.

Final verification: 933 Java tests PASS (0 failures/errors), 104 JavaScript tests PASS. MockMvc accepts the exact identity-free HTTP JSON; publication regression checks DRAFT → completed PUBLISHED in order and no publication on persistence failure.

Restart backend and refresh viewer. For draft quizzes saved by the previous buggy version, click **Tạo bài mới** once: new quizzes now finish PUBLISHED. Existing PUBLISHED V2 quizzes retain their IDs and can start attempts. Old drafts were not silently changed in production. Select all answers, submit and verify start 200 → submit 200 → score/feedback. Browser host retest remains required.
