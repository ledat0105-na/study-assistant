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

    @Autowired
    private com.example.studyassistant.service.PasswordResetService passwordResetService;

    // userId chỉ lấy từ session đã đăng nhập, KHÔNG tin header X-User-Id từ client
    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    // ACC-01: Đăng ký tài khoản
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody User user, HttpServletRequest request) {
        try {
            User registered = userService.register(user);
            HttpSession previousSession = request.getSession(false);
            if (previousSession != null) previousSession.invalidate();
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
            request.changeSessionId();
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

    public record ProfileUpdate(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=150) String name,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max=150) String email) {}

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) return ResponseEntity.status(401).build();
        try { return ResponseEntity.ok(userService.getUserById(userId)); }
        catch (IllegalArgumentException ex) { return ResponseEntity.status(401).build(); }
    }

    @PatchMapping("/auth/profile")
    public ResponseEntity<?> updateProfile(@jakarta.validation.Valid @RequestBody ProfileUpdate body,
                                           HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) return ResponseEntity.status(401).build();
        User profile = new User();
        profile.setFullName(body.name()); profile.setEmail(body.email());
        try { return ResponseEntity.ok(userService.updateProfile(userId, profile)); }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(Map.of("error", "Email đã được sử dụng."));
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

    public record ResetEmail(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max=150) String email) {}
    public record ResetPassword(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max=150) String email,
            @jakarta.validation.constraints.Pattern(regexp="[0-9]{6}") @jakarta.validation.constraints.NotBlank String otp,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=72) String password) {}

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<?> forgotPassword(@jakarta.validation.Valid @RequestBody ResetEmail body,HttpServletRequest request) {
        passwordResetService.limit(request.getRemoteAddr());
        passwordResetService.request(body.email());
        return ResponseEntity.ok(Map.of("success",true,"message",
            "Nếu email đã đăng ký, mã OTP sẽ được gửi tới hộp thư. Vui lòng kiểm tra cả thư rác.","retryAfterSeconds",60,"expiresInSeconds",com.example.studyassistant.service.PasswordResetService.OTP_TTL_SECONDS));
    }
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@jakarta.validation.Valid @RequestBody ResetPassword body,HttpServletRequest request) {
        passwordResetService.limit(request.getRemoteAddr());
        try {
            passwordResetService.reset(body.email(),body.otp(),body.password());
            var session=request.getSession(false);if(session!=null)session.invalidate();
            return ResponseEntity.ok(Map.of("success",true,"message","Đã đổi mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới."));
        } catch(IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success",false,"error",ex.getMessage()));
        }
    }
}
