package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
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

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isEmpty()) {
            try {
                return Long.parseLong(headerUserId);
            } catch (NumberFormatException ignored) {}
        }
        return null;
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

        if (quiz.getTopic() == null || quiz.getTopic().getDocument() == null || !quiz.getTopic().getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền truy cập Quiz này"));
        }

        List<QuizQuestion> rawQuestions = quizQuestionRepository.findByQuizId(quizId);

        // Chỉ trả về câu hỏi và các lựa chọn A, B, C, D (ẨN correctAnswer và explanation)
        List<Map<String, Object>> sanitizedQuestions = rawQuestions.stream().map(q -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", q.getId());
            map.put("question", q.getQuestion());
            map.put("answerA", q.getAnswerA());
            map.put("answerB", q.getAnswerB());
            map.put("answerC", q.getAnswerC());
            map.put("answerD", q.getAnswerD());
            // KHÔNG trả correctAnswer và explanation
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(sanitizedQuestions);
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

        if (quiz.getTopic() == null || quiz.getTopic().getDocument() == null || !quiz.getTopic().getDocument().getUser().getId().equals(userId)) {
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

        if (quiz.getTopic() == null || quiz.getTopic().getDocument() == null || !quiz.getTopic().getDocument().getUser().getId().equals(userId)) {
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
