package com.example.studyassistant.service;

import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.projection.GraphSeedCandidateProjection;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphSeedConceptService {

    private final KnowledgeGraphQueryRepository knowledgeGraphQueryRepository;

    @Value("${knowledge.graph.rag.max-seed-concepts:5}")
    private int maxSeedConcepts;

    public List<GraphSeedConceptDto> getSeedConcepts(Long notebookId, List<RetrievedChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Defensively deduplicate and map trusted chunk IDs
        Map<Long, RetrievedChunk> trustedChunkMap = new HashMap<>();
        for (int i = 0; i < retrievedChunks.size(); i++) {
            RetrievedChunk chunk = retrievedChunks.get(i);
            if (chunk.getChunkId() != null && !trustedChunkMap.containsKey(chunk.getChunkId())) {
                // Determine rank fallback if needed
                if (chunk.getRrfScore() == null && chunk.getDenseRank() == null && chunk.getKeywordRank() == null) {
                    // Assign a synthetic 1-based rank based on input list order if absolutely no rank exists
                    chunk = RetrievedChunk.builder()
                            .chunkId(chunk.getChunkId())
                            .rrfScore(chunk.getRrfScore())
                            .denseRank(i + 1)
                            .build();
                }
                trustedChunkMap.put(chunk.getChunkId(), chunk);
            }
        }

        if (trustedChunkMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Fetch grounded candidates
        List<GraphSeedCandidateProjection> candidates = knowledgeGraphQueryRepository
                .findSeedCandidatesByChunkIds(notebookId, trustedChunkMap.keySet());

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Deduplicate by concept and calculate best signals
        Map<Long, GraphSeedConceptDto> conceptMap = new HashMap<>();
        Map<Long, Set<Long>> conceptSupportingChunks = new HashMap<>();

        for (GraphSeedCandidateProjection candidate : candidates) {
            Long chunkId = candidate.getChunkId();
            if (!trustedChunkMap.containsKey(chunkId)) {
                continue; // Fail-closed
            }

            RetrievedChunk rc = trustedChunkMap.get(chunkId);
            Long conceptId = candidate.getConceptId();
            
            conceptSupportingChunks.computeIfAbsent(conceptId, k -> new HashSet<>()).add(chunkId);
            
            GraphSeedConceptDto dto = conceptMap.getOrDefault(conceptId, GraphSeedConceptDto.builder()
                    .conceptId(conceptId)
                    .canonicalName(candidate.getCanonicalName())
                    .conceptType(candidate.getConceptType())
                    .build());
            
            // Update best RRF score (highest is best)
            if (rc.getRrfScore() != null) {
                if (dto.getBestRetrievalScore() == null || rc.getRrfScore() > dto.getBestRetrievalScore()) {
                    dto.setBestRetrievalScore(rc.getRrfScore());
                }
            } else {
                // Update best Rank (lowest is best)
                Integer fallbackRank = rc.getDenseRank() != null ? rc.getDenseRank() : rc.getKeywordRank();
                if (fallbackRank != null) {
                    if (dto.getBestRetrievalRank() == null || fallbackRank < dto.getBestRetrievalRank()) {
                        dto.setBestRetrievalRank(fallbackRank);
                    }
                }
            }
            
            // Update best confidence (highest is best)
            if (candidate.getConceptSourceConfidence() != null) {
                if (dto.getBestSourceConfidence() == null || candidate.getConceptSourceConfidence() > dto.getBestSourceConfidence()) {
                    dto.setBestSourceConfidence(candidate.getConceptSourceConfidence());
                }
            }
            
            conceptMap.put(conceptId, dto);
        }

        // Update chunk counts
        for (GraphSeedConceptDto dto : conceptMap.values()) {
            dto.setSupportingChunkCount(conceptSupportingChunks.get(dto.getConceptId()).size());
        }

        // 4. Deterministic ranking
        List<GraphSeedConceptDto> sortedSeeds = new ArrayList<>(conceptMap.values());
        sortedSeeds.sort(this::compareSeeds);

        // 5. Enforce limit
        int limit = Math.max(1, maxSeedConcepts);
        return sortedSeeds.size() > limit ? sortedSeeds.subList(0, limit) : sortedSeeds;
    }

    private int compareSeeds(GraphSeedConceptDto a, GraphSeedConceptDto b) {
        // 1. Best supporting RRF score DESC
        if (a.getBestRetrievalScore() != null && b.getBestRetrievalScore() != null) {
            int scoreCompare = b.getBestRetrievalScore().compareTo(a.getBestRetrievalScore());
            if (scoreCompare != 0) return scoreCompare;
        } else if (a.getBestRetrievalScore() != null) {
            return -1;
        } else if (b.getBestRetrievalScore() != null) {
            return 1;
        } else {
            // Fallback to retrieval rank ASC (lower is better)
            if (a.getBestRetrievalRank() != null && b.getBestRetrievalRank() != null) {
                int rankCompare = a.getBestRetrievalRank().compareTo(b.getBestRetrievalRank());
                if (rankCompare != 0) return rankCompare;
            } else if (a.getBestRetrievalRank() != null) {
                return -1;
            } else if (b.getBestRetrievalRank() != null) {
                return 1;
            }
        }

        // 2. Supporting chunk count DESC
        int countCompare = b.getSupportingChunkCount().compareTo(a.getSupportingChunkCount());
        if (countCompare != 0) return countCompare;

        // 3. Best ConceptSource confidence DESC, NULLS LAST
        if (a.getBestSourceConfidence() != null && b.getBestSourceConfidence() != null) {
            int confCompare = b.getBestSourceConfidence().compareTo(a.getBestSourceConfidence());
            if (confCompare != 0) return confCompare;
        } else if (a.getBestSourceConfidence() != null) {
            return -1;
        } else if (b.getBestSourceConfidence() != null) {
            return 1;
        }

        // 4. Concept ID ASC
        return a.getConceptId().compareTo(b.getConceptId());
    }
}
