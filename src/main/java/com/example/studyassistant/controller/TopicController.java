package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.StudyProgress;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.StudyProgressRepository;
import com.example.studyassistant.repository.TopicRepository;
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
@RequestMapping("/api/topics")
@CrossOrigin(origins = "*")
public class TopicController {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

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

    // KM-01: Xem cây kiến thức của tài liệu (Kiểm tra Document thuộc currentUser)
    @GetMapping("/document/{docId}")
    public ResponseEntity<?> getTopicsByDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        // Validate Ownership: Document phải thuộc sở hữu của currentUser
        Optional<Document> docOpt = documentRepository.findByIdAndUserId(docId, userId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập Knowledge Map"));
        }

        List<Topic> rootTopics = topicRepository.findByDocumentIdAndParentIsNull(docId);
        return ResponseEntity.ok(rootTopics);
    }

    // KM-02: Mở rộng/thu gọn nhánh (Dữ liệu node chỉ từ API authorized)
    @GetMapping("/{topicId}/subtopics")
    public ResponseEntity<?> getSubTopics(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        // Kiểm tra Topic->Document->User Ownership
        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền truy cập Topic này"));
        }

        return ResponseEntity.ok(topic.getChildren());
    }

    // KM-03: Click Topic để mở đúng vị trí tài liệu (Backend kiểm tra Topic->Document->Owner rồi mới trả vị trí)
    @GetMapping("/{topicId}/location")
    public ResponseEntity<?> getTopicLocation(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền truy cập vị trí tài liệu của Topic này"));
        }

        Map<String, Object> location = new HashMap<>();
        location.put("topicId", topic.getId());
        location.put("topicName", topic.getName());
        location.put("documentId", topic.getDocument().getId());
        location.put("documentTitle", topic.getDocument().getFileName());
        location.put("pageStart", topic.getPageStart() != null ? topic.getPageStart() : 1);
        location.put("pageEnd", topic.getPageEnd() != null ? topic.getPageEnd() : 1);

        return ResponseEntity.ok(location);
    }

    // KM-04: Xem trạng thái Topic đang học (Progress tính server-side theo user+topic)
    @GetMapping("/{topicId}/progress")
    public ResponseEntity<?> getTopicProgress(@PathVariable Long topicId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic không tồn tại"));

        if (topic.getDocument() == null || !topic.getDocument().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền xem tiến độ của Topic này"));
        }

        Optional<StudyProgress> progressOpt = studyProgressRepository.findByUserIdAndTopicId(userId, topicId);
        if (progressOpt.isPresent()) {
            return ResponseEntity.ok(progressOpt.get());
        } else {
            // Mặc định trả về NOT_STARTED nếu chưa có tiến độ ghi nhận
            Map<String, Object> defaultProgress = new HashMap<>();
            defaultProgress.put("userId", userId);
            defaultProgress.put("topicId", topicId);
            defaultProgress.put("status", "NOT_STARTED");
            defaultProgress.put("progress", 0.0);
            return ResponseEntity.ok(defaultProgress);
        }
    }
}
