package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class QuizScopeValidator {
    
    private final NotebookRepository notebookRepository;
    private final DocumentRepository documentRepository;
    private final TopicRepository topicRepository;
    private final ConceptRepository conceptRepository;

    public void validateScope(GroundedQuizGenerationCommand cmd) {
        if (cmd.scopeType() == null) throw new IllegalArgumentException("scopeType is required");
        if (cmd.scopeType() == QuizScopeType.DOCUMENT) {
            if (cmd.documentId() == null) throw new IllegalArgumentException("DOCUMENT scope requires documentId");
            if (cmd.topicId() != null || cmd.scopeConceptId() != null) throw new IllegalArgumentException("DOCUMENT scope must have topic and scopeConcept NULL");
            Document doc = documentRepository.findKnowledgeGraphDocumentByIdAndUserId(cmd.documentId(), cmd.userId())
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
            if (!"READY".equals(doc.getStatus()) || doc.getActiveIndexVersion() == null || doc.getActiveIndexVersion().isBlank()) {
                throw new IllegalArgumentException("Document must be READY and have an active index");
            }
            Notebook documentNotebook = doc.getNotebook();
            if (documentNotebook != null && (documentNotebook.getUser() == null
                    || !Objects.equals(documentNotebook.getUser().getId(), cmd.userId())
                    || Boolean.TRUE.equals(documentNotebook.getIsDeleted()))) {
                throw new IllegalArgumentException("Notebook ownership mismatch");
            }
            if (cmd.notebookId() != null && (documentNotebook == null
                    || !cmd.notebookId().equals(documentNotebook.getId()))) {
                throw new IllegalArgumentException("Document does not belong to the specified notebook");
            }
            return;
        }
        if (cmd.notebookId() == null) {
            throw new IllegalArgumentException("notebookId is required for this scope");
        }
        
        Notebook notebook = notebookRepository.findById(cmd.notebookId())
            .orElseThrow(() -> new IllegalArgumentException("Notebook not found"));
        
        if (!notebook.getUser().getId().equals(cmd.userId())) {
            throw new IllegalArgumentException("Notebook ownership mismatch");
        }

        switch (cmd.scopeType()) {
            case NOTEBOOK:
                if (cmd.documentId() != null || cmd.topicId() != null || cmd.scopeConceptId() != null) {
                    throw new IllegalArgumentException("NOTEBOOK scope must have document, topic, and scopeConcept NULL");
                }
                break;
            case TOPIC:
                if (cmd.topicId() == null) throw new IllegalArgumentException("TOPIC scope requires topicId");
                if (cmd.scopeConceptId() != null) throw new IllegalArgumentException("TOPIC scope must have scopeConcept NULL");
                Topic topic = topicRepository.findById(cmd.topicId()).orElseThrow(() -> new IllegalArgumentException("Topic not found"));
                if (topic.getDocument() == null || topic.getDocument().getNotebook() == null || !topic.getDocument().getNotebook().getId().equals(cmd.notebookId())) {
                    throw new IllegalArgumentException("Topic's document does not belong to notebook");
                }
                if (cmd.documentId() != null && !cmd.documentId().equals(topic.getDocument().getId())) {
                    throw new IllegalArgumentException("documentId provided but does not match topic's document");
                }
                break;
            case CONCEPT:
                if (cmd.scopeConceptId() == null) throw new IllegalArgumentException("CONCEPT scope requires scopeConceptId");
                if (cmd.documentId() != null || cmd.topicId() != null) throw new IllegalArgumentException("CONCEPT scope must have document and topic NULL");
                Concept concept = conceptRepository.findById(cmd.scopeConceptId()).orElseThrow(() -> new IllegalArgumentException("Concept not found"));
                if (concept.getNotebook() == null || !concept.getNotebook().getId().equals(cmd.notebookId())) {
                    throw new IllegalArgumentException("Concept does not belong to notebook");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported scope type");
        }
    }
}
