package com.example.studyassistant.controller;

import com.example.studyassistant.entity.ChatMessage;
import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

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

    // CHAT-01: Tạo đoạn chat mới trong Notebook (Gắn user_id + notebook_id + quota check)
    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Long notebookId = body.get("notebookId") != null ? Long.valueOf(body.get("notebookId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : null;

        try {
            Conversation conversation = chatService.createConversation(userId, notebookId, title);
            return ResponseEntity.ok(conversation);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-02: Xem lịch sử chat của mình (Phân trang)
    @GetMapping("/conversations")
    public ResponseEntity<?> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Page<Conversation> conversations = chatService.getMyConversations(userId, page, size);
        return ResponseEntity.ok(conversations);
    }

    // CHAT-03 & CHAT-09: Mở chat cũ và tải lịch sử tin nhắn
    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> getConversationDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        try {
            Map<String, Object> detail = chatService.getConversationDetail(userId, id);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-04: Đổi tên chat
    @PutMapping("/conversations/{id}/title")
    public ResponseEntity<?> updateConversationTitle(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        String newTitle = body.get("title");
        try {
            Conversation updated = chatService.updateConversationTitle(userId, id, newTitle);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-05: Xóa chat (Soft delete)
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        try {
            chatService.deleteConversation(userId, id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa đoạn chat thành công"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-06: Tìm kiếm chat
    @GetMapping("/search")
    public ResponseEntity<?> searchConversations(@RequestParam String keyword, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        try {
            List<Conversation> results = chatService.searchConversations(userId, keyword);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-07 & CHAT-09: Gửi câu hỏi vào đoạn chat
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        String question = body.get("message");
        try {
            ChatMessage assistantResponse = chatService.sendMessage(userId, id, question);
            return ResponseEntity.ok(assistantResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CHAT-08: Click nguồn trong câu trả lời (Verify document ownership & authorization)
    @GetMapping("/citations/{documentId}")
    public ResponseEntity<?> getCitationLocation(@PathVariable Long documentId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        try {
            Map<String, Object> citation = chatService.getCitationLocation(userId, documentId);
            return ResponseEntity.ok(citation);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
