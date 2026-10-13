package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_outline_revisions")
public class PresentationOutlineRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", insertable = false, updatable = false)
    private PresentationDeck deck;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PresentationOutlineStatus status = PresentationOutlineStatus.DRAFT;

    @Column(name = "generation_scope_snapshot", nullable = false, columnDefinition = "json")
    private String generationScopeSnapshot;

    @Column(name = "outline_json", nullable = false, columnDefinition = "json")
    private String outlineJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public PresentationOutlineRevision() {}

    public Long getId() { return id; }
    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }
    public PresentationDeck getDeck() { return deck; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public PresentationOutlineStatus getStatus() { return status; }
    public void setStatus(PresentationOutlineStatus status) { this.status = status; }
    public String getGenerationScopeSnapshot() { return generationScopeSnapshot; }
    public void setGenerationScopeSnapshot(String generationScopeSnapshot) { this.generationScopeSnapshot = generationScopeSnapshot; }
    public String getOutlineJson() { return outlineJson; }
    public void setOutlineJson(String outlineJson) { this.outlineJson = outlineJson; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
