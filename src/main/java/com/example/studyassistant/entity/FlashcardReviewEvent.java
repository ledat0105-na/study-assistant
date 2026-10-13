package com.example.studyassistant.entity;

import com.example.studyassistant.domain.flashcard.FlashcardRating;
import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.util.UtcInstantAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "flashcard_review_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_flashcard_review_event_user_token", columnNames = {"user_id", "review_token"})
}, indexes = {
        @Index(name = "idx_flashcard_review_event_card_time", columnList = "user_id, flashcard_id, reviewed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class FlashcardReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @Column(name = "review_token", nullable = false, length = 36)
    private String reviewToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private FlashcardRating rating;

    @Column(name = "reviewed_at", nullable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_before", length = 20)
    private FlashcardScheduleStatus stateBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_after", nullable = false, length = 20)
    private FlashcardScheduleStatus stateAfter;

    @Column(name = "step_before")
    private Integer stepBefore;

    @Column(name = "step_after")
    private Integer stepAfter;

    @Column(name = "difficulty_before")
    private Double difficultyBefore;

    @Column(name = "difficulty_after", nullable = false)
    private Double difficultyAfter;

    @Column(name = "stability_before")
    private Double stabilityBefore;

    @Column(name = "stability_after", nullable = false)
    private Double stabilityAfter;

    @Column(name = "due_before")
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant dueBefore;

    @Column(name = "due_after", nullable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant dueAfter;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "scheduler_version", nullable = false, length = 20)
    private String schedulerVersion;

    @Column(name = "scheduler_config_version", nullable = false, length = 50)
    private String schedulerConfigVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
