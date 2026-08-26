package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PricingPlanRepository pricingPlanRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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

    // Tự động khởi tạo bảng giá mẫu nếu CSDL chưa có
    private List<PricingPlan> ensureSeedPricingPlans() {
        List<PricingPlan> dbPlans = pricingPlanRepository.findAll();
        if (dbPlans.isEmpty()) {
            PricingPlan free = new PricingPlan(null, "FREE", "Gói Miễn Phí (FREE)", 0.0, 0.0, "vĩnh viễn", false, true, null, "Phù hợp cho trải nghiệm ban đầu", "Tải lên tối đa 3 tài liệu|Sơ đồ kiến thức cơ bản|10 Bài trắc nghiệm AI mỗi tháng", 3, LocalDateTime.now(), LocalDateTime.now());
            PricingPlan student = new PricingPlan(null, "STUDENT", "Gói Sinh Viên (STUDENT)", 79000.0, 49000.0, "tháng", false, true, "Giảm 38%", "Dành cho học sinh sinh viên học tập hàng ngày", "Tải lên tối đa 50 tài liệu|Sơ đồ kiến thức 3D nâng cao|50 Bài trắc nghiệm AI/tháng|Thẻ Flashcards lật 3D không giới hạn", 50, LocalDateTime.now(), LocalDateTime.now());
            PricingPlan pro = new PricingPlan(null, "PRO", "Gói Chuyên Nghiệp (PRO)", 149000.0, 99000.0, "tháng", true, true, "Phổ Biến Nhất - GIẢM 33%", "Dành cho người học tích cực & nghiên cứu chuyên sâu", "Tải lên không giới hạn tài liệu|Sơ đồ kiến thức 3D không giới hạn|Không giới hạn Thẻ Flashcards & Bài thi AI|Phân tích chuyên sâu tiến độ học tập|Ưu tiên xử lý từ AI Model", 9999, LocalDateTime.now(), LocalDateTime.now());

            pricingPlanRepository.saveAll(List.of(free, student, pro));
            return pricingPlanRepository.findAll();
        }
        return dbPlans;
    }

    // PLAN-01: Xem danh sách các gói dịch vụ và bảng giá công khai từ CSDL
    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        ensureSeedPricingPlans();
        List<PricingPlan> activePlans = pricingPlanRepository.findByIsActiveTrueOrderByIdAsc();
        return ResponseEntity.ok(activePlans);
    }

    // PLAN-ADMIN-01: Xem tất cả các gói dịch vụ (dành cho Admin)
    @GetMapping("/plans/admin")
    public ResponseEntity<?> getAllPlansAdmin() {
        ensureSeedPricingPlans();
        return ResponseEntity.ok(pricingPlanRepository.findAll());
    }

    // PLAN-ADMIN-02: Thêm mới hoặc Cập nhật bảng giá (bao gồm Sale, Khuyến mãi)
    @PostMapping("/plans")
    public ResponseEntity<?> saveOrUpdatePlan(@RequestBody PricingPlan plan) {
        if (plan.getPlanCode() == null || plan.getPlanCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã gói planCode không được để trống!"));
        }

        Optional<PricingPlan> existingOpt = pricingPlanRepository.findByPlanCode(plan.getPlanCode().trim().toUpperCase());
        PricingPlan toSave;
        if (existingOpt.isPresent()) {
            toSave = existingOpt.get();
        } else if (plan.getId() != null) {
            toSave = pricingPlanRepository.findById(plan.getId()).orElse(new PricingPlan());
        } else {
            toSave = new PricingPlan();
            toSave.setPlanCode(plan.getPlanCode().trim().toUpperCase());
        }

        if (plan.getName() != null) toSave.setName(plan.getName());
        if (plan.getOriginalPrice() != null) toSave.setOriginalPrice(plan.getOriginalPrice());
        if (plan.getSalePrice() != null) toSave.setSalePrice(plan.getSalePrice());
        if (plan.getBillingCycle() != null) toSave.setBillingCycle(plan.getBillingCycle());
        if (plan.getIsPopular() != null) toSave.setIsPopular(plan.getIsPopular());
        if (plan.getIsActive() != null) toSave.setIsActive(plan.getIsActive());
        if (plan.getBadgeText() != null) toSave.setBadgeText(plan.getBadgeText());
        if (plan.getDescription() != null) toSave.setDescription(plan.getDescription());
        if (plan.getFeatures() != null) toSave.setFeatures(plan.getFeatures());
        if (plan.getDocumentLimit() != null) toSave.setDocumentLimit(plan.getDocumentLimit());

        PricingPlan saved = pricingPlanRepository.save(toSave);
        return ResponseEntity.ok(saved);
    }

    // PLAN-ADMIN-03: Xóa gói dịch vụ
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable Long id) {
        if (!pricingPlanRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        pricingPlanRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Xóa gói bảng giá thành công!"));
    }

    // PLAN-03: Xem quota còn lại (Server-side usage tracking)
    @GetMapping("/plans/quota")
    public ResponseEntity<?> getQuotaUsage(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        User user = userRepository.findById(userId).orElseThrow();
        String plan = user.getPlan() != null ? user.getPlan() : "FREE";

        long usedNotebooks = notebookRepository.countByUserIdAndIsDeletedFalse(userId);
        long usedDocs = documentRepository.countByUserId(userId);
        long usedConversations = conversationRepository.countByUserIdAndIsDeletedFalse(userId);

        int maxNotebooks = "PRO".equalsIgnoreCase(plan) || "PREMIUM".equalsIgnoreCase(plan) ? 100 : ("STUDENT".equalsIgnoreCase(plan) || "BASIC".equalsIgnoreCase(plan) ? 20 : 5);
        int maxDocs = "PRO".equalsIgnoreCase(plan) || "PREMIUM".equalsIgnoreCase(plan) ? 9999 : ("STUDENT".equalsIgnoreCase(plan) || "BASIC".equalsIgnoreCase(plan) ? 50 : 3);
        int maxConversations = "PRO".equalsIgnoreCase(plan) || "PREMIUM".equalsIgnoreCase(plan) ? 500 : ("STUDENT".equalsIgnoreCase(plan) || "BASIC".equalsIgnoreCase(plan) ? 100 : 20);

        Map<String, Object> response = new HashMap<>();
        response.put("currentPlan", plan);
        response.put("notebooks", Map.of("used", usedNotebooks, "max", maxNotebooks));
        response.put("documents", Map.of("used", usedDocs, "max", maxDocs));
        response.put("conversations", Map.of("used", usedConversations, "max", maxConversations));

        return ResponseEntity.ok(response);
    }

    // PLAN-02 & PAY-01: Tạo yêu cầu thanh toán nâng cấp (lấy giá sale real-time từ DB)
    @PostMapping("/payments/checkout")
    public ResponseEntity<?> checkoutUpgrade(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        String planId = body.get("planId");
        if (planId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn gói dịch vụ"));
        }

        String targetPlanCode = planId.toUpperCase();
        PricingPlan pricingPlan = pricingPlanRepository.findByPlanCode(targetPlanCode).orElse(null);

        Long realPrice = 0L;
        if (pricingPlan != null) {
            realPrice = pricingPlan.getSalePrice() != null ? pricingPlan.getSalePrice().longValue() : pricingPlan.getOriginalPrice().longValue();
        } else {
            if ("STUDENT".equalsIgnoreCase(targetPlanCode) || "BASIC".equalsIgnoreCase(targetPlanCode)) realPrice = 49000L;
            else if ("PRO".equalsIgnoreCase(targetPlanCode) || "PREMIUM".equalsIgnoreCase(targetPlanCode)) realPrice = 99000L;
        }

        User user = userRepository.findById(userId).orElseThrow();

        // Tạo PENDING Payment Transaction
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setPlan(targetPlanCode);
        payment.setAmount(realPrice);
        payment.setStatus("PENDING");
        payment.setPaymentNote("Thanh toán nâng cấp gói " + targetPlanCode + " cho " + user.getUsername());
        payment.setCreatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        Map<String, Object> checkoutInfo = new HashMap<>();
        checkoutInfo.put("transactionId", savedPayment.getId());
        checkoutInfo.put("plan", targetPlanCode);
        checkoutInfo.put("amount", realPrice);
        checkoutInfo.put("status", "PENDING");
        checkoutInfo.put("message", "Vui lòng hoàn tất chuyển khoản. Hệ thống sẽ xác minh để kích hoạt gói.");

        return ResponseEntity.ok(checkoutInfo);
    }

    // PAY-01 Webhook / Callback xác minh thanh toán chuẩn Server-side (Transactional & Audit log)
    @PostMapping("/payments/webhook/verify")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> verifyPaymentWebhook(
            @RequestBody Map<String, Object> webhookData,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {

        Long transactionId = Long.valueOf(webhookData.get("transactionId").toString());
        String status = webhookData.get("status").toString(); // "SUCCESS", "FAILED"

        Optional<Payment> paymentOpt = paymentRepository.findById(transactionId);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        if (!"PENDING".equals(payment.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Giao dịch đã được xử lý trước đó"));
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            String oldPlan = payment.getUser().getPlan();
            payment.setStatus("APPROVED");
            paymentRepository.save(payment);

            // Activate Subscription & Cập nhật Plan cho User
            User user = payment.getUser();
            user.setPlan(payment.getPlan());
            userRepository.save(user);

            Subscription sub = subscriptionRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Subscription s = new Subscription();
                        s.setUser(user);
                        return s;
                    });
            sub.setPlan(payment.getPlan());
            sub.setAutoRenew(true);
            sub.setStartDate(LocalDateTime.now());
            sub.setEndDate(LocalDateTime.now().plusDays(30));
            sub.setStatus("ACTIVE");
            subscriptionRepository.save(sub);

            // Ghi nhận Audit Log cho thao tác nhạy cảm nâng cấp gói
            AuditLog auditLog = new AuditLog();
            auditLog.setActor(user.getUsername());
            auditLog.setAction("PLAN_UPGRADE_WEBHOOK");
            auditLog.setOldValue("Plan: " + oldPlan);
            auditLog.setNewValue("Plan: " + payment.getPlan() + " | TransactionID: " + transactionId);
            auditLog.setIpAddress(request.getRemoteAddr());
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

            return ResponseEntity.ok(Map.of("message", "Xác minh thanh toán thành công. Đã kích hoạt gói " + payment.getPlan()));
        } else {
            payment.setStatus("REJECTED");
            paymentRepository.save(payment);
            return ResponseEntity.ok(Map.of("message", "Giao dịch thất bại"));
        }
    }

    // PAY-02: Xem lịch sử giao dịch
    @GetMapping("/payments/history")
    public ResponseEntity<?> getMyPaymentHistory(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        List<Payment> history = paymentRepository.findByUserId(userId);
        return ResponseEntity.ok(history);
    }

    // SUB-01: Hủy gia hạn
    @PostMapping("/subscription/cancel-auto-renew")
    public ResponseEntity<?> cancelAutoRenew(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn chưa đăng ký gói gia hạn nào"));

        sub.setAutoRenew(false);
        subscriptionRepository.save(sub);

        return ResponseEntity.ok(Map.of(
                "message", "Đã tắt tự động gia hạn thành công. Bạn vẫn giữ nguyên quyền lợi gói " + sub.getPlan() + " đến ngày " + (sub.getEndDate() != null ? sub.getEndDate() : "cuối chu kỳ"),
                "autoRenew", false,
                "endDate", sub.getEndDate()
        ));
    }
}
