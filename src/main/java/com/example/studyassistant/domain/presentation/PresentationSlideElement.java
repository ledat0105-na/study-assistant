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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_slide_elements")
public class PresentationSlideElement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @Column(name = "slide_id", nullable = false)
    private Long slideId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", insertable = false, updatable = false)
    private PresentationSlide slide;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 16)
    private PresentationElementType elementType;

    @Column(name = "z_index", nullable = false)
    private Integer zIndex;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal x;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal y;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal width;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal height;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal rotation = BigDecimal.ZERO;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "style_json", nullable = false, columnDefinition = "json")
    private String styleJson;

    @Column(name = "media_asset_id")
    private Long mediaAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", insertable = false, updatable = false)
    private PresentationMediaAsset mediaAsset;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PresentationSlideElement() {}

    public Long getId() { return id; }
    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public PresentationSlide getSlide() { return slide; }
    public PresentationElementType getElementType() { return elementType; }
    public void setElementType(PresentationElementType elementType) { this.elementType = elementType; }
    public Integer getZIndex() { return zIndex; }
    public void setZIndex(Integer zIndex) { this.zIndex = zIndex; }
    public BigDecimal getX() { return x; }
    public void setX(BigDecimal x) { this.x = x; }
    public BigDecimal getY() { return y; }
    public void setY(BigDecimal y) { this.y = y; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getRotation() { return rotation; }
    public void setRotation(BigDecimal rotation) { this.rotation = rotation; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStyleJson() { return styleJson; }
    public void setStyleJson(String styleJson) { this.styleJson = styleJson; }
    public Long getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(Long mediaAssetId) { this.mediaAssetId = mediaAssetId; }
    public PresentationMediaAsset getMediaAsset() { return mediaAsset; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
