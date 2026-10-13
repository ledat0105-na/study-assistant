package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QuizActiveChunkSelector {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ConceptSourceRepository conceptSourceRepository;
    private final TopicRepository topicRepository;

    private static final int MAX_CHUNKS = 60;

    public List<DocumentChunk> selectEvidence(GroundedQuizGenerationCommand cmd) {
        List<DocumentChunk> results = new ArrayList<>();
        
        if (cmd.scopeType() == QuizScopeType.DOCUMENT) {
            results = getByDocument(cmd.documentId());
        } else if (cmd.scopeType() == QuizScopeType.TOPIC) {
            Topic topic = topicRepository.findById(cmd.topicId()).orElse(null);
            if (topic != null && topic.getDocument() != null) {
                results = getByDocument(topic.getDocument().getId());
            }
        } else if (cmd.scopeType() == QuizScopeType.CONCEPT) {
            results = getByConcept(cmd.scopeConceptId());
        } else if (cmd.scopeType() == QuizScopeType.NOTEBOOK) {
            List<Document> docs = documentRepository.findByUserIdAndNotebookId(cmd.userId(), cmd.notebookId());
            for (Document doc : docs) {
                if ("READY".equals(doc.getStatus()) && doc.getActiveIndexVersion() != null) {
                    List<DocumentChunk> chunks = chunkRepository.findByDocumentIdAndIndexVersion(doc.getId(), doc.getActiveIndexVersion());
                    results.addAll(chunks);
                }
            }
        }

        results = new ArrayList<>(results);
        results.sort(Comparator.comparing((DocumentChunk c) -> c.getDocument().getId())
                .thenComparing(DocumentChunk::getChunkIndex));
        if (results.size() > MAX_CHUNKS) {
            List<DocumentChunk> sampled = new ArrayList<>();
            for (int i = 0; i < MAX_CHUNKS; i++) {
                // Include both the beginning and end of the evidence, deterministically.
                sampled.add(results.get((int) ((long) i * (results.size() - 1) / (MAX_CHUNKS - 1))));
            }
            results = sampled;
        }
        return results;
    }

    private List<DocumentChunk> getByDocument(Long docId) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc != null && "READY".equals(doc.getStatus()) && doc.getActiveIndexVersion() != null) {
            return chunkRepository.findByDocumentIdAndIndexVersion(docId, doc.getActiveIndexVersion());
        }
        return new ArrayList<>();
    }

    private List<DocumentChunk> getByConcept(Long conceptId) {
        List<ConceptSource> sources = conceptSourceRepository.findByConceptId(conceptId);
        List<DocumentChunk> activeChunks = new ArrayList<>();
        for (ConceptSource src : sources) {
            DocumentChunk c = src.getDocumentChunk();
            if (c != null && c.getDocument() != null && "READY".equals(c.getDocument().getStatus()) && c.getIndexVersion().equals(c.getDocument().getActiveIndexVersion())) {
                activeChunks.add(c);
            }
        }
        return activeChunks;
    }
}
