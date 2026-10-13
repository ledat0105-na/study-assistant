package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;


@Service
public class ChatService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Value("${openai.api.key:}")
    private String openaiApiKey;


    // CHAT-01: Tạo đoạn chat mới trong Notebook + Quota check
    public Conversation createConversation(Long userId, Long notebookId, String title) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // Kiểm tra quota conversation theo Plan
        long currentCount = conversationRepository.countByUserIdAndIsDeletedFalse(userId);
        int maxConversations = getConversationQuotaByPlan(user.getPlan());
        if (currentCount >= maxConversations) {
            throw new IllegalStateException("Bạn đã đạt giới hạn cuộc trò chuyện của gói " + user.getPlan() + " (" + maxConversations + " cuộc trò chuyện).");
        }

        Notebook notebook = null;
        if (notebookId != null) {
            notebook = notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền truy cập"));
        }

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setNotebook(notebook);
        String finalTitle = (title != null && !title.trim().isEmpty()) ? sanitizeTitle(title) : "Cuộc trò chuyện mới";
        conversation.setTitle(finalTitle);
        conversation.setIsDeleted(false);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        return conversationRepository.save(conversation);
    }

    // CHAT-02: Xem lịch sử chat của mình (Phân trang, chống IDOR)
    public Page<Conversation> getMyConversations(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return conversationRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
    }

    // CHAT-03 & CHAT-09: Mở chat cũ, tải lịch sử theo thời gian
    public Map<String, Object> getConversationDetail(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập"));

        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        Map<String, Object> response = new HashMap<>();
        response.put("conversation", conversation);
        response.put("messages", messages);
        return response;
    }

    // CHAT-04: Đổi tên chat (Validate & Sanitize title)
    public Conversation updateConversationTitle(Long userId, Long conversationId, String newTitle) {
        Conversation conversation = conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập"));

        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống");
        }
        if (newTitle.trim().length() > 100) {
            throw new IllegalArgumentException("Tiêu đề không được quá 100 ký tự");
        }

        conversation.setTitle(sanitizeTitle(newTitle));
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    // CHAT-05: Xóa chat (Soft Delete)
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập"));

        conversation.setIsDeleted(true);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    // CHAT-06: Tìm kiếm chat (Search trong phạm vi user hiện tại, giới hạn độ dài keyword)
    public List<Conversation> searchConversations(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanKeyword = keyword.trim();
        if (cleanKeyword.length() > 50) {
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 50 ký tự");
        }
        return conversationRepository.searchByUserIdAndKeyword(userId, cleanKeyword);
    }

    @Autowired
    private com.example.studyassistant.retrieval.service.GroundedAnswerService groundedAnswerService;

    // CHAT-07 & CHAT-09: Hỏi dựa trên tài liệu trong Notebook + Lưu nguồn trích dẫn
    public ChatMessage sendMessage(Long userId, Long conversationId, String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            throw new IllegalArgumentException("Câu hỏi không được để trống");
        }

        Conversation conversation = conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập"));

        // 1. Lưu tin nhắn USER
        ChatMessage userMsg = new ChatMessage();
        userMsg.setConversation(conversation);
        userMsg.setSender("USER");
        userMsg.setContent(userQuestion.trim());
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userMsg);

        // 2. CP7: Dùng GroundedAnswerService cho chat có Notebook
        String aiAnswer;
        String sourcesJson = "[]";
        
        if (conversation.getNotebook() != null) {
            com.example.studyassistant.retrieval.model.GroundedAnswer groundedAnswer = groundedAnswerService.answer(userId, conversation.getNotebook().getId(), userQuestion);
            aiAnswer = groundedAnswer.getAnswer();
            
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                sourcesJson = mapper.writeValueAsString(groundedAnswer.getCitations());
            } catch (Exception e) {
                sourcesJson = "[]";
            }
        } else {
            // Free chat fallback
            if (openaiApiKey != null && !openaiApiKey.trim().isEmpty()) {
                aiAnswer = callOpenAi(userQuestion);
            } else {
                aiAnswer = "Chào bạn! Tôi đã nhận được câu hỏi: \"" + userQuestion + "\".";
            }
        }

        // 4. Lưu tin nhắn ASSISTANT + sourcesJson
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setConversation(conversation);
        assistantMsg.setSender("ASSISTANT");
        assistantMsg.setContent(aiAnswer);
        assistantMsg.setSourcesJson(sourcesJson);
        assistantMsg.setCreatedAt(LocalDateTime.now());

        // Cập nhật updatedAt cho Conversation
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return chatMessageRepository.save(assistantMsg);
    }

    // CHAT-08: Click nguồn trong câu trả lời (Verify document ownership & authorization)
    public Map<String, Object> getCitationLocation(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền truy cập tài liệu trích dẫn này"));

        Map<String, Object> citation = new HashMap<>();
        citation.put("documentId", doc.getId());
        citation.put("title", doc.getFileName());
        citation.put("totalPages", doc.getTotalPages() != null ? doc.getTotalPages() : 1);
        citation.put("viewUrl", "/api/documents/" + doc.getId() + "/view");
        return citation;
    }

    private String sanitizeTitle(String title) {
        return title.replaceAll("<[^>]*>", "").trim(); // Xóa thẻ HTML chống XSS
    }

    private int getConversationQuotaByPlan(String plan) {
        if ("PREMIUM".equalsIgnoreCase(plan)) return 500;
        if ("BASIC".equalsIgnoreCase(plan)) return 100;
        return 20; // FREE plan tối đa 20 cuộc trò chuyện
    }

    private String callOpenAi(String prompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.openai.com/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");

            Map<String, String> messageObj = new HashMap<>();
            messageObj.put("role", "user");
            messageObj.put("content", prompt);

            requestBody.put("messages", Collections.singletonList(messageObj));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseEntity.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    if (msg != null) return msg.get("content").toString();
                }
            }
        } catch (Exception e) {
            return "Rất tiếc, đã xảy ra lỗi khi kết nối với AI Server: " + e.getMessage();
        }
        return "Không nhận được phản hồi từ AI Server.";
    }
}
