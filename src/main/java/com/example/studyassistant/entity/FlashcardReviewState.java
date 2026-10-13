package com.example.studyassistant.entity;

import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.util.UtcInstantAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "flashcard_review_states", uniqueConstraints = {
        @UniqueConstraint(name = "uq_flashcard_review_state_user_card", columnNames = {"user_id", "flashcard_id"})
}, indexes = {
        @Index(name = "idx_flashcard_review_state_due", columnList = "user_id, due_at, flashcard_id")
})
@Getter
@Setter
@NoArgsConstructor
public class FlashcardReviewState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private FlashcardScheduleStatus state;

    @Column(name = "step")
    private Integer step;

    @Column(name = "difficulty", nullable = false)
    private Double difficulty;

    @Column(name = "stability", nullable = false)
    private Double stability;

    @Column(name = "due_at", nullable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant dueAt;

    @Column(name = "last_review_at")
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant lastReviewAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = UtcInstantAttributeConverter.class)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
