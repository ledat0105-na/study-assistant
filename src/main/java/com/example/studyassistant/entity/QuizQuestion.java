package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "answer_a", nullable = false)
    private String answerA;

    @Column(name = "answer_b", nullable = false)
    private String answerB;

    @Column(name = "answer_c", nullable = false)
    private String answerC;

    @Column(name = "answer_d", nullable = false)
    private String answerD;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer; // 'A', 'B', 'C', 'D'

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;
}
