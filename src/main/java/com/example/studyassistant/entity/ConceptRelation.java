package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "concept_relations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_source_target_type", columnNames = {"source_concept_id", "target_concept_id", "relation_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConceptRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_concept_id", nullable = false)
    private Concept sourceConcept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_concept_id", nullable = false)
    private Concept targetConcept;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 50)
    private RelationType relationType;

    private Double confidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void validateSelfRelation() {
        if (sourceConcept != null && targetConcept != null 
                && sourceConcept.getId() != null 
                && sourceConcept.getId().equals(targetConcept.getId())) {
            throw new IllegalStateException("Self-relations are not permitted");
        }
    }
}
