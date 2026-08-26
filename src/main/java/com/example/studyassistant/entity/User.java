package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name")
    @com.fasterxml.jackson.annotation.JsonProperty("name")
    private String fullName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "STUDENT"; // "STUDENT" or "ADMIN"

    @Column(nullable = false)
    private String plan = "FREE"; // "FREE", "BASIC", "PREMIUM"

    @Column(nullable = false)
    private String status = "ACTIVE"; // "ACTIVE", "LOCKED", "SUSPENDED"

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;
}
