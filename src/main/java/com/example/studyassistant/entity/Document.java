package com.example.studyassistant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id", nullable = true)
    @JsonIgnore
    private Notebook notebook;

    @Column(name = "file_name", nullable = false)
    @JsonProperty("title")
    private String fileName;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "file_path", nullable = false)
    @JsonIgnore
    private String filePath;

    @Column(name = "file_type")
    @JsonProperty("format")
    private String fileType; // "PDF", "DOCX", "PPTX"

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "total_pages")
    @JsonProperty("pages")
    private Integer totalPages;

    @Column(nullable = false)
    private String status = "UPLOADING"; // "UPLOADING", "PROCESSING", "READY", "FAILED"

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage; // Thông báo lỗi thân thiện, không lộ stacktrace

    @Column(name = "created_at")
    @JsonProperty("uploadDate")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonProperty("size")
    public String getSize() {
        if (fileSize == null) return "0 MB";
        return String.format("%.1f MB", (double) fileSize / (1024 * 1024));
    }
}
