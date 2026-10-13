package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.VectorRecord;

import java.util.List;

public interface VectorStore {
    
    /**
     * Ensures the collection exists with the correct dimension and settings.
     * @param dimension the expected vector dimension.
     */
    void ensureCollection(int dimension);

    /**
     * Upserts a batch of vectors into the store.
     * @param records The records to upsert.
     */
    void upsert(List<VectorRecord> records);

    /**
     * Searches for nearest neighbors. (Placeholder for future CP)
     * @param queryVector The query vector.
     * @param documentId The document to filter by (or null).
     * @param indexVersion The index version to filter by (or null).
     * @param limit Maximum number of results.
     * @return List of matching chunk IDs (or VectorRecords).
     */
    List<VectorRecord> search(List<Double> queryVector, Long documentId, String indexVersion, int limit);

    /**
     * Searches for nearest neighbors filtering by a list of authorized document/version pairs.
     * @param queryVector The query vector.
     * @param authorizedPairs The authorized pairs to filter by.
     * @param limit Maximum number of results.
     * @return List of matching vector records.
     */
    List<VectorRecord> search(List<Double> queryVector, List<com.example.studyassistant.retrieval.model.DocumentVersionPair> authorizedPairs, int limit);

    /**
     * Deletes all vectors for a given document and index version.
     * Useful for cleanup.
     * @param documentId the document ID.
     * @param indexVersion the index version.
     */
    void deleteByDocumentAndVersion(Long documentId, String indexVersion);
}
