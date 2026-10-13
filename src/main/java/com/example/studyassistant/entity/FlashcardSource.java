package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flashcard_sources", uniqueConstraints = {
        @UniqueConstraint(name = "uq_flashcard_source_chunk", columnNames = {"flashcard_id", "document_chunk_id"}),
        @UniqueConstraint(name = "uq_flashcard_source_order", columnNames = {"flashcard_id", "source_order"})
})
@Getter
@Setter
@NoArgsConstructor
public class FlashcardSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(name = "source_order", nullable = false)
    private Integer sourceOrder;
}
