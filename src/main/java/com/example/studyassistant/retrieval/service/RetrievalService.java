package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.DocumentVersionPair;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.model.VectorRecord;
import com.example.studyassistant.retrieval.repository.DocumentChunkSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetrievalService {

    @Value("${rag.retrieval.dense-top-k:30}")
    private int denseTopK;

    @Value("${rag.retrieval.keyword-top-k:30}")
    private int keywordTopK;

    @Value("${rag.retrieval.candidate-top-k:15}")
    private int candidateTopK;

    @Value("${rag.retrieval.context-top-k:6}")
    private int contextTopK;

    @Value("${rag.retrieval.context-max-tokens:6000}")
    private int contextMaxTokens;

    @Value("${rag.retrieval.rrf-k:60}")
    private int rrfK;

    @Value("${rag.retrieval.min-dense-similarity:0.3}")
    private double minDenseSimilarity;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentChunkSearchRepository documentChunkSearchRepository;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private VectorStore vectorStore;

    /**
     * Main retrieval method.
     */
    public RetrievalResult search(Long authenticatedUserId, Long notebookId, String query) {
        return search(authenticatedUserId, notebookId, query, null);
    }

    /**
     * Main retrieval method with restricted document scope.
     */
    public RetrievalResult search(Long authenticatedUserId, Long notebookId, String query, List<Long> documentIds) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }

        // 1. Resolve authorized active documents
        List<DocumentVersionPair> authorizedPairs = resolveAuthorizedPairs(authenticatedUserId, notebookId, documentIds);
        
        if (authorizedPairs.isEmpty()) {
            log.info("No authorized READY documents found for user {} notebook {}", authenticatedUserId, notebookId);
            return new RetrievalResult(new ArrayList<>(), false, 0);
        }

        // 2. Dense Search
        List<VectorRecord> denseResults = new ArrayList<>();
        try {
            List<Double> queryVector = embeddingProvider.embed(query);
            denseResults = vectorStore.search(queryVector, authorizedPairs, denseTopK);
        } catch (Exception e) {
            log.warn("Dense retrieval failed", e);
        }

        // 3. Keyword Search
        List<DocumentChunkSearchRepository.KeywordSearchResult> keywordResults = new ArrayList<>();
        try {
            keywordResults = documentChunkSearchRepository.searchByKeyword(query, authorizedPairs, keywordTopK);
        } catch (Exception e) {
            log.warn("Keyword retrieval failed", e);
        }

        // 4. RRF Fusion
        List<FusedCandidate> fusedCandidates = fuseResults(denseResults, keywordResults, authorizedPairs);

        // 5. Context Selection (with hydration)
        return selectContext(fusedCandidates);
    }

    private List<DocumentVersionPair> resolveAuthorizedPairs(Long userId, Long notebookId, List<Long> requestedDocumentIds) {
        List<Document> documents = documentRepository.findByUserIdAndNotebookId(userId, notebookId);
        
        Set<Long> filter = null;
        if (requestedDocumentIds != null && !requestedDocumentIds.isEmpty()) {
            filter = new HashSet<>(requestedDocumentIds);
        }
        
        final Set<Long> finalFilter = filter;
        return documents.stream()
                .filter(doc -> "READY".equals(doc.getStatus()) && doc.getActiveIndexVersion() != null)
                .filter(doc -> finalFilter == null || finalFilter.contains(doc.getId()))
                .map(doc -> new DocumentVersionPair(doc.getId(), doc.getActiveIndexVersion()))
                .collect(Collectors.toList());
    }

    private List<FusedCandidate> fuseResults(List<VectorRecord> denseResults, 
                                             List<DocumentChunkSearchRepository.KeywordSearchResult> keywordResults,
                                             List<DocumentVersionPair> authorizedPairs) {
        Map<Long, FusedCandidate> candidateMap = new HashMap<>();

        // Helper to check authorization as defense in depth
        Set<String> authorizedSet = authorizedPairs.stream()
                .map(p -> p.getDocumentId() + "_" + p.getIndexVersion())
                .collect(Collectors.toSet());

        // Process Dense
        for (int i = 0; i < denseResults.size(); i++) {
            VectorRecord record = denseResults.get(i);
            String pairKey = record.getDocumentId() + "_" + record.getIndexVersion();
            if (!authorizedSet.contains(pairKey)) {
                log.warn("Defense in depth: Qdrant returned unauthorized document/version pair: {}", pairKey);
                continue;
            }
            
            FusedCandidate candidate = candidateMap.computeIfAbsent(record.getChunkId(), k -> new FusedCandidate(record.getChunkId(), record.getDocumentId(), record.getIndexVersion()));
            candidate.denseRank = i + 1;
            candidate.denseScore = record.getScore();
        }

        // Process Keyword
        for (int i = 0; i < keywordResults.size(); i++) {
            DocumentChunkSearchRepository.KeywordSearchResult record = keywordResults.get(i);
            String pairKey = record.documentId + "_" + record.indexVersion;
            if (!authorizedSet.contains(pairKey)) {
                continue;
            }
            
            FusedCandidate candidate = candidateMap.computeIfAbsent(record.chunkId, k -> new FusedCandidate(record.chunkId, record.documentId, record.indexVersion));
            candidate.keywordRank = i + 1;
            candidate.keywordScore = record.score;
        }

        // Compute RRF
        for (FusedCandidate candidate : candidateMap.values()) {
            double rrf = 0.0;
            if (candidate.denseRank != null) {
                rrf += 1.0 / (rrfK + candidate.denseRank);
            }
            if (candidate.keywordRank != null) {
                rrf += 1.0 / (rrfK + candidate.keywordRank);
            }
            candidate.rrfScore = rrf;
        }

        // Sort descending by RRF, tie-break by documentId and chunkId for determinism
        return candidateMap.values().stream()
                .sorted(Comparator.comparing(FusedCandidate::getRrfScore).reversed()
                        .thenComparing(FusedCandidate::getDocumentId)
                        .thenComparing(FusedCandidate::getChunkId))
                .limit(candidateTopK)
                .collect(Collectors.toList());
    }

    private RetrievalResult selectContext(List<FusedCandidate> candidates) {
        List<RetrievedChunk> selected = new ArrayList<>();
        int currentTokens = 0;
        
        // Tracking for overlap suppression: docVer -> list of selected chunks
        Map<String, List<DocumentChunk>> selectedChunksMap = new HashMap<>();

        for (FusedCandidate candidate : candidates) {
            if (selected.size() >= contextTopK) break;

            DocumentChunk chunk = documentChunkRepository.findById(candidate.chunkId).orElse(null);
            if (chunk == null) {
                log.warn("Missing stale chunk during hydration: chunkId={}", candidate.chunkId);
                continue;
            }
            if (!chunk.getIndexVersion().equals(candidate.indexVersion)) {
                continue;
            }

            int tokens = chunk.getTokenCount() != null ? chunk.getTokenCount() : 0;
            if (currentTokens + tokens > contextMaxTokens && !selected.isEmpty()) {
                continue;
            }

            String docVerKey = chunk.getDocument().getId() + "_" + chunk.getIndexVersion();
            List<DocumentChunk> existingChunks = selectedChunksMap.getOrDefault(docVerKey, new ArrayList<>());
            
            // Overlap check: only suppress if adjacent AND has very high textual overlap.
            // A simple proxy for high overlap is to check if one string largely contains another, or just a very simple overlap ratio.
            boolean isHighlyRedundant = false;
            for (DocumentChunk existing : existingChunks) {
                if (Math.abs(existing.getChunkIndex() - chunk.getChunkIndex()) == 1) {
                    // It's adjacent. Check text overlap.
                    if (isSubstantiallyOverlapping(existing.getContent(), chunk.getContent())) {
                        isHighlyRedundant = true;
                        break;
                    }
                }
            }

            if (isHighlyRedundant) {
                continue; 
            }

            RetrievedChunk hydrated = RetrievedChunk.builder()
                    .chunkId(chunk.getId())
                    .documentId(chunk.getDocument().getId())
                    .documentTitle(chunk.getDocument().getFileName())
                    .chunkIndex(chunk.getChunkIndex())
                    .content(chunk.getContent())
                    .pageStart(chunk.getPageStart())
                    .pageEnd(chunk.getPageEnd())
                    .sectionTitle(chunk.getSectionTitle())
                    .tokenCount(tokens)
                    .indexVersion(chunk.getIndexVersion())
                    .denseRank(candidate.denseRank)
                    .keywordRank(candidate.keywordRank)
                    .denseScore(candidate.denseScore)
                    .keywordScore(candidate.keywordScore)
                    .rrfScore(candidate.rrfScore)
                    .build();

            selected.add(hydrated);
            currentTokens += tokens;
            
            existingChunks.add(chunk);
            selectedChunksMap.put(docVerKey, existingChunks);
        }

        boolean hasEvidence = evaluateEvidencePolicy(selected);

        return new RetrievalResult(selected, hasEvidence, currentTokens);
    }
    
    private boolean isSubstantiallyOverlapping(String text1, String text2) {
        if (text1 == null || text2 == null) return false;
        // Simple heuristic: if more than 50% of the shorter text is contained in the longer text
        String shorter = text1.length() < text2.length() ? text1 : text2;
        String longer = text1.length() >= text2.length() ? text1 : text2;
        
        // Very basic V1 overlap: just check if 50% length prefix or suffix is contained
        if (shorter.length() < 50) return false;
        String prefix = shorter.substring(0, shorter.length() / 2);
        String suffix = shorter.substring(shorter.length() / 2);
        return longer.contains(prefix) || longer.contains(suffix);
    }

    public boolean evaluateEvidencePolicy(List<RetrievedChunk> selected) {
        if (selected.isEmpty()) return false;
        
        for (RetrievedChunk chunk : selected) {
            // Strong dense evidence
            if (chunk.getDenseScore() != null && chunk.getDenseScore() >= minDenseSimilarity) {
                return true;
            }
            // Credible keyword evidence (e.g. matched in top 5 keyword search and not just noisy tail)
            if (chunk.getKeywordRank() != null && chunk.getKeywordRank() <= 5) {
                return true;
            }
            // Agreement between branches (retrieved by both)
            if (chunk.getDenseRank() != null && chunk.getKeywordRank() != null) {
                return true;
            }
        }
        
        return false;
    }

    private static class FusedCandidate {
        Long chunkId;
        Long documentId;
        String indexVersion;
        
        Integer denseRank;
        Integer keywordRank;
        Double denseScore;
        Double keywordScore;
        Double rrfScore;

        public FusedCandidate(Long chunkId, Long documentId, String indexVersion) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.indexVersion = indexVersion;
        }

        public Double getRrfScore() { return rrfScore; }
        public Long getDocumentId() { return documentId; }
        public Long getChunkId() { return chunkId; }
    }
}
