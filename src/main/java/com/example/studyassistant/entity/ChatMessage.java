package com.example.studyassistant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    private Conversation conversation;

    @Column(nullable = false)
    private String sender; // "USER" or "ASSISTANT"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson; // JSON lưu danh sách citation nguồn [{documentId, title, pageStart, pageEnd}]

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
