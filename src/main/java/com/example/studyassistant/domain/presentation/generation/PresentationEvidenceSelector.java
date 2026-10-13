package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PresentationEvidenceSelector {
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;

    public List<RetrievedChunk> select(long ownerId, long notebookId, List<Long> requestedIds) {
        List<RetrievedChunk> all = new ArrayList<>();
        List<Document> scope = new ArrayList<>(documents.findByUserIdAndNotebookId(ownerId, notebookId));
        scope.sort(Comparator.comparing(Document::getId));
        for (Document doc : scope) {
            if (requestedIds != null && !requestedIds.isEmpty() && !requestedIds.contains(doc.getId())) continue;
            if (!"READY".equals(doc.getStatus()) || doc.getActiveIndexVersion() == null || doc.getActiveIndexVersion().isBlank()) continue;
            List<DocumentChunk> active = new ArrayList<>(chunks.findByDocumentIdAndIndexVersion(doc.getId(), doc.getActiveIndexVersion()));
            active.sort(Comparator.comparing(DocumentChunk::getChunkIndex));
            for (DocumentChunk c : active) {
                if (c.getContent() == null || c.getContent().isBlank()) continue;
                all.add(RetrievedChunk.builder().chunkId(c.getId()).documentId(doc.getId()).documentTitle(doc.getFileName())
                        .chunkIndex(c.getChunkIndex()).content(c.getContent()).indexVersion(doc.getActiveIndexVersion())
                        .pageStart(c.getPageStart()).pageEnd(c.getPageEnd()).sectionTitle(c.getSectionTitle()).tokenCount(c.getTokenCount()).build());
            }
        }
        int count = Math.min(16, all.size());
        while (count > 0) {
            List<RetrievedChunk> sampled = new ArrayList<>();
            long tokens = 0;
            for (int i = 0; i < count; i++) {
                RetrievedChunk c = all.get(count == 1 ? 0 : (int) ((long) i * (all.size() - 1) / (count - 1)));
                tokens += Math.max(c.getTokenCount() == null ? 0 : c.getTokenCount(), (c.getContent().length() + 1) / 2);
                sampled.add(c);
            }
            if (tokens <= 9000) return List.copyOf(sampled);
            count--;
        }
        return List.of();
    }
}
