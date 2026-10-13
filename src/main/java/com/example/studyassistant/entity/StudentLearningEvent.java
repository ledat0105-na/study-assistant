package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_learning_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_idempotency", columnNames = {"user_id", "idempotency_key", "concept_id"})
})
@Getter
@Setter(AccessLevel.NONE) // Enforce append-only conceptually, no setters after creation
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentLearningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "normalized_score", nullable = false)
    private Double normalizedScore;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public StudentLearningEvent(User user, Concept concept, String idempotencyKey, String eventType, 
                                Double normalizedScore, String sourceType, String sourceId, LocalDateTime occurredAt) {
        this.user = user;
        this.concept = concept;
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.normalizedScore = normalizedScore;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
    }
}
