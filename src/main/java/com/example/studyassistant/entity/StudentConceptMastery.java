package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_concept_mastery", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mastery_user_concept", columnNames = {"user_id", "concept_id"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentConceptMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    @Column(name = "mastery_estimate", nullable = false)
    private Double masteryEstimate = 0.0;

    @Column(name = "evidence_count", nullable = false)
    private Integer evidenceCount = 0;

    @Column(name = "last_evidence_at")
    private LocalDateTime lastEvidenceAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public StudentConceptMastery(User user, Concept concept, Double masteryEstimate, Integer evidenceCount) {
        this.user = user;
        this.concept = concept;
        this.masteryEstimate = masteryEstimate != null ? masteryEstimate : 0.0;
        this.evidenceCount = evidenceCount != null ? evidenceCount : 0;
    }
}
