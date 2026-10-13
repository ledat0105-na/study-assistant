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
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_generation_jobs")
public class PresentationGenerationJob {
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
    @Column(name = "job_type", nullable = false, length = 20)
    private PresentationJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PresentationJobStatus status;

    @Column(name = "outline_revision_id")
    private Long outlineRevisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outline_revision_id", insertable = false, updatable = false)
    private PresentationOutlineRevision outlineRevision;

    @Column(name = "target_slide_id")
    private Long targetSlideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_slide_id", insertable = false, updatable = false)
    private PresentationSlide targetSlide;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "progress_current", nullable = false)
    private Integer progressCurrent = 0;

    @Column(name = "progress_total", nullable = false)
    private Integer progressTotal = 0;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public PresentationGenerationJob() {}

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }
    public PresentationDeck getDeck() { return deck; }
    public PresentationJobType getJobType() { return jobType; }
    public void setJobType(PresentationJobType jobType) { this.jobType = jobType; }
    public PresentationJobStatus getStatus() { return status; }
    public void setStatus(PresentationJobStatus status) { this.status = status; }
    public Long getOutlineRevisionId() { return outlineRevisionId; }
    public void setOutlineRevisionId(Long outlineRevisionId) { this.outlineRevisionId = outlineRevisionId; }
    public PresentationOutlineRevision getOutlineRevision() { return outlineRevision; }
    public Long getTargetSlideId() { return targetSlideId; }
    public void setTargetSlideId(Long targetSlideId) { this.targetSlideId = targetSlideId; }
    public PresentationSlide getTargetSlide() { return targetSlide; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Integer getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(Integer progressCurrent) { this.progressCurrent = progressCurrent; }
    public Integer getProgressTotal() { return progressTotal; }
    public void setProgressTotal(Integer progressTotal) { this.progressTotal = progressTotal; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getVersion() { return version; }
}
