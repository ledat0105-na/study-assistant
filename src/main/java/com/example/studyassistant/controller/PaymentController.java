package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Payment;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.PaymentRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/request")
    public ResponseEntity<?> requestUpgrade(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String plan = payload.get("plan").toString(); // "STUDENT", "PRO"
        Long amount = Long.valueOf(payload.get("amount").toString());
        String paymentNote = payload.get("paymentNote") != null ? payload.get("paymentNote").toString() : "";

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        Payment payment = new Payment();
        payment.setUser(userOpt.get());
        payment.setPlan(plan);
        payment.setAmount(amount);
        payment.setStatus("PENDING");
        payment.setPaymentNote(paymentNote);
        payment.setCreatedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Payment>> getPendingPayments() {
        return ResponseEntity.ok(paymentRepository.findByStatus("PENDING"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approvePayment(@PathVariable Long id) {
        Optional<Payment> paymentOpt = paymentRepository.findById(id);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        payment.setStatus("APPROVED");
        paymentRepository.save(payment);

        // Update user role/tier
        User user = payment.getUser();
        user.setRole(payment.getPlan()); // Set to "STUDENT" or "PRO"
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Payment approved and tier upgraded successfully"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectPayment(@PathVariable Long id) {
        Optional<Payment> paymentOpt = paymentRepository.findById(id);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        payment.setStatus("REJECTED");
        paymentRepository.save(payment);

        return ResponseEntity.ok(Map.of("message", "Payment request rejected"));
    }
}
