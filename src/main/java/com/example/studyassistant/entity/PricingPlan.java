package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, unique = true)
    private String planCode; // "FREE", "STUDENT", "PRO"

    @Column(nullable = false)
    private String name;

    @Column(name = "original_price", nullable = false)
    private Double originalPrice;

    @Column(name = "sale_price", nullable = false)
    private Double salePrice;

    @Column(name = "billing_cycle")
    private String billingCycle = "tháng";

    @Column(name = "is_popular")
    private Boolean isPopular = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "badge_text")
    private String badgeText;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String features; // Separate features by '|'

    @Column(name = "document_limit")
    private Integer documentLimit = 3;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
