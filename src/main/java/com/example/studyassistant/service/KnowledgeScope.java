package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import java.util.Objects;

/** Immutable generation snapshot; never infer scope from provider output. */
public record KnowledgeScope(Long documentId, Long ownerId, Long notebookId,
                             String indexVersion, String contentHash) {
    public static KnowledgeScope capture(Document document, Long ownerId) {
        if (ownerId == null || document == null || document.getId() == null || document.getUser() == null
                || !Objects.equals(document.getUser().getId(), ownerId)) {
            throw new IllegalArgumentException("Document not found");
        }
        if (!"READY".equals(document.getStatus())) {
            throw new KnowledgeGraphStateException(GraphFailureCode.DOCUMENT_NOT_READY);
        }
        if (document.getActiveIndexVersion() == null || document.getActiveIndexVersion().isBlank()) {
            throw new KnowledgeGraphStateException(GraphFailureCode.NO_ACTIVE_INDEX);
        }
        Notebook notebook = document.getNotebook();
        if (notebook != null && (notebook.getId() == null || notebook.getUser() == null
                || !Objects.equals(notebook.getUser().getId(), ownerId) || Boolean.TRUE.equals(notebook.getIsDeleted()))) {
            throw new IllegalArgumentException("Notebook is not owned by document owner");
        }
        return new KnowledgeScope(document.getId(), ownerId, notebook == null ? null : notebook.getId(),
                document.getActiveIndexVersion(), document.getContentHash());
    }

    public static KnowledgeScope fromChunk(DocumentChunk chunk) {
        Document doc = chunk.getDocument();
        return capture(doc, doc == null || doc.getUser() == null ? null : doc.getUser().getId());
    }

    public void validate(Document current, DocumentChunk chunk) {
        if (!equals(capture(current, ownerId)) || chunk == null || chunk.getId() == null
                || chunk.getDocument() == null || !Objects.equals(documentId, chunk.getDocument().getId())
                || !Objects.equals(indexVersion, chunk.getIndexVersion())) {
            throw new KnowledgeGraphStateException(GraphFailureCode.STALE_INDEX);
        }
    }

    public boolean contains(Concept concept) {
        concept.validateScope();
        return notebookId != null
                ? concept.getNotebook() != null && Objects.equals(notebookId, concept.getNotebook().getId())
                : concept.getDocument() != null && Objects.equals(documentId, concept.getDocument().getId());
    }

    public static void validateRelation(Concept source, Concept target) {
        source.validateScope();
        target.validateScope();
        boolean same = source.getNotebook() != null && target.getNotebook() != null
                ? Objects.equals(source.getNotebook().getId(), target.getNotebook().getId())
                : source.getDocument() != null && target.getDocument() != null
                    && Objects.equals(source.getDocument().getId(), target.getDocument().getId());
        if (!same || Objects.equals(source.getId(), target.getId())) {
            throw new IllegalArgumentException("Relation endpoints must be distinct and have the same scope");
        }
    }
}
