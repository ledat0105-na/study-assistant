package com.example.studyassistant.domain.summary;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.DocumentSummary;
import com.example.studyassistant.entity.DocumentSummarySource;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.DocumentSummaryRepository;
import com.example.studyassistant.repository.DocumentSummarySourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentSummaryPersistenceService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentSummaryRepository summaryRepository;
    private final DocumentSummarySourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<SummaryResponse> getCached(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(DocumentSummaryNotFoundException::new);
        validateReady(document);
        Optional<DocumentSummary> cached = summaryRepository.findByDocumentIdAndIndexVersion(
                documentId, document.getActiveIndexVersion());
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        DocumentSummary summary = cached.get();
        if (!Objects.equals(summary.getDocumentContentHash(), document.getContentHash())) {
            return Optional.empty();
        }
        SummaryContent content;
        try {
            content = objectMapper.readValue(summary.getSummaryJson(), SummaryContent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored Summary V2 JSON is invalid", e);
        }
        return Optional.of(toResponse(documentId, summary, content,
                sourceRepository.findBySummaryIdOrderBySourceOrderAsc(summary.getId()), true));
    }

    @Transactional
    public SummaryResponse persist(
            Long userId,
            Long documentId,
            String indexVersion,
            String contentHash,
            SummaryContent content,
            List<SummaryCitation> citations,
            String modelIdentifier) {
        Document document = documentRepository.findByIdWithPessimisticLock(documentId)
                .orElseThrow(DocumentSummaryNotFoundException::new);
        if (document.getUser() == null || !Objects.equals(document.getUser().getId(), userId)) {
            throw new DocumentSummaryNotFoundException();
        }
        validateReady(document);
        if (!Objects.equals(document.getActiveIndexVersion(), indexVersion)
                || !Objects.equals(document.getContentHash(), contentHash)) {
            throw new StaleDocumentSummaryException();
        }

        Map<Long, DocumentChunk> verifiedChunks = new HashMap<>();
        for (SummaryCitation citation : citations) {
            DocumentChunk chunk = chunkRepository.findByIdAndDocumentIdAndIndexVersion(
                            citation.chunkId(), documentId, indexVersion)
                    .orElseThrow(StaleDocumentSummaryException::new);
            if (!Objects.equals(chunk.getDocument().getId(), documentId)
                    || !Objects.equals(chunk.getPageStart(), citation.pageStart())
                    || !Objects.equals(chunk.getPageEnd(), citation.pageEnd())
                    || !Objects.equals(chunk.getSectionTitle(), citation.sectionTitle())) {
                throw new StaleDocumentSummaryException();
            }
            verifiedChunks.put(chunk.getId(), chunk);
        }
        if (citations.isEmpty()) {
            throw new DocumentSummaryProviderException("Summary has no verified source citations");
        }

        LocalDateTime now = LocalDateTime.now();
        DocumentSummary summary = summaryRepository
                .findByDocumentIdAndIndexVersion(documentId, indexVersion)
                .orElseGet(DocumentSummary::new);
        if (summary.getId() != null) {
            sourceRepository.deleteBySummaryId(summary.getId());
            sourceRepository.flush();
            summaryRepository.delete(summary);
            summaryRepository.flush();
            summary = new DocumentSummary();
        }
        summary.setDocument(document);
        summary.setIndexVersion(indexVersion);
        summary.setDocumentContentHash(contentHash);
        summary.setSummaryText(formatSummary(content));
        try {
            summary.setSummaryJson(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize validated Summary V2 JSON", e);
        }
        summary.setModelIdentifier(modelIdentifier);
        summary.setGeneratedAt(now);
        summary.setCreatedAt(now);
        summary.setUpdatedAt(now);
        summaryRepository.saveAndFlush(summary);

        int order = 0;
        for (SummaryCitation citation : citations) {
            DocumentSummarySource source = new DocumentSummarySource();
            source.setSummary(summary);
            source.setDocumentChunk(verifiedChunks.get(citation.chunkId()));
            source.setSourceAlias(citation.sourceId());
            source.setSourceOrder(order++);
            source.setPageStart(citation.pageStart());
            source.setPageEnd(citation.pageEnd());
            source.setSectionTitle(citation.sectionTitle());
            sourceRepository.save(source);
        }
        return toResponse(documentId, summary, content,
                sourceRepository.findBySummaryIdOrderBySourceOrderAsc(summary.getId()), false);
    }

    private void validateReady(Document document) {
        if (!"READY".equals(document.getStatus()) || document.getActiveIndexVersion() == null) {
            throw new DocumentSummaryUnavailableException("Document must be READY and have an active index");
        }
    }

    private SummaryResponse toResponse(
            Long documentId,
            DocumentSummary summary,
            SummaryContent content,
            List<DocumentSummarySource> sources,
            boolean cached) {
        List<SummaryCitation> citations = sources.stream()
                .map(source -> new SummaryCitation(
                        source.getSourceAlias(),
                        source.getDocumentChunk().getId(),
                        documentId,
                        source.getPageStart(),
                        source.getPageEnd(),
                        source.getSectionTitle()))
                .toList();
        return new SummaryResponse(documentId, summary.getSummaryText(), cached,
                summary.getIndexVersion(), summary.getGeneratedAt(), citations, content);
    }

    private String formatSummary(SummaryContent content) {
        StringBuilder text = new StringBuilder();
        text.append("Overview\n").append(content.overview()).append("\n\nKey points\n");
        for (SummaryContent.KeyPoint point : content.keyPoints()) {
            text.append("- ").append(point.text()).append(" [")
                    .append(String.join(", ", point.sourceRefs())).append("]\n");
        }
        if (!content.importantTerms().isEmpty()) {
            text.append("\nImportant terms\n");
            for (SummaryContent.ImportantTerm term : content.importantTerms()) {
                text.append("- ").append(term.term()).append(": ").append(term.definition())
                        .append(" [").append(String.join(", ", term.sourceRefs())).append("]\n");
            }
        }
        text.append("\nConclusion\n").append(content.conclusion());
        return text.toString();
    }
}
