package com.example.studyassistant.service;

import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.projection.GraphEdgeProjection;
import com.example.studyassistant.repository.projection.GraphNodeProjection;
import com.example.studyassistant.repository.projection.RelationEvidenceProjection;
import com.example.studyassistant.service.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KnowledgeGraphQueryService {

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private com.example.studyassistant.repository.DocumentRepository documentRepository;

    @Autowired
    private KnowledgeGraphQueryRepository graphQueryRepository;

    @Value("${knowledge.graph.max-nodes:500}")
    private int maxNodes;

    @Value("${knowledge.graph.max-edges:1000}")
    private int maxEdges;

    public KnowledgeGraphDto getGroundedKnowledgeGraph(Long notebookId, Long userId) {
        verifyOwnership(notebookId, userId);

        List<GraphNodeProjection> nodeProjections = graphQueryRepository.findGroundedNodesByNotebookId(notebookId);
        if (nodeProjections.size() > maxNodes) {
            throw new GraphTooLargeException("Graph contains too many nodes (" + nodeProjections.size() + "). Maximum is " + maxNodes);
        }

        List<GraphEdgeProjection> edgeProjections = graphQueryRepository.findGroundedEdgesByNotebookId(notebookId);
        if (edgeProjections.size() > maxEdges) {
            throw new GraphTooLargeException("Graph contains too many edges (" + edgeProjections.size() + "). Maximum is " + maxEdges);
        }

        Set<Long> visibleNodeIds = buildVisibleNodeIds(nodeProjections);

        List<GraphConceptDto> nodes = nodeProjections.stream()
                .map(n -> GraphConceptDto.builder()
                        .id(n.getConceptId())
                        .canonicalName(n.getCanonicalName())
                        .conceptType(n.getConceptType())
                        .description(n.getDescription())
                        .sourceCount(n.getSourceCount())
                        .build())
                .collect(Collectors.toList());

        List<GraphRelationDto> edges = edgeProjections.stream()
                .filter(e -> hasVisibleEndpoints(e, visibleNodeIds))
                .map(e -> GraphRelationDto.builder()
                        .id(e.getRelationId())
                        .sourceConceptId(e.getSourceConceptId())
                        .targetConceptId(e.getTargetConceptId())
                        .relationType(e.getRelationType())
                        .confidence(e.getEdgeConfidence())
                        .evidenceCount(e.getEvidenceCount())
                        .build())
                .collect(Collectors.toList());

        return KnowledgeGraphDto.builder()
                .notebookId(notebookId)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    public KnowledgeGraphDto getGroundedKnowledgeGraphForDocument(Long documentId, Long userId) {
        var document = verifyDocumentOwnership(documentId, userId);
        KnowledgeScope.capture(document, userId);

        List<GraphNodeProjection> nodeProjections = graphQueryRepository.findGroundedNodesByDocumentId(documentId);
        if (nodeProjections.size() > maxNodes) {
            throw new GraphTooLargeException("Graph contains too many nodes (" + nodeProjections.size() + "). Maximum is " + maxNodes);
        }

        List<GraphEdgeProjection> edgeProjections = graphQueryRepository.findGroundedEdgesByDocumentId(documentId);
        if (edgeProjections.size() > maxEdges) {
            throw new GraphTooLargeException("Graph contains too many edges (" + edgeProjections.size() + "). Maximum is " + maxEdges);
        }

        Set<Long> visibleNodeIds = buildVisibleNodeIds(nodeProjections);

        List<GraphConceptDto> nodes = nodeProjections.stream()
                .map(n -> GraphConceptDto.builder()
                        .id(n.getConceptId())
                        .canonicalName(n.getCanonicalName())
                        .conceptType(n.getConceptType())
                        .description(n.getDescription())
                        .sourceCount(n.getSourceCount())
                        .build())
                .collect(Collectors.toList());

        List<GraphRelationDto> edges = edgeProjections.stream()
                .filter(e -> hasVisibleEndpoints(e, visibleNodeIds))
                .map(e -> GraphRelationDto.builder()
                        .id(e.getRelationId())
                        .sourceConceptId(e.getSourceConceptId())
                        .targetConceptId(e.getTargetConceptId())
                        .relationType(e.getRelationType())
                        .confidence(e.getEdgeConfidence())
                        .evidenceCount(e.getEvidenceCount())
                        .build())
                .collect(Collectors.toList());

        return KnowledgeGraphDto.builder()
                .notebookId(document.getNotebook() == null ? null : document.getNotebook().getId())
                .scopeType(document.getNotebook() == null ? "DOCUMENT" : "NOTEBOOK")
                .documentId(documentId)
                .activeIndexVersion(document.getActiveIndexVersion())
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    public ConceptDetailDto getConceptDetail(Long notebookId, Long conceptId, Long userId) {
        verifyOwnership(notebookId, userId);

        List<GraphNodeProjection> requestedNodeProjections = graphQueryRepository.findGroundedNodeByIdAndNotebookId(conceptId, notebookId);
        if (requestedNodeProjections.isEmpty()) {
            throw new IllegalArgumentException("Concept not found or ungrounded");
        }

        GraphNodeProjection nodeProjection = requestedNodeProjections.get(0);
        GraphConceptDto conceptDto = GraphConceptDto.builder()
                .id(nodeProjection.getConceptId())
                .canonicalName(nodeProjection.getCanonicalName())
                .conceptType(nodeProjection.getConceptType())
                .description(nodeProjection.getDescription())
                .sourceCount(nodeProjection.getSourceCount())
                .build();

        // 1. Obtain visible grounded nodes for Notebook
        List<GraphNodeProjection> allNodeProjections = graphQueryRepository.findGroundedNodesByNotebookId(notebookId);
        Set<Long> visibleNodeIds = buildVisibleNodeIds(allNodeProjections);

        // 2. Obtain grounded relation projections
        List<GraphEdgeProjection> allEdges = graphQueryRepository.findGroundedEdgesByNotebookId(notebookId);

        // 3. Filter relations through SAME endpoint visibility invariant
        List<GraphEdgeProjection> survivingEdges = allEdges.stream()
                .filter(e -> hasVisibleEndpoints(e, visibleNodeIds))
                .collect(Collectors.toList());

        // 4. from those surviving relations only: collect incoming/outgoing
        List<GraphRelationDto> incomingRelations = survivingEdges.stream()
                .filter(e -> e.getTargetConceptId().equals(conceptId))
                .map(e -> GraphRelationDto.builder()
                        .id(e.getRelationId())
                        .sourceConceptId(e.getSourceConceptId())
                        .targetConceptId(e.getTargetConceptId())
                        .relationType(e.getRelationType())
                        .confidence(e.getEdgeConfidence())
                        .evidenceCount(e.getEvidenceCount())
                        .build())
                .collect(Collectors.toList());

        List<GraphRelationDto> outgoingRelations = survivingEdges.stream()
                .filter(e -> e.getSourceConceptId().equals(conceptId))
                .map(e -> GraphRelationDto.builder()
                        .id(e.getRelationId())
                        .sourceConceptId(e.getSourceConceptId())
                        .targetConceptId(e.getTargetConceptId())
                        .relationType(e.getRelationType())
                        .confidence(e.getEdgeConfidence())
                        .evidenceCount(e.getEvidenceCount())
                        .build())
                .collect(Collectors.toList());

        List<Long> relevantRelationIds = java.util.stream.Stream.concat(
                        incomingRelations.stream().map(GraphRelationDto::getId),
                        outgoingRelations.stream().map(GraphRelationDto::getId))
                .distinct()
                .collect(Collectors.toList());

        List<RelationEvidenceDto> relationEvidence = List.of();
        if (!relevantRelationIds.isEmpty()) {
            List<RelationEvidenceProjection> evidenceProjections = graphQueryRepository.findRelationEvidenceInBatches(notebookId, relevantRelationIds);
            relationEvidence = evidenceProjections.stream()
                    .map(e -> RelationEvidenceDto.builder()
                            .relationId(e.getRelationId())
                            .chunkId(e.getChunkId())
                            .documentId(e.getDocumentId())
                            .documentTitle(e.getDocumentTitle())
                            .evidenceText(e.getEvidenceText())
                            .confidence(e.getConfidence())
                            .build())
                    .collect(Collectors.toList());
        }

        return ConceptDetailDto.builder()
                .concept(conceptDto)
                .incomingRelations(incomingRelations)
                .outgoingRelations(outgoingRelations)
                .relationEvidence(relationEvidence)
                .build();
    }

    private void verifyOwnership(Long notebookId, Long userId) {
        Notebook notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook not found"));
        if (!notebook.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Notebook not found");
        }
    }

    private com.example.studyassistant.entity.Document verifyDocumentOwnership(Long documentId, Long userId) {
        com.example.studyassistant.entity.Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!document.getUser().getId().equals(userId)
                || (document.getNotebook() != null && (!document.getNotebook().getUser().getId().equals(userId)
                    || Boolean.TRUE.equals(document.getNotebook().getIsDeleted())))) {
            throw new IllegalArgumentException("Document not found");
        }
        return document;
    }

    private Set<Long> buildVisibleNodeIds(List<GraphNodeProjection> nodeProjections) {
        return nodeProjections.stream()
                .map(GraphNodeProjection::getConceptId)
                .collect(Collectors.toSet());
    }

    private boolean hasVisibleEndpoints(GraphEdgeProjection edge, Set<Long> visibleNodeIds) {
        return visibleNodeIds.contains(edge.getSourceConceptId()) && visibleNodeIds.contains(edge.getTargetConceptId());
    }
}
