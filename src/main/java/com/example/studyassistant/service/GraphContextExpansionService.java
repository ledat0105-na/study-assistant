package com.example.studyassistant.service;

import com.example.studyassistant.entity.RelationType;
import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.projection.GraphContextEvidenceProjection;
import com.example.studyassistant.repository.projection.GraphContextRelationProjection;
import com.example.studyassistant.service.dto.GraphContextDto;
import com.example.studyassistant.service.dto.GraphContextEvidenceDto;
import com.example.studyassistant.service.dto.GraphContextRelationDto;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphContextExpansionService {

    private final KnowledgeGraphQueryRepository repository;

    @Value("${knowledge.graph.rag.max-relations:10}")
    private int maxRelations;

    @Value("${knowledge.graph.rag.max-evidence-per-relation:2}")
    private int maxEvidencePerRelation;

    public GraphContextDto expandContext(Long notebookId, List<GraphSeedConceptDto> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return GraphContextDto.builder()
                    .seeds(Collections.emptyList())
                    .relations(Collections.emptyList())
                    .build();
        }

        Map<Long, Integer> seedRanks = new HashMap<>();
        for (int i = 0; i < seeds.size(); i++) {
            seedRanks.putIfAbsent(seeds.get(i).getConceptId(), i + 1);
        }
        Set<Long> seedIds = seedRanks.keySet();

        // 1. Fetch relations
        List<GraphContextRelationProjection> relationProjections = repository.findGraphContextRelations(notebookId, seedIds);

        if (relationProjections.isEmpty()) {
            return GraphContextDto.builder()
                    .seeds(seeds)
                    .relations(Collections.emptyList())
                    .build();
        }

        // Defensive filtering & Deduplication
        Map<Long, GraphContextRelationDto> relationMap = new HashMap<>();
        Map<Long, Integer> relationBestSeedRank = new HashMap<>();

        for (GraphContextRelationProjection rp : relationProjections) {
            if (rp.getRelationType() == null) continue; // Unknown/null relation type excluded

            boolean sourceIsSeed = seedIds.contains(rp.getSourceConceptId());
            boolean targetIsSeed = seedIds.contains(rp.getTargetConceptId());

            if (!sourceIsSeed && !targetIsSeed) {
                continue; // Fail-closed unrelated relation
            }

            int endpoints = 0;
            int bestRank = Integer.MAX_VALUE;
            if (sourceIsSeed) {
                endpoints++;
                bestRank = Math.min(bestRank, seedRanks.get(rp.getSourceConceptId()));
            }
            if (targetIsSeed) {
                endpoints++;
                bestRank = Math.min(bestRank, seedRanks.get(rp.getTargetConceptId()));
            }

            if (!relationMap.containsKey(rp.getRelationId())) {
                GraphContextRelationDto dto = GraphContextRelationDto.builder()
                        .relationId(rp.getRelationId())
                        .sourceConceptId(rp.getSourceConceptId())
                        .sourceCanonicalName(rp.getSourceCanonicalName())
                        .targetConceptId(rp.getTargetConceptId())
                        .targetCanonicalName(rp.getTargetCanonicalName())
                        .relationType(rp.getRelationType())
                        .confidence(rp.getRelationConfidence())
                        .activeEvidenceCount(rp.getActiveEvidenceCount())
                        .seedEndpointCount(endpoints)
                        .evidence(new ArrayList<>())
                        .build();
                relationMap.put(rp.getRelationId(), dto);
                relationBestSeedRank.put(rp.getRelationId(), bestRank);
            }
        }

        if (relationMap.isEmpty()) {
            return GraphContextDto.builder()
                    .seeds(seeds)
                    .relations(Collections.emptyList())
                    .build();
        }

        // Rank relations
        List<GraphContextRelationDto> sortedRelations = new ArrayList<>(relationMap.values());
        sortedRelations.sort((a, b) -> {
            // 1. type priority
            int typeA = getTier(a.getRelationType());
            int typeB = getTier(b.getRelationType());
            if (typeA != typeB) return Integer.compare(typeA, typeB); // Lower tier value is better

            // 2. endpoints DESC
            int endComp = b.getSeedEndpointCount().compareTo(a.getSeedEndpointCount());
            if (endComp != 0) return endComp;

            // 3. best connected seed rank ASC
            int rankA = relationBestSeedRank.get(a.getRelationId());
            int rankB = relationBestSeedRank.get(b.getRelationId());
            if (rankA != rankB) return Integer.compare(rankA, rankB);

            // 4. confidence DESC NULLS LAST
            if (a.getConfidence() != null && b.getConfidence() != null) {
                int confComp = b.getConfidence().compareTo(a.getConfidence());
                if (confComp != 0) return confComp;
            } else if (a.getConfidence() != null) {
                return -1;
            } else if (b.getConfidence() != null) {
                return 1;
            }

            // 5. activeEvidenceCount DESC
            int evComp = b.getActiveEvidenceCount().compareTo(a.getActiveEvidenceCount());
            if (evComp != 0) return evComp;

            // 6. relationId ASC
            return a.getRelationId().compareTo(b.getRelationId());
        });

        int relLimit = Math.max(1, maxRelations);
        List<GraphContextRelationDto> topRelations = sortedRelations.size() > relLimit 
                ? sortedRelations.subList(0, relLimit) 
                : sortedRelations;

        Set<Long> selectedRelationIds = topRelations.stream().map(GraphContextRelationDto::getRelationId).collect(Collectors.toSet());

        // 2. Fetch evidence
        List<GraphContextEvidenceProjection> evidenceProjections = repository.findGraphContextEvidence(notebookId, selectedRelationIds);

        Map<Long, Map<Long, GraphContextEvidenceDto>> groupedEvidence = new HashMap<>();
        for (GraphContextEvidenceProjection ep : evidenceProjections) {
            if (!selectedRelationIds.contains(ep.getRelationId())) continue;

            groupedEvidence.computeIfAbsent(ep.getRelationId(), k -> new HashMap<>());
            Map<Long, GraphContextEvidenceDto> relationEvidence = groupedEvidence.get(ep.getRelationId());
            
            // Deduplicate by ConceptRelationSource.id
            if (!relationEvidence.containsKey(ep.getRelationSourceId())) {
                GraphContextEvidenceDto eDto = GraphContextEvidenceDto.builder()
                        .relationId(ep.getRelationId())
                        .relationSourceId(ep.getRelationSourceId())
                        .chunkId(ep.getChunkId())
                        .documentId(ep.getDocumentId())
                        .evidenceText(ep.getEvidenceText())
                        .confidence(ep.getConfidence())
                        .pageStart(ep.getPageStart())
                        .pageEnd(ep.getPageEnd())
                        .build();
                relationEvidence.put(ep.getRelationSourceId(), eDto);
            }
        }

        int evLimit = Math.max(1, maxEvidencePerRelation);
        for (GraphContextRelationDto rel : topRelations) {
            Map<Long, GraphContextEvidenceDto> relEvMap = groupedEvidence.getOrDefault(rel.getRelationId(), Collections.emptyMap());
            List<GraphContextEvidenceDto> sortedEv = new ArrayList<>(relEvMap.values());
            
            sortedEv.sort((a, b) -> {
                if (a.getConfidence() != null && b.getConfidence() != null) {
                    int c = b.getConfidence().compareTo(a.getConfidence());
                    if (c != 0) return c;
                } else if (a.getConfidence() != null) {
                    return -1;
                } else if (b.getConfidence() != null) {
                    return 1;
                }
                
                int chkComp = a.getChunkId().compareTo(b.getChunkId());
                if (chkComp != 0) return chkComp;
                
                return a.getRelationSourceId().compareTo(b.getRelationSourceId());
            });
            
            rel.setEvidence(sortedEv.size() > evLimit ? sortedEv.subList(0, evLimit) : sortedEv);
        }

        return GraphContextDto.builder()
                .seeds(seeds)
                .relations(topRelations)
                .build();
    }

    private int getTier(RelationType type) {
        if (type == RelationType.PREREQUISITE_OF || type == RelationType.PART_OF || type == RelationType.IS_A) {
            return 1;
        }
        if (type == RelationType.RELATED_TO) {
            return 2;
        }
        return 3;
    }
}
