package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.service.GroundedAnswerService;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AiController {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @Autowired
    private GroundedAnswerService groundedAnswerService;

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

        if (!(payload.get("docIds") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn tài liệu để hỏi đáp."));
        }
        List<Long> docIds;
        try {
            docIds = rawIds.stream().map(value -> Long.valueOf(Objects.toString(value))).distinct().toList();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã tài liệu không hợp lệ."));
        }
        for (Long docId : docIds) {
            Optional<Document> document = documentRepository.findByIdAndUserId(docId, userId);
            if (document.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy tài liệu đã chọn."));
            }
        }
        try {
            GroundedAnswer answer = groundedAnswerService.answer(userId, null, message, docIds);
            return ResponseEntity.ok(Map.of("response", answer.getAnswer(), "docId", docIds.get(0),
                    "sources", Optional.ofNullable(answer.getCitations()).orElseGet(Collections::emptyList)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Document chat failed for user {} and documents {}", userId, docIds, e);
            return ResponseEntity.status(500).body(Map.of("error", "Chưa thể tạo câu trả lời từ tài liệu."));
        }
    }

    @GetMapping("/summary/{docId}")
    public ResponseEntity<?> summarize(@PathVariable Long docId, HttpServletRequest request) {
        return documentSummaryController.getSummary(docId, request);
    }

}
