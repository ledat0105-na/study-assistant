package com.example.studyassistant.domain.flashcard.generation;

import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

/**
 * Selects a bounded, deterministic set of active DocumentChunks for document-level
 * flashcard generation. Operates purely on DB rows — no Qdrant/embedding required.
 *
 * Evidence strategy: BOUNDED_ACTIVE_CHUNK_SELECTION
 *
 * Rationale: Document-level flashcard generation has no user query to drive semantic
 * retrieval. Using a bounded selection of all active chunks (matching activeIndexVersion),
 * ordered by chunkIndex ASC for page-aware determinism, gives representative coverage
 * without unbounded findAll or legacy extractedText truncation.
 *
 * Bounded at MAX_CHUNKS to cap LLM prompt size. Only chunks belonging to the exact
 * activeIndexVersion are included — stale or re-indexed chunks are excluded.
 */
@Component
@RequiredArgsConstructor
public class BoundedActiveChunkSelector {

    static final int MAX_CHUNKS = 60;

    private final DocumentChunkRepository chunkRepo;

    /**
     * Returns up to MAX_CHUNKS active chunks for the given documentId and indexVersion,
     * ordered by chunkIndex ascending for page-aware determinism.
     */
    public List<DocumentChunk> selectChunks(long documentId, String indexVersion) {
        List<DocumentChunk> all = new ArrayList<>(chunkRepo.findByDocumentIdAndIndexVersion(documentId, indexVersion));
        // Sort by chunkIndex for determinism
        all.sort(java.util.Comparator.comparingInt(DocumentChunk::getChunkIndex));
        if (all.size() > MAX_CHUNKS) {
            List<DocumentChunk> sampled = new ArrayList<>();
            for (int i = 0; i < MAX_CHUNKS; i++) {
                sampled.add(all.get((int) ((long) i * (all.size() - 1) / (MAX_CHUNKS - 1))));
            }
            return sampled;
        }
        return all;
    }

    public boolean hasEvidence(List<DocumentChunk> chunks) {
        return chunks != null && !chunks.isEmpty();
    }
}
