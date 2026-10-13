package com.example.studyassistant.domain.presentation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_slides")
public class PresentationSlide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", insertable = false, updatable = false)
    private PresentationDeck deck;

    @Column(name = "slide_order", nullable = false)
    private Integer slideOrder;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_type", nullable = false, length = 32)
    private PresentationSlideLayout layoutType;

    @Column(name = "speaker_notes", columnDefinition = "TEXT")
    private String speakerNotes;

    @Column(name = "background_payload", columnDefinition = "json")
    private String backgroundPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_state", nullable = false, length = 16)
    private PresentationSlideGenerationState generationState = PresentationSlideGenerationState.EMPTY;

    @Column(name = "generated_from_outline_revision_id")
    private Long generatedFromOutlineRevisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_from_outline_revision_id", insertable = false, updatable = false)
    private PresentationOutlineRevision generatedFromOutlineRevision;

    @Column(name = "generation_key", length = 128)
    private String generationKey;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PresentationSlide() {}

    public Long getId() { return id; }
    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }
    public Integer getSlideOrder() { return slideOrder; }
    public void setSlideOrder(Integer slideOrder) { this.slideOrder = slideOrder; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public PresentationSlideLayout getLayoutType() { return layoutType; }
    public void setLayoutType(PresentationSlideLayout layoutType) { this.layoutType = layoutType; }
    public String getSpeakerNotes() { return speakerNotes; }
    public void setSpeakerNotes(String speakerNotes) { this.speakerNotes = speakerNotes; }
    public String getBackgroundPayload() { return backgroundPayload; }
    public void setBackgroundPayload(String backgroundPayload) { this.backgroundPayload = backgroundPayload; }
    public PresentationSlideGenerationState getGenerationState() { return generationState; }
    public void setGenerationState(PresentationSlideGenerationState generationState) { this.generationState = generationState; }
    public Long getGeneratedFromOutlineRevisionId() { return generatedFromOutlineRevisionId; }
    public void setGeneratedFromOutlineRevisionId(Long generatedFromOutlineRevisionId) { this.generatedFromOutlineRevisionId = generatedFromOutlineRevisionId; }
    public PresentationOutlineRevision getGeneratedFromOutlineRevision() { return generatedFromOutlineRevision; }
    public String getGenerationKey() { return generationKey; }
    public void setGenerationKey(String generationKey) { this.generationKey = generationKey; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
