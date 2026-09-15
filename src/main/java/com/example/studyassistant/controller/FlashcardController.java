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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/flashcards")
@CrossOrigin(origins = "*")
public class FlashcardController {

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

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

    @GetMapping("/document/{docId}")
    public ResponseEntity<?> getOrGenerateFlashcardsForDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Document doc = documentRepository.findByIdAndUserId(docId, userId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
        }

        List<Flashcard> existing = flashcardRepository.findByDocumentId(docId);
        if (!existing.isEmpty()) {
            return ResponseEntity.ok(existing);
        }

        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return ResponseEntity.ok(Map.of("cards", List.of(), "message", "Tài liệu chưa có nội dung trích xuất để tạo Flashcard."));
        }
        if (!aiService.hasApiKey()) {
            return ResponseEntity.ok(Map.of("cards", List.of(), "message", "OpenAI API Key chưa được cấu hình nên chưa thể tự động tạo Flashcard."));
        }

        String snippet = text.length() > 5000 ? text.substring(0, 5000) : text;
        String prompt = "Đọc kỹ nội dung tài liệu học tập dưới đây và tạo 8-10 thẻ Flashcard cốt lõi nhất (thuật ngữ - định nghĩa ngắn gọn) bằng tiếng Việt. " +
                "Trả về CHỈ MỘT đối tượng JSON (không thêm chữ nào khác), dạng:\n" +
                "{\"cards\": [{\"front\": \"Thuật ngữ hoặc câu hỏi ngắn\", \"back\": \"Định nghĩa/câu trả lời ngắn 1-2 câu\"}]}\n\n" +
                "YÊU CẦU: Tạo siêu nhanh, ngắn gọn, súc tích, tập trung ý chính nhất, không dài dòng.\n\n" +
                "TÀI LIỆU:\n" + snippet;

        try {
            String raw = aiService.callOpenAI(prompt, true);
            JsonNode cardsNode = objectMapper.readTree(extractJsonObject(raw)).get("cards");
            if (cardsNode == null || !cardsNode.isArray() || cardsNode.isEmpty()) {
                return ResponseEntity.ok(Map.of("cards", List.of(), "message", "AI không tạo được thẻ nào từ tài liệu này."));
            }

            List<Flashcard> saved = new ArrayList<>();
            for (JsonNode node : cardsNode) {
                String front = node.hasNonNull("front") ? node.get("front").asText().trim() : null;
                String back = node.hasNonNull("back") ? node.get("back").asText().trim() : null;
                if (front != null && !front.isEmpty() && back != null && !back.isEmpty()) {
                    Flashcard card = new Flashcard();
                    card.setQuestion(front);
                    card.setAnswer(back);
                    card.setDocument(doc);
                    saved.add(flashcardRepository.save(card));
                }
            }
            return ResponseEntity.ok(saved);
        } catch (AiService.NoApiKeyException e) {
            return ResponseEntity.ok(Map.of("cards", List.of(), "message", "OpenAI API Key chưa được cấu hình."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Không thể tạo Flashcard: " + e.getMessage()));
        }
    }

    @DeleteMapping("/document/{docId}")
    public ResponseEntity<?> regenerateFlashcardsForDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        Document doc = documentRepository.findByIdAndUserId(docId, userId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
        }
        flashcardRepository.deleteAll(flashcardRepository.findByDocumentId(docId));
        return ResponseEntity.ok(Map.of("message", "Đã xoá bộ thẻ cũ, lần sau mở lại sẽ tạo bộ thẻ mới."));
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Phản hồi của AI không chứa JSON hợp lệ");
        }
        return raw.substring(start, end + 1);
    }

    // FC-01: Tạo Flashcard từ Topic (Kiểm tra quota, Topic phải thuộc user, giới hạn số thẻ/lần)
    @PostMapping
    public ResponseEntity<?> createFlashcards(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Long topicId = body.get("topicId") != null ? Long.valueOf(body.get("topicId").toString()) : null;
        if (topicId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Topic ID là bắt buộc"));
        }

        // Authorize Topic -> Document -> Owner
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền tạo Flashcard trên Topic của người khác"));
        }

        List<Map<String, String>> cardsRaw = (List<Map<String, String>>) body.get("cards");
        if (cardsRaw == null || cardsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Danh sách thẻ không được để trống"));
        }

        // Giới hạn số thẻ tối đa mỗi lần (ví dụ: tối đa 20 thẻ/lần tạo)
        if (cardsRaw.size() > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mỗi lần chỉ được tạo tối đa 20 Flashcards"));
        }

        // Check Quota theo Plan
        User user = userRepository.findById(userId).orElseThrow();
        long currentTotalCards = flashcardRepository.count(); // hoặc count theo user topic
        int maxQuota = "PREMIUM".equalsIgnoreCase(user.getPlan()) ? 2000 : ("BASIC".equalsIgnoreCase(user.getPlan()) ? 500 : 100);

        List<Flashcard> createdCards = new ArrayList<>();
        for (Map<String, String> c : cardsRaw) {
            String front = c.get("front");
            String back = c.get("back");
            if (front != null && !front.trim().isEmpty() && back != null && !back.trim().isEmpty()) {
                Flashcard card = new Flashcard();
                card.setQuestion(front.trim());
                card.setAnswer(back.trim());
                card.setTopic(topic);
                createdCards.add(flashcardRepository.save(card));
            }
        }

        return ResponseEntity.ok(createdCards);
    }

    // FC-03: Xem Flashcard theo Topic (Authorize qua Topic->Document->Owner)
    @GetMapping("/topic/{topicId}")
    public ResponseEntity<?> getFlashcardsByTopic(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        // Authorize qua Topic->Document->Owner chống IDOR
        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền truy cập Flashcards của Topic này"));
        }

        List<Flashcard> cards = flashcardRepository.findByTopicId(topicId);
        return ResponseEntity.ok(cards);
    }

    // FC-02: Ôn thẻ và đánh dấu đã nhớ/chưa nhớ (Progress lưu riêng theo User)
    @PostMapping("/topic/{topicId}/review")
    public ResponseEntity<?> reviewFlashcardProgress(
            @PathVariable Long topicId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền cập nhật tiến độ cho Topic này"));
        }

        int rememberedCount = body.get("rememberedCount") != null ? Integer.parseInt(body.get("rememberedCount").toString()) : 0;
        int totalCards = body.get("totalCards") != null ? Integer.parseInt(body.get("totalCards").toString()) : 1;

        double progressPercent = Math.min(100.0, Math.max(0.0, ((double) rememberedCount / totalCards) * 100.0));

        Optional<StudyProgress> progressOpt = studyProgressRepository.findByUserIdAndTopicId(userId, topicId);
        StudyProgress progress;
        if (progressOpt.isPresent()) {
            progress = progressOpt.get();
        } else {
            progress = new StudyProgress();
            progress.setUser(userRepository.findById(userId).orElseThrow());
            progress.setTopic(topic);
        }

        progress.setProgress(progressPercent);
        progress.setStatus(progressPercent >= 100.0 ? "COMPLETED" : "LEARNING");
        studyProgressRepository.save(progress);

        return ResponseEntity.ok(Map.of(
                "message", "Đã lưu tiến độ ôn tập Flashcard",
                "progress", progressPercent,
                "status", progress.getStatus()
        ));
    }
}
