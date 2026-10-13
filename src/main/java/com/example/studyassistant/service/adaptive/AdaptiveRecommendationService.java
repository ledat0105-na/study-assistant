package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.adaptive.AdaptiveReasonCode;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdaptiveRecommendationService {

    private final AdaptiveCandidateService candidateService;
    private final AdaptivePolicyProperties properties;
    private final AdaptiveMasteryPolicy adaptiveMasteryPolicy;
    
    public static final String POLICY_VERSION = "ADAPTIVE_V1";

    public AdaptiveRecommendationService(AdaptiveCandidateService candidateService, AdaptivePolicyProperties properties, AdaptiveMasteryPolicy adaptiveMasteryPolicy) {
        this.candidateService = candidateService;
        this.properties = properties;
        this.adaptiveMasteryPolicy = adaptiveMasteryPolicy;
    }

    public List<AdaptiveRecommendationDto> getRecommendations(Long userId, Long notebookId, Integer requestedLimit) {
        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(userId, notebookId);
        
        // Defensive validation and setup graph
        Map<Long, AdaptiveCandidateFactDto> factMap = new HashMap<>();
        for (AdaptiveCandidateFactDto fact : facts) {
            if (factMap.containsKey(fact.getConceptId())) {
                throw new IllegalStateException("Duplicate concept ID found in candidate facts: " + fact.getConceptId());
            }
            if (fact.getMasteryState() == MasteryState.OBSERVED) {
                if (fact.getMasteryEstimate() == null) {
                    throw new IllegalStateException("OBSERVED mastery estimate cannot be null for concept: " + fact.getConceptId());
                }
                if (fact.getEvidenceCount() < 0) {
                    throw new IllegalStateException("evidenceCount cannot be negative for concept: " + fact.getConceptId());
                }
            }
            factMap.put(fact.getConceptId(), fact);
        }

        // SCC detection for cycles
        List<Set<Long>> sccs = findStronglyConnectedComponents(facts, factMap);
        Map<Long, Set<Long>> conceptToScc = new HashMap<>();
        for (Set<Long> scc : sccs) {
            for (Long cid : scc) {
                conceptToScc.put(cid, scc);
            }
        }

        List<AdaptiveCandidateFactDto> eligible = new ArrayList<>();

        for (AdaptiveCandidateFactDto fact : facts) {
            if (isMastered(fact)) {
                continue; // Normally excluded
            }

            boolean blocked = false;
            if (fact.getPrerequisiteConceptIds() != null) {
                Set<Long> myScc = conceptToScc.get(fact.getConceptId());
                for (Long prereqId : fact.getPrerequisiteConceptIds()) {
                    // Ignore internal SCC edges for eligibility
                    if (myScc != null && myScc.contains(prereqId)) {
                        continue;
                    }
                    
                    AdaptiveCandidateFactDto prereq = factMap.get(prereqId);
                    if (prereq == null || !isMastered(prereq)) {
                        blocked = true;
                        break;
                    }
                }
            }
            
            if (!blocked) {
                eligible.add(fact);
            }
        }

        // Sort
        eligible.sort(new RecommendationComparator());

        // Map and limit
        int limit = normalizeLimit(requestedLimit);
        
        return eligible.stream()
                .limit(limit)
                .map(this::toRecommendationDto)
                .collect(Collectors.toList());
    }

    private boolean isMastered(AdaptiveCandidateFactDto fact) {
        return adaptiveMasteryPolicy.isMastered(fact.getMasteryState(), fact.getMasteryEstimate(), fact.getEvidenceCount());
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return properties.getDefaultLimit();
        }
        if (requestedLimit > properties.getMaxLimit()) {
            return properties.getMaxLimit();
        }
        return requestedLimit;
    }

    private AdaptiveRecommendationDto toRecommendationDto(AdaptiveCandidateFactDto fact) {
        AdaptiveRecommendationDto dto = new AdaptiveRecommendationDto();
        dto.setConceptId(fact.getConceptId());
        dto.setConceptName(fact.getConceptName());
        dto.setMasteryState(fact.getMasteryState());
        dto.setMasteryEstimate(fact.getMasteryEstimate());
        dto.setEvidenceCount(fact.getMasteryState() == MasteryState.UNSEEN ? 0 : fact.getEvidenceCount());
        
        List<AdaptiveReasonCode> reasons = new ArrayList<>();
        if (fact.getMasteryState() == MasteryState.UNSEEN) {
            reasons.add(AdaptiveReasonCode.UNSEEN_CONCEPT);
        } else {
            if (fact.getMasteryEstimate() < properties.getMasteredThreshold()) {
                reasons.add(AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD);
            }
            if (fact.getEvidenceCount() < properties.getMasteredMinEvidence()) {
                reasons.add(AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY);
            }
        }
        dto.setReasonCodes(reasons);
        return dto;
    }

    private List<Set<Long>> findStronglyConnectedComponents(List<AdaptiveCandidateFactDto> facts, Map<Long, AdaptiveCandidateFactDto> factMap) {
        int[] time = {0};
        Map<Long, Integer> disc = new HashMap<>();
        Map<Long, Integer> low = new HashMap<>();
        Set<Long> onStack = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        List<Set<Long>> sccs = new ArrayList<>();

        for (AdaptiveCandidateFactDto fact : facts) {
            if (!disc.containsKey(fact.getConceptId())) {
                dfsTarjan(fact.getConceptId(), factMap, disc, low, onStack, stack, sccs, time);
            }
        }
        
        return sccs;
    }

    private void dfsTarjan(Long u, Map<Long, AdaptiveCandidateFactDto> factMap, Map<Long, Integer> disc, Map<Long, Integer> low, 
                           Set<Long> onStack, Deque<Long> stack, List<Set<Long>> sccs, int[] time) {
        disc.put(u, time[0]);
        low.put(u, time[0]);
        time[0]++;
        stack.push(u);
        onStack.add(u);

        AdaptiveCandidateFactDto fact = factMap.get(u);
        if (fact != null && fact.getPrerequisiteConceptIds() != null) {
            // Edges: u depends on v, so directed edge is u -> v
            for (Long v : fact.getPrerequisiteConceptIds()) {
                if (!factMap.containsKey(v)) continue; // Ignore unknown externally

                if (!disc.containsKey(v)) {
                    dfsTarjan(v, factMap, disc, low, onStack, stack, sccs, time);
                    low.put(u, Math.min(low.get(u), low.get(v)));
                } else if (onStack.contains(v)) {
                    low.put(u, Math.min(low.get(u), disc.get(v)));
                }
            }
        }

        if (low.get(u).equals(disc.get(u))) {
            Set<Long> scc = new HashSet<>();
            while (true) {
                Long w = stack.pop();
                onStack.remove(w);
                scc.add(w);
                if (w.equals(u)) break;
            }
            
            // Check self-loop if size == 1
            if (scc.size() == 1) {
                if (fact != null && fact.getPrerequisiteConceptIds() != null && fact.getPrerequisiteConceptIds().contains(u)) {
                    sccs.add(scc);
                }
            } else {
                sccs.add(scc);
            }
        }
    }

    private static class RecommendationComparator implements Comparator<AdaptiveCandidateFactDto> {
        @Override
        public int compare(AdaptiveCandidateFactDto o1, AdaptiveCandidateFactDto o2) {
            // Stage 1: OBSERVED_NOT_MASTERED before UNSEEN
            boolean o1Observed = o1.getMasteryState() == MasteryState.OBSERVED;
            boolean o2Observed = o2.getMasteryState() == MasteryState.OBSERVED;
            
            if (o1Observed && !o2Observed) return -1;
            if (!o1Observed && o2Observed) return 1;

            if (o1Observed) {
                // Stage 2: masteryEstimate ASC
                int masteryCmp = Double.compare(o1.getMasteryEstimate(), o2.getMasteryEstimate());
                if (masteryCmp != 0) return masteryCmp;

                // Stage 3: evidenceCount DESC
                int evCmp = Integer.compare(o2.getEvidenceCount(), o1.getEvidenceCount());
                if (evCmp != 0) return evCmp;
            }

            // Stage 4: firstDocumentId ASC (nulls last)
            int docCmp = compareNullsLast(o1.getFirstDocumentId(), o2.getFirstDocumentId());
            if (docCmp != 0) return docCmp;

            // Stage 5: firstChunkIndex ASC (nulls last)
            int chunkCmp = compareNullsLast(o1.getFirstChunkIndex(), o2.getFirstChunkIndex());
            if (chunkCmp != 0) return chunkCmp;

            // Stage 6: conceptId ASC
            return o1.getConceptId().compareTo(o2.getConceptId());
        }

        private <T extends Comparable<T>> int compareNullsLast(T a, T b) {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return a.compareTo(b);
        }
    }
}
