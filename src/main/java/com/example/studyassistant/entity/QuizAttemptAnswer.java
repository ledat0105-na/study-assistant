package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_attempt_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "question_text_snapshot", columnDefinition = "TEXT", nullable = false)
    private String questionTextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type_snapshot", length = 50, nullable = false)
    private QuizQuestionType questionTypeSnapshot;

    @Column(name = "points_possible", nullable = false)
    private Double pointsPossible;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "points_earned")
    private Double pointsEarned;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "explanation_snapshot", columnDefinition = "TEXT")
    private String explanationSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    @Column(name = "graded_at")
    private java.time.LocalDateTime gradedAt;
}
