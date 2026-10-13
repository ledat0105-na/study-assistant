package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Check(constraints = "(notebook_id IS NOT NULL AND document_id IS NULL) OR (notebook_id IS NULL AND document_id IS NOT NULL)")
@Table(name = "concepts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notebook_normalized_name", columnNames = {"notebook_id", "normalized_name"}),
        @UniqueConstraint(name = "uk_document_normalized_name", columnNames = {"document_id", "normalized_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Concept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id")
    private Notebook notebook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @PrePersist
    public void validateScope() {
        if ((notebook == null) == (document == null)) {
            throw new IllegalArgumentException("Concept must have exactly one notebook or document scope");
        }
    }

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "concept_type", length = 50)
    private String conceptType;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        validateScope();
        updatedAt = LocalDateTime.now();
    }
}
