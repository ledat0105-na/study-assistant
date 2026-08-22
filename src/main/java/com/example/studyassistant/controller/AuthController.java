package com.example.studyassistant.controller;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            User registered = userService.register(user);
            return ResponseEntity.ok(registered);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String usernameOrEmail = credentials.get("usernameOrEmail");
        String password = credentials.get("password");

        if (usernameOrEmail == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username/email and password are required"));
        }

        Optional<User> userOpt = userService.login(usernameOrEmail, password);
        if (userOpt.isPresent()) {
            return ResponseEntity.ok(userOpt.get());
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username/email or password"));
        }
    }
}
