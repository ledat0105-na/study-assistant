package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.adaptive.AdaptiveCandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdaptiveCandidateService {

    private final AdaptiveCandidateRepository adaptiveCandidateRepository;
    private final NotebookRepository notebookRepository;

    public AdaptiveCandidateService(AdaptiveCandidateRepository adaptiveCandidateRepository,
                                    NotebookRepository notebookRepository) {
        this.adaptiveCandidateRepository = adaptiveCandidateRepository;
        this.notebookRepository = notebookRepository;
    }

    @Transactional(readOnly = true)
    public List<AdaptiveCandidateFactDto> getCandidateFacts(Long userId, Long notebookId) {
        // Ownership / Existence isolation
        boolean isOwner = notebookRepository.findById(notebookId)
                .map(nb -> nb.getUser().getId().equals(userId))
                .orElse(false);

        if (!isOwner) {
            // Masking absent vs unowned
            return Collections.emptyList();
        }

        // 1. Fetch Candidates + Mastery
        List<Object[]> rawCandidates = adaptiveCandidateRepository.findCandidateFacts(notebookId, userId);
        
        // 2. Fetch Prerequisite Edges (Dependent -> Prerequisite)
        List<Object[]> rawPrereqs = adaptiveCandidateRepository.findActivePrerequisiteEdges(notebookId);
        
        // 3. Fetch Source Order Facts
        List<Object[]> rawSourceOrders = adaptiveCandidateRepository.findActiveSourceOrderFacts(notebookId);

        // Build Prerequisites Map
        // Map<DependentConceptId, Set<PrerequisiteConceptId>>
        Map<Long, Set<Long>> prereqMap = new HashMap<>();
        for (Object[] row : rawPrereqs) {
            Long dependentId = (Long) row[0];
            Long prereqId = (Long) row[1];
            prereqMap.computeIfAbsent(dependentId, k -> new HashSet<>()).add(prereqId);
        }

        // Build Source Order Map
        // Map<ConceptId, Object[]> -> Object[] = {documentId, chunkIndex}
        // Deterministic locator: order by documentId ASC, chunkIndex ASC
        Map<Long, Object[]> sourceOrderMap = new HashMap<>();
        for (Object[] row : rawSourceOrders) {
            Long conceptId = (Long) row[0];
            Long docId = (Long) row[1];
            Integer chunkIdx = (Integer) row[2];
            
            sourceOrderMap.compute(conceptId, (k, current) -> {
                if (current == null) return row;
                Long currDoc = (Long) current[1];
                Integer currChunk = (Integer) current[2];
                if (docId < currDoc) return row;
                if (docId.equals(currDoc) && chunkIdx < currChunk) return row;
                return current;
            });
        }

        // Assemble DTOs
        List<AdaptiveCandidateFactDto> results = new ArrayList<>();
        for (Object[] row : rawCandidates) {
            Long conceptId = (Long) row[0];
            String conceptName = (String) row[1];
            Double masteryEstimate = (Double) row[2];
            Integer evidenceCount = (Integer) row[3];
            LocalDateTime lastEvidenceAt = (LocalDateTime) row[4];

            AdaptiveCandidateFactDto dto = new AdaptiveCandidateFactDto();
            dto.setConceptId(conceptId);
            dto.setConceptName(conceptName);
            
            if (evidenceCount == null || masteryEstimate == null) {
                dto.setMasteryState(MasteryState.UNSEEN);
                dto.setMasteryEstimate(null);
                dto.setEvidenceCount(0);
                dto.setLastEvidenceAt(null);
            } else {
                dto.setMasteryState(MasteryState.OBSERVED);
                dto.setMasteryEstimate(masteryEstimate);
                dto.setEvidenceCount(evidenceCount);
                dto.setLastEvidenceAt(lastEvidenceAt);
            }

            Object[] sourceOrder = sourceOrderMap.get(conceptId);
            if (sourceOrder != null) {
                dto.setFirstDocumentId((Long) sourceOrder[1]);
                dto.setFirstChunkIndex((Integer) sourceOrder[2]);
            }

            Set<Long> prereqs = prereqMap.getOrDefault(conceptId, Collections.emptySet());
            dto.setPrerequisiteConceptIds(new ArrayList<>(prereqs));

            results.add(dto);
        }

        // Deterministic candidate ordering (by ID as a stable baseline, AL1.3 will implement policy ranking)
        results.sort(Comparator.comparing(AdaptiveCandidateFactDto::getConceptId));

        return results;
    }
}
