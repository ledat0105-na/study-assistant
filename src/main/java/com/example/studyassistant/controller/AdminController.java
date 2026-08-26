package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.DocumentService;
import com.example.studyassistant.util.HashUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DocumentService documentService;

    // Helper kiểm tra quyền ADMIN
    private User verifyAdminRole(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = null;
        if (session != null && session.getAttribute("userId") != null) {
            userId = (Long) session.getAttribute("userId");
        } else {
            String headerUserId = request.getHeader("X-User-Id");
            if (headerUserId != null && !headerUserId.isEmpty()) {
                try {
                    userId = Long.parseLong(headerUserId);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (userId == null) {
            throw new IllegalStateException("Chưa đăng nhập");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Quyền truy cập bị từ chối. API này chỉ dành cho Admin!");
        }
        return user;
    }

    // ADM-01: Dashboard tổng quan thống kê
    @GetMapping("/dashboard")
    public ResponseEntity<?> getAdminDashboard(HttpServletRequest request) {
        try {
            verifyAdminRole(request);

            long totalUsers = userRepository.count();
            long totalDocs = documentRepository.count();
            long totalConversations = conversationRepository.countByUserIdAndIsDeletedFalse(null);
            long totalQuizzesTaken = quizResultRepository.count();

            long totalRevenue = paymentRepository.findAll().stream()
                    .filter(p -> "APPROVED".equalsIgnoreCase(p.getStatus()))
                    .mapToLong(p -> p.getAmount() != null ? p.getAmount() : 0L)
                    .sum();

            long failedDocs = documentRepository.findAll().stream()
                    .filter(d -> "FAILED".equalsIgnoreCase(d.getStatus()))
                    .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalDocuments", totalDocs);
            stats.put("totalConversations", totalConversations);
            stats.put("totalQuizzesTaken", totalQuizzesTaken);
            stats.put("totalRevenue", totalRevenue);
            stats.put("failedProcessingDocs", failedDocs);

            return ResponseEntity.ok(stats);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-02: Danh sách User
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            HttpServletRequest request) {

        try {
            verifyAdminRole(request);

            List<User> users = userRepository.findAll();
            List<Map<String, Object>> sanitizedList = users.stream().map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("fullName", u.getFullName());
                map.put("username", u.getUsername());
                map.put("email", u.getEmail());
                map.put("role", u.getRole());
                map.put("plan", u.getPlan());
                map.put("status", u.getStatus());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(sanitizedList);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-USER-CREATE: Admin thêm người dùng mới
    @PostMapping("/users")
    public ResponseEntity<?> createUserByAdmin(@RequestBody User reqUser, HttpServletRequest request) {
        try {
            User admin = verifyAdminRole(request);
            if (reqUser.getUsername() == null || reqUser.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username không được để trống!"));
            }
            if (reqUser.getEmail() == null || reqUser.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email không được để trống!"));
            }
            if (userRepository.findByUsername(reqUser.getUsername().trim()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên đăng nhập đã tồn tại!"));
            }
            if (userRepository.findByEmail(reqUser.getEmail().trim()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email đã tồn tại!"));
            }

            User user = new User();
            user.setFullName(reqUser.getFullName());
            user.setUsername(reqUser.getUsername().trim());
            user.setEmail(reqUser.getEmail().trim());
            String rawPassword = reqUser.getPassword() != null && !reqUser.getPassword().isEmpty() ? reqUser.getPassword() : "123456";
            user.setPassword(HashUtil.hash(rawPassword));
            user.setRole(reqUser.getRole() != null ? reqUser.getRole().toUpperCase() : "STUDENT");
            user.setPlan(reqUser.getPlan() != null ? reqUser.getPlan().toUpperCase() : "FREE");
            user.setStatus("ACTIVE");

            User saved = userRepository.save(user);

            // Audit log
            AuditLog audit = new AuditLog();
            audit.setActor(admin.getUsername());
            audit.setAction("ADMIN_CREATE_USER");
            audit.setOldValue("N/A");
            audit.setNewValue("Created User: " + saved.getUsername() + " Role: " + saved.getRole());
            audit.setIpAddress(request.getRemoteAddr());
            audit.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(audit);

            return ResponseEntity.ok(saved);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-USER-UPDATE: Admin sửa thông tin người dùng (Role, Plan, Name, Email)
    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUserByAdmin(@PathVariable Long userId, @RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            User admin = verifyAdminRole(request);
            User target = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng!"));

            String oldData = "Role: " + target.getRole() + ", Plan: " + target.getPlan() + ", Status: " + target.getStatus();

            if (body.containsKey("fullName")) target.setFullName(body.get("fullName"));
            if (body.containsKey("email")) target.setEmail(body.get("email"));
            if (body.containsKey("role")) target.setRole(body.get("role").toUpperCase());
            if (body.containsKey("plan")) target.setPlan(body.get("plan").toUpperCase());
            if (body.containsKey("status")) target.setStatus(body.get("status").toUpperCase());

            User updated = userRepository.save(target);

            // Audit log
            AuditLog audit = new AuditLog();
            audit.setActor(admin.getUsername());
            audit.setAction("ADMIN_UPDATE_USER");
            audit.setOldValue(oldData);
            audit.setNewValue("Updated User ID: " + userId + " Role: " + updated.getRole() + " Plan: " + updated.getPlan());
            audit.setIpAddress(request.getRemoteAddr());
            audit.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(audit);

            return ResponseEntity.ok(updated);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-USER-DELETE: Admin xóa người dùng khỏi CSDL
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUserByAdmin(@PathVariable Long userId, HttpServletRequest request) {
        try {
            User admin = verifyAdminRole(request);
            if (admin.getId().equals(userId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không thể tự xóa tài khoản Admin đang đăng nhập!"));
            }

            User target = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng!"));
            userRepository.deleteById(userId);

            // Audit log
            AuditLog audit = new AuditLog();
            audit.setActor(admin.getUsername());
            audit.setAction("ADMIN_DELETE_USER");
            audit.setOldValue("User: " + target.getUsername() + " ID: " + userId);
            audit.setNewValue("Deleted User");
            audit.setIpAddress(request.getRemoteAddr());
            audit.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(audit);

            return ResponseEntity.ok(Map.of("message", "Xóa người dùng thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-03: Khóa / Mở khóa User
    @PostMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        try {
            User admin = verifyAdminRole(request);
            String newStatus = body.get("status");
            String reason = body.get("reason");

            if (newStatus == null || (!"ACTIVE".equalsIgnoreCase(newStatus) && !"LOCKED".equalsIgnoreCase(newStatus) && !"SUSPENDED".equalsIgnoreCase(newStatus))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Trạng thái không hợp lệ"));
            }

            User targetUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

            String oldStatus = targetUser.getStatus();
            targetUser.setStatus(newStatus.toUpperCase());
            userRepository.save(targetUser);

            // Audit Log
            AuditLog audit = new AuditLog();
            audit.setActor(admin.getUsername());
            audit.setAction("ADMIN_CHANGE_USER_STATUS");
            audit.setOldValue("User ID: " + userId + " Status: " + oldStatus);
            audit.setNewValue("New Status: " + newStatus + " | Reason: " + (reason != null ? reason : "N/A"));
            audit.setIpAddress(request.getRemoteAddr());
            audit.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(audit);

            return ResponseEntity.ok(Map.of("message", "Đã cập nhật trạng thái người dùng thành công"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-DOCS-ALL: Xem tất cả tài liệu toàn hệ thống
    @GetMapping("/documents")
    public ResponseEntity<?> getAllSystemDocuments(HttpServletRequest request) {
        try {
            verifyAdminRole(request);
            List<Document> docs = documentRepository.findAll();
            return ResponseEntity.ok(docs);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-DOCS-DELETE: Xóa tài liệu khỏi hệ thống
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocumentByAdmin(@PathVariable Long id, HttpServletRequest request) {
        try {
            User admin = verifyAdminRole(request);
            Document doc = documentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu!"));
            documentRepository.deleteById(id);

            // Audit Log
            AuditLog audit = new AuditLog();
            audit.setActor(admin.getUsername());
            audit.setAction("ADMIN_DELETE_DOCUMENT");
            audit.setOldValue("Doc ID: " + id + " Title: " + doc.getFileName());
            audit.setNewValue("Deleted Document");
            audit.setIpAddress(request.getRemoteAddr());
            audit.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(audit);

            return ResponseEntity.ok(Map.of("message", "Đã xóa tài liệu khỏi hệ thống thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // ADM-13: Audit log
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(HttpServletRequest request) {
        try {
            verifyAdminRole(request);
            return ResponseEntity.ok(auditLogRepository.findAll());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
