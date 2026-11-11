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
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    public String getRole() {
        return switch (role == null ? "" : role.toUpperCase(java.util.Locale.ROOT)) {
            case "STUDENT", "TEACHER", "" -> "USER";
            default -> role.toUpperCase(java.util.Locale.ROOT);
        };
    }

    @PrePersist
    @PreUpdate
    private void normalizeRole() {
        role = getRole();
    }

    @Column(nullable = false)
    private String plan = "FREE"; // FREE / PRO / VIP; legacy BASIC/PREMIUM are normalized

    @Column(nullable = false)
    private String status = "ACTIVE"; // "ACTIVE", "LOCKED", "SUSPENDED"

    @Column(name = "reset_token")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String resetToken;

    @Column(name = "reset_token_expiry")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private LocalDateTime resetTokenExpiry;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;
}
