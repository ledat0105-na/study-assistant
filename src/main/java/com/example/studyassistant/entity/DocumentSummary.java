package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_summaries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_document_summary_version", columnNames = {"document_id", "index_version"})
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "index_version", nullable = false, length = 128)
    private String indexVersion;

    @Column(name = "document_content_hash", length = 64)
    private String documentContentHash;

    @Lob
    @Column(name = "summary_text", columnDefinition = "LONGTEXT", nullable = false)
    private String summaryText;

    @Column(name = "summary_json", columnDefinition = "JSON", nullable = false)
    private String summaryJson;

    @Column(name = "model_identifier", length = 128, nullable = false)
    private String modelIdentifier;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
