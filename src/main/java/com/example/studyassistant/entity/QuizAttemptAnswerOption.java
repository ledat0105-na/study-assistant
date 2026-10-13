package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_attempt_answer_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswerOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_answer_id", nullable = false)
    private QuizAttemptAnswer attemptAnswer;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(name = "option_text_snapshot", columnDefinition = "TEXT", nullable = false)
    private String optionTextSnapshot;

    @Column(name = "is_correct_snapshot", nullable = false)
    private Boolean isCorrectSnapshot;

    @Column(name = "student_selected", nullable = false)
    private Boolean studentSelected;
}
