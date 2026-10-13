package com.example.studyassistant.repository;

import com.example.studyassistant.entity.DocumentChunk;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Override default findById to eagerly fetch the Document association in a single LEFT JOIN query.
     * This prevents LazyInitializationException when RetrievalService.selectContext() accesses
     * chunk.getDocument().getFileName() after the repository transaction has closed.
     */
    @Override
    @EntityGraph(attributePaths = "document")
    Optional<DocumentChunk> findById(Long id);

    List<DocumentChunk> findByDocumentIdAndIndexVersion(Long documentId, String indexVersion);

    List<DocumentChunk> findByDocumentIdAndIndexVersionOrderByChunkIndexAsc(Long documentId, String indexVersion);

    Optional<DocumentChunk> findByIdAndDocumentIdAndIndexVersion(Long id, Long documentId, String indexVersion);

    void deleteByDocumentIdAndIndexVersion(Long documentId, String indexVersion);

    long countByDocumentIdAndIndexVersion(Long documentId, String indexVersion);
    
    // For test verification
    Optional<DocumentChunk> findByDocumentIdAndChunkIndexAndIndexVersion(Long documentId, Integer chunkIndex, String indexVersion);
}
