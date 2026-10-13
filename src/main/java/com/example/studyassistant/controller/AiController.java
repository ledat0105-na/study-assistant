package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
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
    private UserRepository userRepository;

    @Autowired
    private AiService aiService;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @Autowired
    private com.example.studyassistant.retrieval.service.GroundedAnswerService groundedAnswerService;

    @Autowired
    private DocumentSummaryController documentSummaryController;

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
        if (docIdsRaw != null && !docIdsRaw.isEmpty()) {
            try {
                firstDocId = Long.valueOf(docIdsRaw.get(0).toString());
            } catch (Exception e) {}
        }

        if (firstDocId != null) {
            // CP7: Use GroundedAnswerService for document RAG
            // Wait, GroundedAnswerService currently takes (userId, notebookId, question).
            // But here we have docId instead of notebookId.
            // Let's fallback to finding the notebookId of the document.
            Optional<Document> docOpt = documentRepository.findByIdAndUserId(firstDocId, userId);
            if (docOpt.isPresent() && docOpt.get().getNotebook() != null) {
                try {
                    com.example.studyassistant.retrieval.model.GroundedAnswer answer = groundedAnswerService.answer(userId, docOpt.get().getNotebook().getId(), message);
                    Map<String, Object> result = new HashMap<>();
                    result.put("response", answer.getAnswer());
                    result.put("docId", firstDocId);
                    
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        result.put("sources", mapper.convertValue(answer.getCitations(), List.class));
                    } catch (Exception e) {}
                    
                    return ResponseEntity.ok(result);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
                } catch (Exception e) {
                    return ResponseEntity.status(500).body(Map.of("error", "Error connecting to GroundedAnswerService: " + e.getMessage()));
                }
            }
        }
        
        // Fallback for non-RAG chat or missing notebook
        return ResponseEntity.status(400).body(Map.of("error", "Tài liệu chưa được xử lý để trò chuyện (thiếu Notebook)."));
    }

    @GetMapping("/summary/{docId}")
    public ResponseEntity<?> summarize(@PathVariable Long docId, HttpServletRequest request) {
        return documentSummaryController.getSummary(docId, request);
    }

}
