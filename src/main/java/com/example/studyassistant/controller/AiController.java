package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> payload) {
        String message = payload.get("message") != null ? payload.get("message").toString() : "";
        if (message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }

        // Parse optional docIds for multi-document context mapping
        List<?> docIdsRaw = (List<?>) payload.get("docIds");
        StringBuilder context = new StringBuilder();
        if (docIdsRaw != null && !docIdsRaw.isEmpty()) {
            context.append("Bạn là một Trợ lý AI học tập thông minh. Hãy sử dụng thông tin từ các tài liệu sau đây để trả lời câu hỏi của người dùng bằng tiếng Việt:\n\n");
            for (Object idObj : docIdsRaw) {
                try {
                    Long docId = Long.valueOf(idObj.toString());
                    Optional<Document> docOpt = documentRepository.findById(docId);
                    if (docOpt.isPresent()) {
                        Document doc = docOpt.get();
                        context.append("=== TÀI LIỆU: ").append(doc.getFileName()).append(" ===\n");
                        
                        // Fetch all topic contents for this document
                        List<Topic> topics = topicRepository.findByDocumentId(docId);
                        for (Topic topic : topics) {
                            context.append("Khái niệm: ").append(topic.getName()).append("\n");
                            if (topic.getDescription() != null && !topic.getDescription().trim().isEmpty()) {
                                context.append("Nội dung: ").append(topic.getDescription()).append("\n");
                            }
                            context.append("\n");
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

        // Fallback simulated AI if API key is not configured
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            String fallbackResponse = "Chào bạn! Tôi là Trợ lý AI học tập. Hiện tại OpenAI API Key chưa được cấu hình. " +
                    "Bạn vui lòng thêm cấu hình `openai.api.key=YOUR_API_KEY` vào file `application.properties` để bắt đầu trò chuyện trực tiếp với tôi nhé!\n\n" +
                    "Câu hỏi của bạn là: \"" + message + "\"";
            return ResponseEntity.ok(Map.of("response", fallbackResponse));
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.openai.com/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            // Construct OpenAI GPT Chat Completion payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");

            Map<String, String> messageObj = new HashMap<>();
            messageObj.put("role", "user");
            messageObj.put("content", finalPrompt);

            requestBody.put("messages", Collections.singletonList(messageObj));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                Map<String, Object> body = responseEntity.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");
                    if (messageMap != null) {
                        String text = messageMap.get("content").toString();
                        return ResponseEntity.ok(Map.of("response", text));
                    }
                }
            }
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get valid response from OpenAI API"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error connecting to AI API: " + e.getMessage()));
        }
    }
}
