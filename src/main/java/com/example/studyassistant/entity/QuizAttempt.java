package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt {
    @Column(name = "submission_token", length = 36)
    private String submissionToken;

    @Column(name = "submission_hash", length = 64)
    private String submissionHash;

    @Column(name = "scoring_version", length = 50)
    private String scoringVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_snapshot", length = 50)
    private QuizMode modeSnapshot;

    @Column(name = "time_limit_seconds_snapshot")
    private Integer timeLimitSecondsSnapshot;

    @Column(name = "pass_threshold_snapshot")
    private Double passThresholdSnapshot;

    @Column(name = "mastery_applied", nullable = false)
    private Boolean masteryApplied = false;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "correct_answers")
    private Integer correctAnswers;

    private Double score;

    @Column(nullable = false)
    private String status = "IN_PROGRESS"; // "IN_PROGRESS", "COMPLETED"

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
