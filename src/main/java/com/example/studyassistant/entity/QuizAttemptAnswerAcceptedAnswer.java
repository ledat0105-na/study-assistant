package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_attempt_answer_accepted_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswerAcceptedAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_answer_id", nullable = false)
    private QuizAttemptAnswer attemptAnswer;

    @Column(name = "accepted_order", nullable = false)
    private Integer acceptedOrder;

    @Column(name = "accepted_text_snapshot", columnDefinition = "TEXT", nullable = false)
    private String acceptedTextSnapshot;
}
