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

    @Autowired
    private com.example.studyassistant.service.QuotaService quotaService;

    @Autowired private com.example.studyassistant.billing.MonthlyQuotaService monthlyQuotaService;
    @Autowired private java.time.Clock billingClock;

    @org.springframework.beans.factory.annotation.Value("${payment.webhook.secret:}")
    private String webhookSecret;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    // Bắt buộc phải là ADMIN mới được sửa/xóa bảng giá
    private void requireAdmin(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            throw new IllegalStateException("Chưa đăng nhập");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Chưa đăng nhập"));
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Quyền truy cập bị từ chối. API này chỉ dành cho Admin!");
        }
    }

    // Seed an empty catalog only; viewing prices must never overwrite admin edits.
    private List<PricingPlan> ensureSeedPricingPlans() {
        List<PricingPlan> dbPlans = pricingPlanRepository.findAll();

        if (dbPlans.isEmpty()) {
            Map<String,PricingPlan> defaults=com.example.studyassistant.billing.BillingController.catalog().stream()
                    .collect(java.util.stream.Collectors.toMap(PricingPlan::getPlanCode,java.util.function.Function.identity()));

            for (PricingPlan def : defaults.values()) {
                Optional<PricingPlan> existingOpt = pricingPlanRepository.findByPlanCode(def.getPlanCode());
                if (existingOpt.isEmpty()) {
                    pricingPlanRepository.save(def);
                }
            }
            return pricingPlanRepository.findAll();
        }
        return dbPlans;
    }


    // PLAN-01: Xem danh sách các gói dịch vụ và bảng giá công khai từ CSDL
    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        return ResponseEntity.ok(com.example.studyassistant.billing.BillingController.catalog());
    }

    // PLAN-ADMIN-01: Xem tất cả các gói dịch vụ (dành cho Admin)
    @GetMapping("/plans/admin")
    public ResponseEntity<?> getAllPlansAdmin(HttpServletRequest request) {
        try {
            requireAdmin(request);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        ensureSeedPricingPlans();
        return ResponseEntity.ok(pricingPlanRepository.findAll());
    }

    // PLAN-ADMIN-02: Thêm mới hoặc Cập nhật bảng giá (bao gồm Sale, Khuyến mãi)
    @PostMapping("/plans")
    public ResponseEntity<?> saveOrUpdatePlan(@RequestBody PricingPlan plan, HttpServletRequest request) {
        try {
            requireAdmin(request);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
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
    public ResponseEntity<?> deletePlan(@PathVariable Long id, HttpServletRequest request) {
        try {
            requireAdmin(request);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
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
        @SuppressWarnings("unchecked")
        Map<String,Object> monthly=(Map<String,Object>)monthlyQuotaService.status(userId).get("features");
        long usedDocs=((Number)((Map<?,?>)monthly.get("UPLOAD")).get("used")).longValue();
        long usedConversations = conversationRepository.countByUserIdAndIsDeletedFalse(userId);

        String canonical=com.example.studyassistant.billing.PlanPolicy.normalize(plan);
        int maxNotebooks=canonical.equals("VIP")?-1:canonical.equals("PRO")?50:3;
        int maxDocs=com.example.studyassistant.billing.PlanPolicy.limit(canonical,"UPLOAD");
        int maxConversations=canonical.equals("VIP")?-1:canonical.equals("PRO")?500:20;

        Map<String, Object> response = new HashMap<>();
        response.put("currentPlan", plan);
        response.put("notebooks", Map.of("used", usedNotebooks, "max", maxNotebooks));
        response.put("documents", Map.of("used", usedDocs, "max", maxDocs));
        response.put("conversations", Map.of("used", usedConversations, "max", maxConversations));

        return ResponseEntity.ok(response);
    }

    // PLAN-02 & PAY-01: Tạo yêu cầu thanh toán nâng cấp (lấy giá sale real-time từ DB)
    @PostMapping("/payments/checkout")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> checkoutUpgrade(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }

        String transactionId = body.get("transactionId");
        if (transactionId != null) {
            Long id;
            try {
                id = Long.valueOf(transactionId);
            } catch (NumberFormatException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã yêu cầu không hợp lệ."));
            }
            var existing = paymentRepository.findByIdForUpdate(id)
                    .filter(payment -> userId.equals(payment.getUser().getId()));
            if (existing.isEmpty()) return ResponseEntity.notFound().build();
            Payment payment = existing.get();
            if (!"PENDING".equals(payment.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "Yêu cầu đã được xử lý. Vui lòng tải lại trang."));
            }
            return savePaymentDetails(payment, body, true);
        }

        String planId = body.get("planId");
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn gói dịch vụ"));
        }

        String targetPlanCode = planId.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PRO","VIP").contains(targetPlanCode)) return ResponseEntity.badRequest().body(Map.of("error","Chỉ hỗ trợ nâng cấp PRO hoặc VIP."));
        int durationMonths;
        long realPrice;
        try {durationMonths=Integer.parseInt(body.getOrDefault("months","1"));realPrice=com.example.studyassistant.billing.PlanPolicy.price(targetPlanCode,durationMonths);}
        catch(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()));}

        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        if (!"USER".equals(user.getRole())) return ResponseEntity.status(403).build();
        var pending = paymentRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, "PENDING");
        if (pending.isPresent()) return ResponseEntity.ok(paymentStatus(pending.get()));

        // Tạo PENDING Payment Transaction
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setPlan(targetPlanCode);
        payment.setAmount(realPrice);
        payment.setDurationMonths(durationMonths);
        payment.setStatus("PENDING");
        payment.setCreatedAt(LocalDateTime.now());
        return savePaymentDetails(payment, body, false);
    }

    private ResponseEntity<?> savePaymentDetails(Payment payment, Map<String, String> body, boolean existing) {
        String method = switch (Objects.toString(body.get("paymentMethod"), "")) {
            case "ZALOPAY" -> "ZaloPay";
            case "MOMO" -> "MoMo";
            case "SHOPEEPAY" -> "ShopeePay";
            case "BANK" -> "Ngân hàng";
            default -> "";
        };
        String note = Objects.toString(body.get("paymentNote"), "").strip();
        if (note.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ghi chú đối soát tối đa 200 ký tự."));
        }
        if (!existing || !note.isEmpty() || payment.getPaymentNote() == null) {
            String description = "Thanh toán nâng cấp gói " + payment.getPlan() + " cho " + payment.getUser().getUsername();
            payment.setPaymentNote((method.isEmpty() ? "" : method + " · ") + description
                    + (note.isEmpty() ? "" : " · " + note));
        }
        Map<String, Object> result = new HashMap<>(paymentStatus(paymentRepository.save(payment)));
        if (!existing) {
            result.put("message", "Vui lòng hoàn tất chuyển khoản. Hệ thống sẽ xác minh để kích hoạt gói.");
        }
        return ResponseEntity.ok(result);
    }

    // PAY-01 Webhook / Callback xác minh thanh toán chuẩn Server-side (Transactional & Audit log)
    @PostMapping("/payments/webhook/verify")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> verifyPaymentWebhook(
            @RequestBody Map<String, Object> webhookData,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {

        if (signature == null || webhookSecret == null || webhookSecret.isBlank()
                || !java.security.MessageDigest.isEqual(
                        signature.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chữ ký webhook không hợp lệ"));
        }

        Long transactionId;
        String status;
        try {
            transactionId = Long.valueOf(String.valueOf(webhookData.get("transactionId")));
            status = String.valueOf(webhookData.get("status"));
            if (transactionId <= 0 || !("SUCCESS".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dữ liệu webhook không hợp lệ."));
            }
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã giao dịch không hợp lệ."));
        }
        if ("SUCCESS".equalsIgnoreCase(status)) {
            return paymentRepository.existsById(transactionId)
                    ? ResponseEntity.accepted().body(Map.of("status", "PENDING_ADMIN_REVIEW"))
                    : ResponseEntity.notFound().build();
        }
        return settlePayment(transactionId, status, request, "PAYMENT_WEBHOOK", "PLAN_UPGRADE_WEBHOOK");
    }

    private ResponseEntity<?> settlePayment(Long transactionId, String status, HttpServletRequest request,
                                           String actor, String action) {
        Optional<Payment> paymentOpt = paymentRepository.findByIdForUpdate(transactionId);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        if (!"PENDING".equals(payment.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Giao dịch đã được xử lý trước đó"));
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            User user = userRepository.findByIdForUpdate(payment.getUser().getId()).orElseThrow();
            if (!"USER".equals(user.getRole())) {
                payment.setStatus("REJECTED");
                return ResponseEntity.status(409).body(Map.of("error", "Tài khoản quản trị không sử dụng gói dịch vụ."));
            }
            String oldPlan = user.getPlan();
            payment.setStatus("APPROVED");

            // Activate Subscription & Cập nhật Plan cho User
            user.setPlan(payment.getPlan());
            userRepository.save(user);

            Subscription sub = subscriptionRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Subscription s = new Subscription();
                        s.setUser(user);
                        return s;
                    });
            sub.setAutoRenew(false);
            LocalDateTime activatedAt=LocalDateTime.ofInstant(billingClock.instant(),java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            LocalDateTime startsAt="ACTIVE".equals(sub.getStatus()) && payment.getPlan().equals(sub.getPlan())
                    && sub.getEndDate()!=null && sub.getEndDate().isAfter(activatedAt) ? sub.getEndDate() : activatedAt;
            sub.setPlan(payment.getPlan());
            sub.setStartDate(activatedAt);
            sub.setEndDate(startsAt.plusMonths(payment.getDurationMonths()==null ? 1 : payment.getDurationMonths()));
            sub.setStatus("ACTIVE");
            subscriptionRepository.save(sub);

            // Ghi nhận Audit Log cho thao tác nhạy cảm nâng cấp gói
            AuditLog auditLog = new AuditLog();
            auditLog.setActor(actor);
            auditLog.setAction(action);
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

    @GetMapping("/payments/pending")
    public ResponseEntity<?> pendingPayments(HttpServletRequest request) {
        try { requireAdmin(request); }
        catch (SecurityException ex) { return ResponseEntity.status(403).build(); }
        catch (IllegalStateException ex) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok(paymentRepository.findByStatus("PENDING"));
    }

    @PostMapping("/payments/{id}/approve")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> approvePayment(@PathVariable Long id, HttpServletRequest request) {
        return settleAsAdmin(id, "SUCCESS", request);
    }

    @PostMapping("/payments/{id}/reject")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> rejectPayment(@PathVariable Long id, HttpServletRequest request) {
        return settleAsAdmin(id, "FAILED", request);
    }

    private ResponseEntity<?> settleAsAdmin(Long id, String status, HttpServletRequest request) {
        try { requireAdmin(request); }
        catch (SecurityException ex) { return ResponseEntity.status(403).build(); }
        catch (IllegalStateException ex) { return ResponseEntity.status(401).build(); }
        String actor = userRepository.findById(getCurrentUserId(request)).orElseThrow().getUsername();
        return settlePayment(id, status, request, actor, "PLAN_UPGRADE_ADMIN");
    }

    private Map<String, Object> paymentStatus(Payment payment) {
        return Map.of("transactionId", payment.getId(), "status", payment.getStatus(), "plan", payment.getPlan(),
                "amount", payment.getAmount(), "months", payment.getDurationMonths());
    }

    @GetMapping("/payments/mine/latest")
    public ResponseEntity<?> latestPayment(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) return ResponseEntity.status(401).build();
        var pending = paymentRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, "PENDING");
        return pending.or(() -> paymentRepository.findFirstByUserIdOrderByIdDesc(userId))
                .map(payment -> ResponseEntity.ok(paymentStatus(payment)))
                .orElseGet(() -> ResponseEntity.noContent().build());
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
