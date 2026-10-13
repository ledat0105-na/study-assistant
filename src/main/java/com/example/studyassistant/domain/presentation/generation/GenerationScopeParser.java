package com.example.studyassistant.domain.presentation.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the frozen generationScopeSnapshot JSON from PresentationOutlineRevision.
 *
 * Snapshot format:
 * {
 *   "notebookId": 42,
 *   "requestedDocumentIds": [1, 2, 3],   // optional
 *   "sources": [...]                     // frozen source provenance
 * }
 */
@Component
public class GenerationScopeParser {

    private final ObjectMapper objectMapper;

    public GenerationScopeParser(@Qualifier("legacyJackson2ObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ParsedScope(Long notebookId, List<Long> documentIds) {}

    /**
     * Parse scope from snapshot JSON.
     * Throws IllegalStateException if scope is malformed or notebookId is missing.
     */
    public ParsedScope parse(String scopeSnapshot) {
        if (scopeSnapshot == null || scopeSnapshot.isBlank()) {
            throw new IllegalStateException("generationScopeSnapshot is null or blank");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(scopeSnapshot,
                new TypeReference<Map<String, Object>>() {});
            Object nbId = map.get("notebookId");
            if (!(nbId instanceof Number)) {
                throw new IllegalStateException("generationScopeSnapshot missing notebookId");
            }
            Long notebookId = ((Number) nbId).longValue();
            if (notebookId <= 0) {
                throw new IllegalStateException("generationScopeSnapshot has invalid notebookId");
            }

            Set<Long> requestedDocumentIds = new LinkedHashSet<>();
            Object docIds = map.get("requestedDocumentIds");
            if (docIds instanceof List) {
                for (Object docId : (List<?>) docIds) {
                    requestedDocumentIds.add(parseDocumentId(docId));
                }
            } else if (docIds != null) {
                throw new IllegalStateException("generationScopeSnapshot has invalid requestedDocumentIds");
            }

            Set<Long> sourceDocumentIds = new LinkedHashSet<>();
            Object sources = map.get("sources");
            if (sources instanceof List) {
                for (Object source : (List<?>) sources) {
                    if (!(source instanceof Map<?, ?> sourceMap)) {
                        throw new IllegalStateException("generationScopeSnapshot has invalid source entry");
                    }
                    Object documentId = sourceMap.get("documentId");
                    sourceDocumentIds.add(parseDocumentId(documentId));
                }
            } else if (sources != null) {
                throw new IllegalStateException("generationScopeSnapshot has invalid sources");
            }

            if (!requestedDocumentIds.isEmpty()) {
                if (!requestedDocumentIds.containsAll(sourceDocumentIds)) {
                    throw new IllegalStateException("generationScopeSnapshot source is outside requested document scope");
                }
                return new ParsedScope(notebookId, List.copyOf(requestedDocumentIds));
            }
            if (sourceDocumentIds.isEmpty()) {
                throw new IllegalStateException("generationScopeSnapshot has no frozen document scope");
            }
            return new ParsedScope(notebookId, List.copyOf(sourceDocumentIds));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse generationScopeSnapshot: " + e.getMessage(), e);
        }
    }

    private Long parseDocumentId(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("generationScopeSnapshot has invalid documentId");
        }
        long documentId = number.longValue();
        if (documentId <= 0 || number.doubleValue() != (double) documentId) {
            throw new IllegalStateException("generationScopeSnapshot has invalid documentId");
        }
        return documentId;
    }
}
