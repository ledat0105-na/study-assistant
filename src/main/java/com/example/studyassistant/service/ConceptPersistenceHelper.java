package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.ConceptSourceRepository;
import com.example.studyassistant.entity.ConceptSource;
import com.example.studyassistant.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConceptPersistenceHelper {

    private final ConceptRepository conceptRepository;
    private final ConceptSourceRepository conceptSourceRepository;
    private final KnowledgeGraphEvidenceValidator evidenceValidator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Concept insert(Concept concept) {
        concept.validateScope();
        return conceptRepository.saveAndFlush(concept);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.util.Optional<Concept> findExisting(Long notebookId, String normalizedName) {
        return conceptRepository.findByNotebookIdAndNormalizedName(notebookId, normalizedName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.util.Optional<Concept> findExisting(KnowledgeScope scope, String normalizedName) {
        return scope.notebookId() != null
                ? conceptRepository.findByNotebookIdAndNormalizedName(scope.notebookId(), normalizedName)
                : conceptRepository.findByDocumentIdAndNormalizedName(scope.documentId(), normalizedName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.util.Optional<ConceptSource> findExistingSource(Long conceptId, Long documentChunkId) {
        return conceptSourceRepository.findByConceptIdAndDocumentChunkId(conceptId, documentChunkId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptSource insertConceptAndSource(Concept concept, DocumentChunk chunk, String evidenceText, Double confidence) {
        return insertConceptAndSource(concept, chunk, evidenceText, confidence, KnowledgeScope.fromChunk(chunk));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptSource insertConceptAndSource(Concept concept, DocumentChunk chunk, String evidenceText, Double confidence, KnowledgeScope scope) {
        chunk = evidenceValidator.validate(scope, concept, chunk, evidenceText);
        requireCapacity(chunk);
        Concept savedConcept = conceptRepository.saveAndFlush(concept);
        
        ConceptSource source = new ConceptSource();
        source.setConcept(savedConcept);
        source.setDocumentChunk(chunk);
        source.setEvidenceText(evidenceText);
        source.setConfidence(confidence);
        return conceptSourceRepository.saveAndFlush(source);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptSource insertSourceOnly(Concept concept, DocumentChunk chunk, String evidenceText, Double confidence) {
        return insertSourceOnly(concept, chunk, evidenceText, confidence, KnowledgeScope.fromChunk(chunk));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptSource insertSourceOnly(Concept concept, DocumentChunk chunk, String evidenceText, Double confidence, KnowledgeScope scope) {
        return insertSourceOnly(concept, chunk, evidenceText, confidence, scope, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptSource insertSourceOnly(Concept concept, DocumentChunk chunk, String evidenceText, Double confidence, KnowledgeScope scope, String description) {
        chunk = evidenceValidator.validate(scope, concept, chunk, evidenceText);
        // Refresh only this validated concept; keep its identity and existing provenance.
        if (description != null && !description.isBlank()) {
            Concept managed = conceptRepository.findById(concept.getId()).orElseThrow();
            if (!scope.contains(managed)) throw new IllegalStateException("Concept scope changed");
            managed.setDescription(description);
            conceptRepository.saveAndFlush(managed);
            concept = managed;
        }
        java.util.Optional<ConceptSource> existing = conceptSourceRepository.findByConceptIdAndDocumentChunkId(concept.getId(), chunk.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        
        requireCapacity(chunk);
        ConceptSource source = new ConceptSource();
        source.setConcept(concept);
        source.setDocumentChunk(chunk);
        source.setEvidenceText(evidenceText);
        source.setConfidence(confidence);
        return conceptSourceRepository.saveAndFlush(source);
    }
    private void requireCapacity(DocumentChunk chunk) {
        // Evidence validation has locked the document for this transaction. Count before
        // inserting the identity as well as its source, so rejected additions leave no orphan.
        // Existing identities/sources and description refreshes remain usable at the limit.
        if (conceptSourceRepository.countByDocumentChunkId(chunk.getId()) >= 50) {
            throw new ConceptCandidateLimitException();
        }
    }
}
