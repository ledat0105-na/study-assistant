package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin(origins = "*")
public class QuizController {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // userId chỉ lấy từ session đã đăng nhập, KHÔNG tin header X-User-Id từ client
    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    private boolean isOwner(Quiz quiz, Long userId) {
        if (quiz.getDocument() != null) {
            return quiz.getDocument().getUser() != null && quiz.getDocument().getUser().getId().equals(userId);
        }
        if (quiz.getTopic() != null && quiz.getTopic().getDocument() != null) {
            return quiz.getTopic().getDocument().getUser().getId().equals(userId);
        }
        return false;
    }

    @GetMapping("/document/{docId}")
    public ResponseEntity<?> getOrGenerateQuizForDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Document doc = documentRepository.findByIdAndUserId(docId, userId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
        }

        List<Quiz> existingQuizzes = quizRepository.findByDocumentId(docId);
        if (!existingQuizzes.isEmpty()) {
            Quiz quiz = existingQuizzes.get(0);
            return ResponseEntity.ok(Map.of("quizId", quiz.getId(), "questions", sanitize(quizQuestionRepository.findByQuizId(quiz.getId()))));
        }

        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return ResponseEntity.ok(Map.of("quizId", 0, "questions", List.of(), "message", "Tài liệu chưa có nội dung trích xuất để tạo bài trắc nghiệm."));
        }
        if (!aiService.hasApiKey()) {
            return ResponseEntity.ok(Map.of("quizId", 0, "questions", List.of(), "message", "OpenAI API Key chưa được cấu hình nên chưa thể tự động tạo bài trắc nghiệm."));
        }

        String snippet = text.length() > 5000 ? text.substring(0, 5000) : text;
        String prompt = "Đọc kỹ nội dung tài liệu học tập dưới đây và tạo bài trắc nghiệm 5-8 câu hỏi cốt lõi (4 đáp án A, B, C, D) bằng tiếng Việt. " +
                "Trả về CHỈ MỘT đối tượng JSON (không thêm chữ nào khác), dạng:\n" +
                "{\"questions\": [{\"question\": \"Nội dung câu hỏi ngắn\", \"answerA\": \"...\", \"answerB\": \"...\", \"answerC\": \"...\", \"answerD\": \"...\", \"correctAnswer\": \"A\", \"explanation\": \"Lời giải 1 câu ngắn gọn\"}]}\n\n" +
                "YÊU CẦU: Tạo siêu nhanh, ngắn gọn, chính xác, không lan man dài dòng.\n\n" +
                "TÀI LIỆU:\n" + snippet;

        try {
            String raw = aiService.callOpenAI(prompt, true);
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.get("questions");
            if (questionsNode == null || !questionsNode.isArray() || questionsNode.isEmpty()) {
                return ResponseEntity.ok(Map.of("quizId", 0, "questions", List.of(), "message", "AI không tạo được câu hỏi nào từ tài liệu này."));
            }

            Quiz quiz = new Quiz();
            quiz.setTitle("Trắc nghiệm: " + doc.getFileName());
            quiz.setDocument(doc);
            Quiz savedQuiz = quizRepository.save(quiz);

            for (JsonNode node : questionsNode) {
                String correct = node.hasNonNull("correctAnswer") ? node.get("correctAnswer").asText().trim().toUpperCase() : "";
                if (!node.hasNonNull("question") || !List.of("A", "B", "C", "D").contains(correct)) continue;

                QuizQuestion q = new QuizQuestion();
                q.setQuiz(savedQuiz);
                q.setQuestion(node.get("question").asText());
                q.setAnswerA(node.hasNonNull("answerA") ? node.get("answerA").asText() : "");
                q.setAnswerB(node.hasNonNull("answerB") ? node.get("answerB").asText() : "");
                q.setAnswerC(node.hasNonNull("answerC") ? node.get("answerC").asText() : "");
                q.setAnswerD(node.hasNonNull("answerD") ? node.get("answerD").asText() : "");
                q.setCorrectAnswer(correct);
                q.setExplanation(node.hasNonNull("explanation") ? node.get("explanation").asText() : null);
                quizQuestionRepository.save(q);
            }

            return ResponseEntity.ok(Map.of("quizId", savedQuiz.getId(), "questions", sanitize(quizQuestionRepository.findByQuizId(savedQuiz.getId()))));
        } catch (AiService.NoApiKeyException e) {
            return ResponseEntity.ok(Map.of("quizId", 0, "questions", List.of(), "message", "OpenAI API Key chưa được cấu hình."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Không thể tạo bài trắc nghiệm: " + e.getMessage()));
        }
    }

    @DeleteMapping("/document/{docId}")
    public ResponseEntity<?> regenerateQuizForDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        Document doc = documentRepository.findByIdAndUserId(docId, userId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
        }
        for (Quiz quiz : quizRepository.findByDocumentId(docId)) {
            quizQuestionRepository.deleteAll(quizQuestionRepository.findByQuizId(quiz.getId()));
            quizRepository.delete(quiz);
        }
        return ResponseEntity.ok(Map.of("message", "Đã xoá bài trắc nghiệm cũ, lần sau mở lại sẽ tạo bộ câu hỏi mới."));
    }

    private List<Map<String, Object>> sanitize(List<QuizQuestion> rawQuestions) {
        return rawQuestions.stream().map(q -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", q.getId());
            map.put("question", q.getQuestion());
            map.put("answerA", q.getAnswerA());
            map.put("answerB", q.getAnswerB());
            map.put("answerC", q.getAnswerC());
            map.put("answerD", q.getAnswerD());
            return map;
        }).collect(Collectors.toList());
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Phản hồi của AI không chứa JSON hợp lệ");
        }
        return raw.substring(start, end + 1);
    }

    // QUIZ-01: Tạo Quiz từ Topic (Kiểm tra quota và max câu theo Plan; Topic thuộc user)
    @PostMapping
    public ResponseEntity<?> createQuiz(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Long topicId = body.get("topicId") != null ? Long.valueOf(body.get("topicId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : "Bài kiểm tra mới";
        
        if (topicId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Topic ID là bắt buộc"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền tạo Quiz trên Topic của người khác"));
        }

        User user = userRepository.findById(userId).orElseThrow();
        int maxQuestions = "PREMIUM".equalsIgnoreCase(user.getPlan()) ? 50 : ("BASIC".equalsIgnoreCase(user.getPlan()) ? 20 : 10);

        List<Map<String, String>> questionsRaw = (List<Map<String, String>>) body.get("questions");
        if (questionsRaw == null || questionsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Danh sách câu hỏi không được để trống"));
        }

        if (questionsRaw.size() > maxQuestions) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng câu hỏi vượt quá giới hạn " + maxQuestions + " câu của gói " + user.getPlan()));
        }

        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setTopic(topic);
        Quiz savedQuiz = quizRepository.save(quiz);

        for (Map<String, String> q : questionsRaw) {
            QuizQuestion question = new QuizQuestion();
            question.setQuiz(savedQuiz);
            question.setQuestion(q.get("question"));
            question.setAnswerA(q.get("answerA"));
            question.setAnswerB(q.get("answerB"));
            question.setAnswerC(q.get("answerC"));
            question.setAnswerD(q.get("answerD"));
            question.setCorrectAnswer(q.get("correctAnswer").toUpperCase());
            question.setExplanation(q.get("explanation"));
            quizQuestionRepository.save(question);
        }

        return ResponseEntity.ok(savedQuiz);
    }

    // QUIZ-02: Làm Quiz (API lấy câu hỏi KHÔNG trả correctAnswer/explanation trước submit)
    @GetMapping("/{quizId}/questions")
    public ResponseEntity<?> getQuestionsByQuiz(@PathVariable Long quizId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz không tồn tại"));

        if (!isOwner(quiz, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền truy cập Quiz này"));
        }

        List<QuizQuestion> rawQuestions = quizQuestionRepository.findByQuizId(quizId);
        return ResponseEntity.ok(sanitize(rawQuestions));
    }

    // QUIZ-05: Làm lại Quiz (Tạo attempt mới, không ghi đè lịch sử cũ)
    @PostMapping("/{quizId}/start-attempt")
    public ResponseEntity<?> startNewAttempt(@PathVariable Long quizId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz không tồn tại"));

        if (!isOwner(quiz, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền làm Quiz này"));
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(userRepository.findById(userId).orElseThrow());
        attempt.setQuiz(quiz);
        attempt.setStatus("IN_PROGRESS");
        attempt.setStartedAt(LocalDateTime.now());

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);
        return ResponseEntity.ok(savedAttempt);
    }

    // QUIZ-03: Nộp bài và nhận điểm (Backend TỰ CHẤM ĐIỂM và lưu score)
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<?> submitQuizAnswers(
            @PathVariable Long quizId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz không tồn tại"));

        if (!isOwner(quiz, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền nộp bài Quiz này"));
        }

        Map<String, Object> answers = (Map<String, Object>) payload.get("answers");
        if (answers == null) answers = Collections.emptyMap();

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizId(quizId);
        int correctAnswers = 0;
        for (QuizQuestion q : questions) {
            Object submittedAnswerObj = answers.get(q.getId().toString());
            if (submittedAnswerObj != null) {
                String subAnswerStr = submittedAnswerObj.toString().trim().toUpperCase();
                if (subAnswerStr.equals(q.getCorrectAnswer().trim())) {
                    correctAnswers++;
                }
            }
        }

        double score = questions.isEmpty() ? 0.0 : Math.round((((double) correctAnswers / questions.size()) * 100.0) * 100.0) / 100.0;

        // Lưu bản ghi kết quả QuizResult
        QuizResult result = new QuizResult();
        result.setUser(userRepository.findById(userId).orElseThrow());
        result.setQuiz(quiz);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(questions.size());
        result.setScore(score);
        QuizResult savedResult = quizResultRepository.save(result);

        // Lưu hoặc cập nhật QuizAttempt cho lượt làm bài mới
        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(userRepository.findById(userId).orElseThrow());
        attempt.setQuiz(quiz);
        attempt.setTotalQuestions(questions.size());
        attempt.setCorrectAnswers(correctAnswers);
        attempt.setScore(score);
        attempt.setStatus("COMPLETED");
        attempt.setCompletedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);

        return ResponseEntity.ok(savedResult);
    }

    // QUIZ-04: Xem đáp án sau khi nộp (Chỉ trả correctAnswer và explanation sau khi đã hoàn tất nộp bài)
    @GetMapping("/{quizId}/answers")
    public ResponseEntity<?> getQuizAnswersAndExplanations(@PathVariable Long quizId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        // Kiểm tra xem user đã có lượt nộp bài nào chưa
        List<QuizResult> results = quizResultRepository.findByUserId(userId).stream()
                .filter(r -> r.getQuiz().getId().equals(quizId))
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn phải hoàn thành nộp bài Quiz trước khi xem đáp án!"));
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizId(quizId);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<?> getQuizzesByTopic(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        return ResponseEntity.ok(quizRepository.findByTopicId(topicId));
    }
}
