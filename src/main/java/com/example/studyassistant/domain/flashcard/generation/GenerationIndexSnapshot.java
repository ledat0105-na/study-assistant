package com.example.studyassistant.domain.flashcard.generation;

import com.example.studyassistant.entity.Document;

/**
 * Immutable snapshot of document index identity captured before LLM call.
 * Revalidated under PESSIMISTIC_WRITE lock before persistence.
 * If the snapshot no longer matches the locked document, persistence is aborted.
 */
public record GenerationIndexSnapshot(
    long documentId,
    long ownerUserId,
    String activeIndexVersion,
    String documentContentHash  // nullable
) {
    /**
     * Returns true if the locked document still matches this snapshot.
     * Fails closed if any identifying field differs.
     */
    public boolean matchesDocument(Document doc) {
        if (doc == null) return false;
        if (!doc.getId().equals(documentId)) return false;
        if (doc.getUser() == null || !doc.getUser().getId().equals(ownerUserId)) return false;
        if (doc.getActiveIndexVersion() == null) return false;
        return doc.getActiveIndexVersion().equals(activeIndexVersion)
                && java.util.Objects.equals(doc.getContentHash(), documentContentHash);
    }
}
