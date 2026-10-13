package com.example.studyassistant.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quizzes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id")
    private Notebook notebook;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 50)
    private QuizScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_concept_id")
    private Concept scopeConcept;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private QuizMode mode;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private QuizStatus status;

    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Column(name = "pass_threshold")
    private Double passThreshold;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @JsonProperty("name")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = true)
    private Topic topic;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = true)
    private Document document;
}
