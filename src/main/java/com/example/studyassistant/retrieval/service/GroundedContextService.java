package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedContext;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.util.PromptSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.regex.Pattern;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroundedContextService {

    private final RetrievalService retrievalService;
    private final TokenCounter tokenCounter;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    private static final Pattern SUMMARY = Pattern.compile("\\b(tom tat|tong quan|noi dung chinh|y chinh|summary|summarize|overview)\\b");
    private static final Pattern SECTION = Pattern.compile("\\b(?:muc|phan|chuong|section|chapter)\\s+(\\d+(?:\\.\\d+)*)");

    public GroundedContext buildContext(Long userId, Long notebookId, String query, long remainingBudget) {
        return buildContext(userId, notebookId, query, remainingBudget, null);
    }

    public GroundedContext buildContext(Long userId, Long notebookId, String query, long remainingBudget,
                                        List<Long> documentIds) {
        boolean selectedDocuments = notebookId == null && documentIds != null && !documentIds.isEmpty();
        String normalizedQuery = normalize(query);
        var section = SECTION.matcher(normalizedQuery);
        String sectionNumber = section.find() ? section.group(1) : null;
        boolean overview = selectedDocuments && sectionNumber == null && SUMMARY.matcher(normalizedQuery).find();
        RetrievalResult retrievalResult;
        try {
            retrievalResult = overview ? new RetrievalResult(List.of(), false, 0) : documentIds == null ? retrievalService.search(userId, notebookId, query)
                    : retrievalService.search(userId, notebookId, query, documentIds);
        } catch (Exception e) {
            log.error("Infrastructure failure during retrieval", e);
            throw new RuntimeException("Infrastructure failure during retrieval", e);
        }

        if (!selectedDocuments && !retrievalResult.isHasSufficientEvidence()) {
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

        if (selectedDocuments && (bestSubset == null || sectionNumber != null)) {
            List<RetrievedChunk> sampled = sampleSelectedDocuments(userId, documentIds, sectionNumber, remainingBudget);
            if (!sampled.isEmpty() && (bestSubset == null || sampled.stream().anyMatch(chunk ->
                    sectionNumber != null && matchesSection(chunk.getSectionTitle(), chunk.getContent(), sectionNumber)))) {
                bestSubset = sampled;
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

    private List<RetrievedChunk> sampleSelectedDocuments(Long userId, List<Long> ids, String section, long budget) {
        List<List<DocumentChunk>> documents = new ArrayList<>();
        for (Long id : ids) {
            documentRepository.findByIdAndUserId(id, userId)
                    .filter(doc -> "READY".equals(doc.getStatus()) && doc.getActiveIndexVersion() != null)
                    .ifPresent(doc -> {
                        List<DocumentChunk> chunks = chunkRepository
                                .findByDocumentIdAndIndexVersionOrderByChunkIndexAsc(id, doc.getActiveIndexVersion())
                                .stream().filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank()).toList();
                        if (!chunks.isEmpty()) documents.add(chunks);
                    });
        }

        List<DocumentChunk> candidates = new ArrayList<>();
        if (section != null) {
            for (List<DocumentChunk> chunks : documents) {
                chunks.stream().filter(chunk -> matchesSection(chunk.getSectionTitle(), chunk.getContent(), section))
                        .limit(12).forEach(candidates::add);
            }
        }
        if (candidates.isEmpty()) {
            // Spread overview excerpts across the document and alternate between selected sources.
            for (double fraction : new double[]{0, 0.5, 1, 0.25, 0.75, 0.125, 0.375, 0.625, 0.875}) {
                for (List<DocumentChunk> chunks : documents) {
                    candidates.add(chunks.get((int) Math.round(fraction * (chunks.size() - 1))));
                }
            }
        }

        List<RetrievedChunk> selected = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (DocumentChunk chunk : candidates) {
            if (selected.size() >= 12) break;
            if (!seen.add(chunk.getId())) continue;
            selected.add(RetrievedChunk.builder().chunkId(chunk.getId()).documentId(chunk.getDocument().getId())
                    .documentTitle(chunk.getDocument().getFileName()).chunkIndex(chunk.getChunkIndex())
                    .content(chunk.getContent()).sectionTitle(chunk.getSectionTitle()).pageStart(chunk.getPageStart())
                    .pageEnd(chunk.getPageEnd()).indexVersion(chunk.getIndexVersion()).build());
            if (tokenCounter.count(buildSourceDataString(selected)) > budget) selected.remove(selected.size() - 1);
        }
        selected.sort(Comparator.comparing(RetrievedChunk::getDocumentId).thenComparing(RetrievedChunk::getChunkIndex));
        return selected;
    }

    private static boolean matchesSection(String title, String content, String number) {
        String prefix = "(?:(?:muc|phan|chuong|section|chapter)\\s+)?" + Pattern.quote(number);
        Pattern heading = Pattern.compile("(?m)^\\h*" + prefix + "(?:\\.(?=\\D|$))?(?=\\h|:|$)");
        return heading.matcher(normalize(title)).find() || heading.matcher(normalize(content)).find();
    }

    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('đ', 'd');
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
                if (chunk.getSectionTitle() != null && !chunk.getSectionTitle().isBlank()) {
                    contextBuilder.append("Section: ")
                            .append(PromptSanitizer.sanitizeUntrustedPromptText(chunk.getSectionTitle())).append("\n");
                }
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