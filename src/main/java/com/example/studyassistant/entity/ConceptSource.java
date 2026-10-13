package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "concept_sources", uniqueConstraints = {
        @UniqueConstraint(name = "uk_concept_chunk", columnNames = {"concept_id", "document_chunk_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConceptSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(name = "evidence_text", columnDefinition = "TEXT", nullable = false)
    private String evidenceText;

    private Double confidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
