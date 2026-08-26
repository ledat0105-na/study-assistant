package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String plan = "FREE"; // "FREE", "BASIC", "PREMIUM"

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    @Column(name = "start_date")
    private LocalDateTime startDate = LocalDateTime.now();

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(nullable = false)
    private String status = "ACTIVE"; // "ACTIVE", "CANCELLED", "EXPIRED"
}
