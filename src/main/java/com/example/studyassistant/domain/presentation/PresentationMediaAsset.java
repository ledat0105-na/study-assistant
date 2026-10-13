package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.entity.Document;
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
@Table(name = "presentation_media_assets")
public class PresentationMediaAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", insertable = false, updatable = false)
    private PresentationDeck deck;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private PresentationMediaSourceType sourceType;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_key", nullable = false, length = 512)
    private String storedFileKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(length = 255)
    private String creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_status", nullable = false, length = 24)
    private PresentationMediaLicenseStatus licenseStatus;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PresentationMediaAsset() {}

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }
    public PresentationDeck getDeck() { return deck; }
    public PresentationMediaSourceType getSourceType() { return sourceType; }
    public void setSourceType(PresentationMediaSourceType sourceType) { this.sourceType = sourceType; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileKey() { return storedFileKey; }
    public void setStoredFileKey(String storedFileKey) { this.storedFileKey = storedFileKey; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public PresentationMediaLicenseStatus getLicenseStatus() { return licenseStatus; }
    public void setLicenseStatus(PresentationMediaLicenseStatus licenseStatus) { this.licenseStatus = licenseStatus; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Document getSourceDocument() { return sourceDocument; }
    public void setSourceDocument(Document sourceDocument) { this.sourceDocument = sourceDocument; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
