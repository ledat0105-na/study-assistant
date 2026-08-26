package com.example.studyassistant;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SecurityIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Test ACC-01: Chống privilege escalation khi đăng ký tài khoản")
    public void testRegisterDefaultRoleAndPlan() {
        User req = new User();
        req.setFullName("Test Student");
        req.setUsername("teststudent_" + System.currentTimeMillis());
        req.setEmail("teststudent_" + System.currentTimeMillis() + "@test.com");
        req.setPassword("Password123!");
        req.setRole("ADMIN"); // Hack cố gắng tự gán ADMIN
        req.setPlan("PREMIUM"); // Hack cố gắng tự nâng gói PREMIUM

        User saved = userService.register(req);

        // Kiểm tra Backend phải ép buộc role STUDENT và plan FREE
        Assertions.assertEquals("STUDENT", saved.getRole());
        Assertions.assertEquals("FREE", saved.getPlan());
        Assertions.assertEquals("ACTIVE", saved.getStatus());
        Assertions.assertTrue(saved.getPassword().startsWith("$2a$") || saved.getPassword().startsWith("$2b$"));
    }

    @Test
    @DisplayName("Test ACC-02: Rate limit khóa tài khoản khi đăng nhập sai nhiều lần")
    public void testLoginRateLimiting() {
        String username = "ratelimit_user_" + System.currentTimeMillis();
        User req = new User();
        req.setFullName("Rate Limit User");
        req.setUsername(username);
        req.setEmail(username + "@test.com");
        req.setPassword("CorrectPassword123!");
        userService.register(req);

        // Thử đăng nhập sai 5 lần
        for (int i = 0; i < 5; i++) {
            try {
                userService.login(username, "WrongPassword");
            } catch (Exception ignored) {}
        }

        // Lần thứ 6 phải ném ra lỗi tài khoản bị khóa tạm thời
        Assertions.assertThrows(IllegalStateException.class, () -> {
            userService.login(username, "CorrectPassword123!");
        });
    }
}
