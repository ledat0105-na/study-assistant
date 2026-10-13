package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeGraphEvidenceValidator {
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;

    // Called inside the short provenance write transaction. The document lock
    // serializes this check with index activation and document scope changes.
    public DocumentChunk validate(KnowledgeScope scope, Concept concept, DocumentChunk supplied, String evidence) {
        Document current = documents.findByIdWithPessimisticLock(scope.documentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        DocumentChunk chunk = chunks.findByIdAndDocumentIdAndIndexVersion(
                supplied.getId(), scope.documentId(), scope.indexVersion())
                .orElseThrow(() -> new KnowledgeGraphStateException(GraphFailureCode.STALE_INDEX));
        scope.validate(current, chunk);
        if (!scope.contains(concept) || evidence == null || evidence.isBlank()
                || chunk.getContent() == null || !normalize(chunk.getContent()).contains(normalize(evidence))) {
            throw new IllegalArgumentException("Concept scope or evidence does not match target document");
        }
        return chunk;
    }

    private String normalize(String value) { return value.replaceAll("\\s+", " ").trim(); }
}
