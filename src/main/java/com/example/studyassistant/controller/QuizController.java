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
import com.example.studyassistant.domain.quiz.generation.*;
import com.example.studyassistant.domain.quiz.attempt.*;
import com.example.studyassistant.domain.quiz.submission.*;


import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin(origins = "*")
public class QuizController {
    @PostMapping("/generate")
    public ResponseEntity<?> generateQuiz(@RequestBody GroundedQuizGenerationCommand cmd, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        if (groundedQuizGenerationService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Generation service not available"));
        }

        try {
            GroundedQuizGenerationCommand command = new GroundedQuizGenerationCommand(
                    userId,
                    cmd.scopeType(),
                    cmd.notebookId(),
                    cmd.documentId(),
                    cmd.topicId(),
                    cmd.scopeConceptId(),
                    cmd.mode(),
                    cmd.questionCount(),
                    cmd.allowedQuestionTypes(),
                    cmd.title(),
                    cmd.timeLimitSeconds(),
                    cmd.passThreshold()
            );
            GroundedQuizGenerationResult result = groundedQuizGenerationService.generate(command);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }


    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizQuestionOptionRepository quizQuestionOptionRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired(required=false)
    private GroundedQuizGenerationService groundedQuizGenerationService;

    @Autowired(required=false)
    private QuizAttemptStartService quizAttemptStartService;

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
            Quiz quiz = existingQuizzes.stream().max(Comparator.comparing(Quiz::getId)).orElseThrow();
            return ResponseEntity.ok(Map.of("quizId", quiz.getId(), "questions", sanitize(quizQuestionRepository.findByQuizId(quiz.getId()))));
        }

        // EI1.4: Legacy substring generation is forbidden. AI generation on GET is forbidden.
        return ResponseEntity.ok(Map.of("quizId", 0, "questions", List.of(), "message", "Tài liệu chưa có bài trắc nghiệm."));
    }

    @DeleteMapping("/document/{docId}")
    public ResponseEntity<?> regenerateQuizForDocument(@PathVariable Long docId, HttpServletRequest request) {
        // EI1.4: Legacy destructive regeneration is forbidden in V2.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Destructive legacy regeneration is forbidden. Please use V2 POST /api/quizzes/generate endpoint."));
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
            if (q.getQuestionType() != null) {
                map.put("options", quizQuestionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(q.getId())
                        .stream().map(QuizQuestionOption::getOptionText).toList());
            }
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (quizAttemptStartService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Service not available"));
        }

        try {
            QuizAttemptStartCommand cmd = new QuizAttemptStartCommand(userId, quizId);
            QuizAttemptResponse response = quizAttemptStartService.startAttempt(cmd);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/{quizId}/attempts")
    public ResponseEntity<?> startNewAttemptV2(@PathVariable Long quizId, HttpServletRequest request) {
        return startNewAttempt(quizId, request);
    }

    @Autowired
    private com.example.studyassistant.service.QuizSubmissionService quizSubmissionService;

    // QUIZ-03: Nộp bài và nhận điểm (Backend TỰ CHẤM ĐIỂM và lưu score)
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<?> submitQuizLegacy(@PathVariable Long quizId, HttpServletRequest request) {
        // EI1.6: Legacy endpoint disabled to prevent QuizResult bypass.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Legacy submission is disabled. Use V2 POST /api/quiz-attempts/{attemptId}/submit"));
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
