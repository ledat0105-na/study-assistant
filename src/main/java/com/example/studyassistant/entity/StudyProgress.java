package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String status; // "NOT_STARTED", "LEARNING", "COMPLETED"

    private Double progress; // Progress percentage (e.g. 100 for completed)

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;
}
