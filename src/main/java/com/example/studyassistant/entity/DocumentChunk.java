package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_doc_id", columnList = "document_id"),
        @Index(name = "uk_doc_idx_version", columnList = "document_id, chunk_index, index_version", unique = true)
        // Note: Hibernate standard @Index does not support MySQL FULLTEXT natively.
        // FULLTEXT(content) must be applied via native SQL migration.
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "section_title", length = 512)
    private String sectionTitle;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "index_version", nullable = false, length = 128)
    private String indexVersion;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
