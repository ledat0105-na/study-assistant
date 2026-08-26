package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/progress")
@CrossOrigin(origins = "*")
public class ProgressController {

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

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

    // PRG-01: Xem tiến độ học tập (Tính hoàn toàn từ dữ liệu Server theo user + topic)
    @GetMapping
    public ResponseEntity<?> getMyProgress(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        List<StudyProgress> progressList = studyProgressRepository.findByUserId(userId);
        return ResponseEntity.ok(progressList);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        long totalDocs = documentRepository.countByUserId(userId);
        List<Document> userDocs = documentRepository.findByUserId(userId);

        long totalTopics = 0;
        for (Document doc : userDocs) {
            totalTopics += topicRepository.findByDocumentId(doc.getId()).size();
        }

        List<StudyProgress> progressList = studyProgressRepository.findByUserId(userId);
        long completedTopics = progressList.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long learningTopics = progressList.stream().filter(p -> "LEARNING".equals(p.getStatus())).count();

        double avgAccuracy = quizResultRepository.findByUserId(userId).stream()
                .mapToDouble(QuizResult::getScore)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", totalDocs);
        stats.put("totalTopics", totalTopics);
        stats.put("completedTopics", completedTopics);
        stats.put("learningTopics", learningTopics);
        stats.put("averageAccuracy", String.format("%.1f%%", avgAccuracy));

        return ResponseEntity.ok(stats);
    }

    // PRG-02: Hoàn thành Topic (Server tự tính từ Flashcard + Quiz threshold, KHÔNG tin progress=100 từ client)
    @PostMapping("/topic/{topicId}/complete")
    public ResponseEntity<?> checkAndCompleteTopic(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền cập nhật Topic này"));
        }

        // Server tự tính toán: Đã qua bài Quiz nào của Topic này chưa và tỉ lệ đúng >= 70% không?
        List<QuizResult> userQuizResults = quizResultRepository.findByUserId(userId);
        boolean passedQuiz = userQuizResults.stream()
                .anyMatch(r -> r.getQuiz().getTopic().getId().equals(topicId) && r.getScore() != null && r.getScore() >= 70.0);

        // Hoặc kiểm tra xem đã tạo Flashcards cho Topic này và ôn tập chưa
        Optional<StudyProgress> existingProgressOpt = studyProgressRepository.findByUserIdAndTopicId(userId, topicId);
        double currentProgress = existingProgressOpt.map(p -> p.getProgress() != null ? p.getProgress() : 0.0).orElse(0.0);

        if (!passedQuiz && currentProgress < 100.0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chưa đạt điều kiện hoàn thành Topic. Bạn cần hoàn thành Quiz với điểm >= 70% hoặc ôn hết Flashcards.",
                    "passedQuiz", passedQuiz,
                    "flashcardProgress", currentProgress
            ));
        }

        // Đạt điều kiện -> Server mới cập nhật COMPLETED & 100%
        StudyProgress progress = existingProgressOpt.orElseGet(() -> {
            StudyProgress p = new StudyProgress();
            p.setUser(userRepository.findById(userId).orElseThrow());
            p.setTopic(topic);
            return p;
        });

        progress.setStatus("COMPLETED");
        progress.setProgress(100.0);
        StudyProgress saved = studyProgressRepository.save(progress);

        return ResponseEntity.ok(Map.of(
                "message", "Chúc mừng! Bạn đã hoàn thành Topic này.",
                "progress", saved
        ));
    }
}
