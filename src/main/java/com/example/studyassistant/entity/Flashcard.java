package com.example.studyassistant.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flashcards")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Flashcard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    @JsonProperty("front")
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    @JsonProperty("back")
    private String answer;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = true)
    private Topic topic;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = true)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = true)
    private Concept concept;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = com.example.studyassistant.util.UtcInstantAttributeConverter.class)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = com.example.studyassistant.util.UtcInstantAttributeConverter.class)
    private java.time.Instant updatedAt;

    @Column(name = "deleted_at")
    @Convert(converter = com.example.studyassistant.util.UtcInstantAttributeConverter.class)
    private java.time.Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
}
