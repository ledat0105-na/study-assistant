package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity @Table(name="password_reset_challenges") @Data
public class PasswordResetChallenge {
    @Id private Long userId;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="userId", insertable=false, updatable=false)
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
    @Column(nullable=false, length=100) private String digest;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private Instant nextSendAt;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private Instant windowStart;
    @Column(nullable=false) private int sentInWindow;
}
