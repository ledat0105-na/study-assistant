package com.example.studyassistant.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false)
    @JsonProperty("title")
    private String fileName;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "file_path", nullable = false)
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
    private String status; // "UPLOADED", "PROCESSING", "READY"

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonProperty("uploadDate")
    private LocalDate createdAt;

    @JsonProperty("size")
    public String getSize() {
        if (fileSize == null) return "0 MB";
        return String.format("%.1f MB", (double) fileSize / (1024 * 1024));
    }
}
