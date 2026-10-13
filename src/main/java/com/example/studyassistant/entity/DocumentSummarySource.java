package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_summary_sources", uniqueConstraints = {
        @UniqueConstraint(name = "uk_document_summary_source_alias", columnNames = {"summary_id", "source_alias"}),
        @UniqueConstraint(name = "uk_document_summary_source_order", columnNames = {"summary_id", "source_order"})
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentSummarySource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private DocumentSummary summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(name = "source_alias", nullable = false, length = 32)
    private String sourceAlias;

    @Column(name = "source_order", nullable = false)
    private Integer sourceOrder;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "section_title", length = 512)
    private String sectionTitle;
}
