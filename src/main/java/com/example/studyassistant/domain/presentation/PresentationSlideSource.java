package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_slide_sources")
public class PresentationSlideSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false)
    private PresentationSlide slide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "source_order", nullable = false)
    private Integer sourceOrder;

    @Column(name = "evidence_excerpt", nullable = false, columnDefinition = "TEXT")
    private String evidenceExcerpt;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "section_title", length = 512)
    private String sectionTitle;

    @Column(name = "source_alias", length = 32)
    private String sourceAlias;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PresentationSlideSource() {}

    public Long getId() { return id; }
    public PresentationSlide getSlide() { return slide; }
    public void setSlide(PresentationSlide slide) { this.slide = slide; }
    public DocumentChunk getDocumentChunk() { return documentChunk; }
    public void setDocumentChunk(DocumentChunk documentChunk) { this.documentChunk = documentChunk; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public Integer getSourceOrder() { return sourceOrder; }
    public void setSourceOrder(Integer sourceOrder) { this.sourceOrder = sourceOrder; }
    public String getEvidenceExcerpt() { return evidenceExcerpt; }
    public void setEvidenceExcerpt(String evidenceExcerpt) { this.evidenceExcerpt = evidenceExcerpt; }
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
    public String getSourceAlias() { return sourceAlias; }
    public void setSourceAlias(String sourceAlias) { this.sourceAlias = sourceAlias; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
