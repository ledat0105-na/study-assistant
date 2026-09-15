package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiService aiService;

    private static final int MAX_CONTEXT_CHARS_PER_DOC = 12000;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        String message = payload.get("message") != null ? payload.get("message").toString() : "";
        if (message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }

        List<?> docIdsRaw = (List<?>) payload.get("docIds");
        Long firstDocId = null;
        StringBuilder context = new StringBuilder();
        if (docIdsRaw != null && !docIdsRaw.isEmpty()) {
            context.append("Bạn là một Trợ lý AI học tập thông minh. Tài liệu bên dưới đã được đánh dấu ranh giới từng trang bằng nhãn dạng [Trang N] hoặc [Slide N]. ")
                    .append("Hãy sử dụng thông tin từ các tài liệu sau để trả lời câu hỏi của người dùng bằng tiếng Việt. ")
                    .append("QUAN TRỌNG: mỗi khi bạn nêu một ý/thông tin lấy từ tài liệu, hãy chèn ngay theo sau một trích dẫn đúng định dạng ")
                    .append("[Trang N: \"một cụm từ NGẮN (5-12 từ), NGUYÊN VĂN chính xác lấy trực tiếp từ đúng đoạn đó trong tài liệu\"] ")
                    .append("(hoặc [Slide N: \"...\"]). Cụm trích dẫn bắt buộc phải là nguyên văn xuất hiện trong tài liệu, không tự bịa số trang hoặc trích dẫn.\n\n");
            for (Object idObj : docIdsRaw) {
                try {
                    Long docId = Long.valueOf(idObj.toString());
                    Optional<Document> docOpt = documentRepository.findByIdAndUserId(docId, userId);
                    if (docOpt.isPresent()) {
                        if (firstDocId == null) firstDocId = docId;
                        Document doc = docOpt.get();
                        context.append("=== TÀI LIỆU: ").append(doc.getFileName()).append(" ===\n");

                        String realText = doc.getExtractedText();
                        if (realText != null && !realText.isBlank()) {
                            String snippet = realText.length() > MAX_CONTEXT_CHARS_PER_DOC
                                    ? realText.substring(0, MAX_CONTEXT_CHARS_PER_DOC)
                                    : realText;
                            context.append(snippet).append("\n");
                        } else {
                            List<Topic> topics = topicRepository.findByDocumentId(docId);
                            for (Topic topic : topics) {
                                context.append("Khái niệm: ").append(topic.getName()).append("\n");
                                if (topic.getDescription() != null && !topic.getDescription().trim().isEmpty()) {
                                    context.append("Nội dung: ").append(topic.getDescription()).append("\n");
                                }
                                context.append("\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid ids
                }
            }
            context.append("=== HẾT TÀI LIỆU ===\n\n");
            context.append("Yêu cầu/Câu hỏi của người dùng: ");
        }

        String finalPrompt = context.toString() + message;

        String fallbackResponse = "Chào bạn! Tôi là Trợ lý AI học tập. Hiện tại OpenAI API Key chưa được cấu hình. " +
                "Bạn vui lòng thêm cấu hình `openai.api.key=YOUR_API_KEY` vào file `application.properties` để bắt đầu trò chuyện trực tiếp với tôi nhé!\n\n" +
                "Câu hỏi của bạn là: \"" + message + "\"";

        try {
            String response = aiService.callOpenAI(finalPrompt);
            Map<String, Object> result = new HashMap<>();
            result.put("response", response);
            if (firstDocId != null) {
                result.put("docId", firstDocId);
            }
            return ResponseEntity.ok(result);
        } catch (AiService.NoApiKeyException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("response", fallbackResponse);
            if (firstDocId != null) result.put("docId", firstDocId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error connecting to AI API: " + e.getMessage()));
        }
    }

    @GetMapping("/summary/{docId}")
    public ResponseEntity<?> summarize(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Document doc = documentRepository.findByIdAndUserId(docId, userId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
        }

        if (doc.getAiSummary() != null && !doc.getAiSummary().isBlank()) {
            return ResponseEntity.ok(Map.of("summary", doc.getAiSummary(), "cached", true, "docId", docId));
        }

        String context = buildDocumentContext(userId, docId);
        if (context.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "summary", "Tài liệu chưa có nội dung để tóm tắt (có thể đang xử lý hoặc trích xuất thất bại).",
                    "cached", false, "docId", docId
            ));
        }

        String prompt = context +
            "Hãy đọc tài liệu trên và tóm tắt ngắn gọn nội dung chính bằng tiếng Việt, " +
            "trình bày dưới dạng các gạch đầu dòng dễ hiểu (khoảng 5-8 ý). " +
            "Mỗi gạch đầu dòng phải kết thúc bằng trích dẫn đúng định dạng [Trang N: \"cụm từ nguyên văn ngắn\"] hoặc [Slide N: \"...\"].";
        try {
            String summary = aiService.callOpenAI(prompt);
            doc.setAiSummary(summary);
            documentRepository.save(doc);
            return ResponseEntity.ok(Map.of("summary", summary, "cached", false, "docId", docId));
        } catch (AiService.NoApiKeyException e) {
            return ResponseEntity.ok(Map.of(
                    "summary", "OpenAI API Key chưa được cấu hình nên chưa thể tự động tóm tắt. Bạn vẫn có thể đọc nội dung tài liệu bên trái.",
                    "cached", false, "docId", docId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Không thể tóm tắt: " + e.getMessage()));
        }
    }

    private String buildDocumentContext(Long userId, Long docId) {
        Optional<Document> docOpt = documentRepository.findByIdAndUserId(docId, userId);
        if (docOpt.isEmpty()) {
            return "";
        }

        Document doc = docOpt.get();
        StringBuilder context = new StringBuilder();
        context.append("Bạn là một Trợ lý AI học tập thông minh. Tài liệu bên dưới đã được đánh dấu ranh giới từng trang bằng nhãn dạng [Trang N] hoặc [Slide N]. ")
            .append("Hãy sử dụng thông tin từ tài liệu sau để trả lời bằng tiếng Việt. ")
            .append("Mỗi thông tin lấy từ tài liệu phải có trích dẫn ngay sau đó theo dạng [Trang N: \"cụm từ nguyên văn ngắn\"] hoặc [Slide N: \"...\"]. ")
            .append("Cụm từ phải là nguyên văn 5-12 từ trong đúng đoạn tài liệu, không tự bịa.\n\n");
        context.append("=== TÀI LIỆU: ").append(doc.getFileName()).append(" ===\n");

        String realText = doc.getExtractedText();
        if (realText != null && !realText.isBlank()) {
            String snippet = realText.length() > MAX_CONTEXT_CHARS_PER_DOC
                    ? realText.substring(0, MAX_CONTEXT_CHARS_PER_DOC)
                    : realText;
            context.append(snippet).append("\n");
        } else {
            List<Topic> topics = topicRepository.findByDocumentId(docId);
            for (Topic topic : topics) {
                context.append("Khái niệm: ").append(topic.getName()).append("\n");
                if (topic.getDescription() != null && !topic.getDescription().trim().isEmpty()) {
                    context.append("Nội dung: ").append(topic.getDescription()).append("\n");
                }
            }
        }
        context.append("=== HẾT TÀI LIỆU ===\n\n");
        return context.toString();
    }

}
