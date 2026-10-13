package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentKnowledgeGraphGenerationService {
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final AIConceptExtractor concepts;
    private final AIConceptRelationExtractor relations;
    private final KnowledgeGraphQueryService queries;

    // No surrounding transaction: each provenance write revalidates under a lock.
    public KnowledgeGraphDto generate(Long userId, Long documentId) {
        Document document = owned(documentId, userId);
        KnowledgeScope scope = KnowledgeScope.capture(document, userId);
        List<DocumentChunk> active = chunks.findByDocumentIdAndIndexVersionOrderByChunkIndexAsc(documentId, scope.indexVersion());
        if (active.isEmpty()) throw new KnowledgeGraphStateException(GraphFailureCode.NO_ACTIVE_EVIDENCE);
        Set<GraphFailureCode> degradation = new LinkedHashSet<>();
        for (DocumentChunk chunk : active) {
            scope.validate(document, chunk);
            chunk.setDocument(document);
            var conceptResult = concepts.extractConcepts(scope, chunk);
            var relationResult = relations.extractRelationsForChunk(scope, chunk);
            collectFailure(conceptResult.getStatus(), degradation);
            collectFailure(relationResult.getStatus(), degradation);
            if (!degradation.isEmpty()) log.warn("KG chunk {} for document {}: concepts={}, relations={}",
                    chunk.getId(), documentId, conceptResult.getStatus(), relationResult.getStatus());
        }
        scope.validate(owned(documentId, userId), active.get(0));
        KnowledgeGraphDto graph = queries.getGroundedKnowledgeGraphForDocument(documentId, userId);
        if (graph.getActiveIndexVersion() != null && !scope.indexVersion().equals(graph.getActiveIndexVersion())) {
            throw new KnowledgeGraphStateException(GraphFailureCode.STALE_INDEX);
        }
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            // Never claim success based solely on an extractor status. Only active grounded
            // persisted nodes count; valid nodes from a previous attempt are useful too.
            GraphFailureCode failure = degradation.contains(GraphFailureCode.GRAPH_PERSISTENCE_ERROR)
                    ? GraphFailureCode.GRAPH_PERSISTENCE_ERROR
                    : degradation.contains(GraphFailureCode.AI_SCHEMA_ERROR) ? GraphFailureCode.AI_SCHEMA_ERROR
                    : degradation.contains(GraphFailureCode.AI_PROVIDER_ERROR) ? GraphFailureCode.AI_PROVIDER_ERROR
                    : degradation.contains(GraphFailureCode.GRAPH_TOO_LARGE) ? GraphFailureCode.GRAPH_TOO_LARGE
                    : GraphFailureCode.NO_CONCEPTS;
            throw new KnowledgeGraphGenerationException(failure);
        }
        graph.setActiveIndexVersion(scope.indexVersion());
        graph.setGenerationStatus(degradation.isEmpty()
                ? (graph.getEdges() != null && !graph.getEdges().isEmpty() ? "FULL_SUCCESS" : "SUCCESS")
                : "PARTIAL_SUCCESS");
        graph.setCode(degradation.isEmpty() ? null : "PARTIAL_GRAPH");
        graph.setDegradationCodes(degradation.stream().map(Enum::name).toList());
        return graph;
    }

    private Document owned(Long id, Long userId) {
        return documents.findKnowledgeGraphDocumentByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }
    private static void collectFailure(String status, Set<GraphFailureCode> failures) {
        if ("SUCCESS".equals(status) || "NO_CANDIDATES".equals(status)) return;
        failures.add(switch (status == null ? "FAILED" : status) {
            case "PROVIDER_FAILURE" -> GraphFailureCode.AI_PROVIDER_ERROR;
            case "PARSE_FAILURE" -> GraphFailureCode.AI_SCHEMA_ERROR;
            case "CANDIDATE_LIMIT_EXCEEDED" -> GraphFailureCode.GRAPH_TOO_LARGE;
            default -> GraphFailureCode.GRAPH_PERSISTENCE_ERROR;
        });
    }
}
