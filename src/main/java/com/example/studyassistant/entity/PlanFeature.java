package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plan_features")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String planName; // "FREE", "BASIC", "PREMIUM"

    @Column(nullable = false)
    private Integer maxNotebooks;

    @Column(nullable = false)
    private Integer dailyChatLimit;

    @Column(nullable = false)
    private Integer dailyQuizLimit;

    @Column(nullable = false)
    private Integer dailyFlashcardLimit;

    @Column(nullable = false)
    private Integer monthlyUploadLimit;

    @Column(nullable = false)
    private Long maxStorageBytes; // Ví dụ: 100MB, 2GB, 10GB tính theo Byte

    @Column(nullable = false)
    private Integer chatRetentionDays; // 7 ngày, 180 ngày (6 tháng), 3650 ngày (không giới hạn)
}
