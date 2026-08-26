package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserService userService;

    // Helper lấy userId từ session/header (hoặc mock logged-in user)
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

    // ACC-01: Đăng ký tài khoản
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            User registered = userService.register(user);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng ký tài khoản thành công",
                "user", registered
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage(), "message", e.getMessage()));
        }
    }

    // ACC-02: Đăng nhập
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String usernameOrEmail = credentials.get("usernameOrEmail");
        String password = credentials.get("password");

        try {
            User user = userService.login(usernameOrEmail, password);
            // Lưu session
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", user.getId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng nhập thành công",
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", e.getMessage(), "message", e.getMessage()));
        }
    }

    // ACC-03: Đăng xuất
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Đăng xuất thành công"));
    }

    // ACC-04: Quên mật khẩu
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String resetToken = userService.generatePasswordResetToken(email);
        if (resetToken != null) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi.",
                "resetToken", resetToken
            ));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));
    }
}
