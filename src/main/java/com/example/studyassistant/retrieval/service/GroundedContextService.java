package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedContext;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.util.PromptSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroundedContextService {

    private final RetrievalService retrievalService;
    private final TokenCounter tokenCounter;

    public GroundedContext buildContext(Long userId, Long notebookId, String query, long remainingBudget) {
        RetrievalResult retrievalResult;
        try {
            retrievalResult = retrievalService.search(userId, notebookId, query);
        } catch (Exception e) {
            log.error("Infrastructure failure during retrieval", e);
            throw new RuntimeException("Infrastructure failure during retrieval", e);
        }

        if (!retrievalResult.isHasSufficientEvidence()) {
            return new GroundedContext(false, "", Map.of(), List.of());
        }

        List<RetrievedChunk> originalChunks = retrievalResult.getChunks();
        List<RetrievedChunk> bestSubset = null;

        for (int i = 0; i < originalChunks.size(); i++) {
            RetrievedChunk anchor = originalChunks.get(i);
            if (retrievalService.evaluateEvidencePolicy(java.util.Collections.singletonList(anchor))) {
                List<RetrievedChunk> candidate = new ArrayList<>();
                candidate.add(anchor);

                if (tokenCounter.count(buildSourceDataString(candidate)) > remainingBudget) {
                    continue; // Anchor itself is too big
                }

                for (int j = 0; j < originalChunks.size(); j++) {
                    if (i == j) continue;

                    RetrievedChunk next = originalChunks.get(j);
                    candidate.add(next);
                    candidate.sort(java.util.Comparator.comparingInt(originalChunks::indexOf));

                    if (tokenCounter.count(buildSourceDataString(candidate)) > remainingBudget) {
                        candidate.remove(next);
                    }
                }

                bestSubset = candidate;
                break;
            }
        }

        if (bestSubset == null || bestSubset.isEmpty()) {
            return new GroundedContext(false, "", Map.of(), List.of());
        }

        String sourceData = buildSourceDataString(bestSubset);
        
        Map<String, CitationDto> sourceMap = new LinkedHashMap<>();
        int sourceIndex = 1;
        Set<Long> seenChunkIds = new HashSet<>();
        
        for (RetrievedChunk chunk : bestSubset) {
            if (!seenChunkIds.contains(chunk.getChunkId())) {
                seenChunkIds.add(chunk.getChunkId());
                String sourceId = "S" + sourceIndex++;
                
                String pages = formatPages(chunk.getPageStart(), chunk.getPageEnd());
                
                CitationDto dto = CitationDto.builder()
                        .citationId(sourceId)
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .documentTitle(chunk.getDocumentTitle())
                        .pages(pages)
                        .sectionTitle(chunk.getSectionTitle())
                        .snippet(generateSnippet(chunk.getContent()))
                        .build();
                
                sourceMap.put(sourceId, dto);
            }
        }

        return new GroundedContext(
            true,
            sourceData,
            Collections.unmodifiableMap(sourceMap),
            List.copyOf(bestSubset)
        );
    }

    private String buildSourceDataString(List<RetrievedChunk> chunks) {
        StringBuilder contextBuilder = new StringBuilder();
        int sourceIndex = 1;
        Set<Long> seenChunkIds = new HashSet<>();
        
        for (RetrievedChunk chunk : chunks) {
            if (!seenChunkIds.contains(chunk.getChunkId())) {
                seenChunkIds.add(chunk.getChunkId());
                String sourceId = "S" + sourceIndex++;
                contextBuilder.append("[").append(sourceId).append("]\n");
                contextBuilder.append(PromptSanitizer.sanitizeUntrustedPromptText(chunk.getContent())).append("\n\n");
            }
        }
        return contextBuilder.toString();
    }
    
    private String formatPages(Integer pageStart, Integer pageEnd) {
        if (pageStart == null && pageEnd == null) return null;
        if (pageStart != null && pageStart.equals(pageEnd)) {
            return "page " + pageStart;
        }
        if (pageStart != null && pageEnd != null) {
            return "pages " + pageStart + "\u2013" + pageEnd;
        }
        return null;
    }
    
    private String generateSnippet(String content) {
        if (content == null) return "";
        content = content.trim();
        if (content.length() <= 150) return content;
        return content.substring(0, 147) + "...";
    }
}