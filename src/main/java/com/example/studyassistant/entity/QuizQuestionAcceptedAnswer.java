package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_question_accepted_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionAcceptedAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(name = "accepted_order", nullable = false)
    private Integer acceptedOrder;

    @Column(name = "accepted_text", columnDefinition = "TEXT", nullable = false)
    private String acceptedText;
}
