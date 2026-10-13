package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ConceptSource;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.repository.ConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final ConceptNameNormalizer normalizer;
    private final ConceptPersistenceHelper persistenceHelper;

    public Concept getOrCreate(Notebook notebook, String rawName, String description, String conceptType) {
        if (notebook == null || notebook.getId() == null) {
            throw new IllegalArgumentException("Notebook must be provided and persisted");
        }

        String normalizedName = normalizer.normalize(rawName);
        String canonicalName = normalizer.canonicalize(rawName);

        // Try to find existing
        Optional<Concept> existing = conceptRepository.findByNotebookIdAndNormalizedName(
                notebook.getId(), normalizedName);
        
        if (existing.isPresent()) {
            return existing.get();
        }

        // If not found, create new
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName(canonicalName);
        concept.setNormalizedName(normalizedName);
        concept.setDescription(description);
        concept.setConceptType(conceptType);

        try {
            return persistenceHelper.insert(concept);
        } catch (DataIntegrityViolationException original) {
                if (!KnowledgeGraphDuplicate.isDuplicate(original)) throw original;
            log.warn("Concurrent concept creation detected for notebook {} and concept {}. Attempting to fetch existing.", 
                    notebook.getId(), normalizedName);
            
            return persistenceHelper.findExisting(notebook.getId(), normalizedName)
                    .orElseThrow(() -> original);
        }
    }

    public ConceptSource getOrCreateWithSource(
            Notebook notebook,
            String name,
            String description,
            String conceptType,
            com.example.studyassistant.entity.DocumentChunk documentChunk,
            String evidenceText,
            Double confidence) {
        KnowledgeScope scope = KnowledgeScope.fromChunk(documentChunk);
        if (!java.util.Objects.equals(scope.notebookId(), notebook == null ? null : notebook.getId())) {
            throw new IllegalArgumentException("Notebook does not match evidence scope");
        }
        return getOrCreateWithSource(scope, name, description, conceptType, documentChunk, evidenceText, confidence);
    }

    public ConceptSource getOrCreateWithSource(
            KnowledgeScope scope, String name, String description, String conceptType,
            com.example.studyassistant.entity.DocumentChunk documentChunk, String evidenceText, Double confidence) {
        scope.validate(documentChunk.getDocument(), documentChunk);
        String normalized = normalizer.normalize(name);
        String canonical = normalizer.canonicalize(name);

        Optional<Concept> existingOpt = scope.notebookId() != null
                ? conceptRepository.findByNotebookIdAndNormalizedName(scope.notebookId(), normalized)
                : conceptRepository.findByDocumentIdAndNormalizedName(scope.documentId(), normalized);
        if (existingOpt.isPresent()) {
            try {
                return persistenceHelper.insertSourceOnly(existingOpt.get(), documentChunk, evidenceText, confidence, scope, description);
            } catch (DataIntegrityViolationException original) {
                if (!KnowledgeGraphDuplicate.isDuplicate(original)) throw original;
                log.warn("Concurrent concept source creation detected for concept {}. Attempting to fetch existing.", existingOpt.get().getId());
                return persistenceHelper.findExistingSource(existingOpt.get().getId(), documentChunk.getId())
                        .orElseThrow(() -> original);
            }
        }

        Concept concept = new Concept();
        if (scope.notebookId() != null) concept.setNotebook(documentChunk.getDocument().getNotebook());
        else concept.setDocument(documentChunk.getDocument());
        concept.setCanonicalName(canonical);
        concept.setNormalizedName(normalized);
        concept.setDescription(description);
        concept.setConceptType(conceptType);

        try {
            return persistenceHelper.insertConceptAndSource(concept, documentChunk, evidenceText, confidence, scope);
        } catch (DataIntegrityViolationException original) {
                if (!KnowledgeGraphDuplicate.isDuplicate(original)) throw original;
            log.warn("Concurrent concept creation detected (with source) for document {} and concept {}. Attempting to fetch existing.",
                    scope.documentId(), normalized);
            Concept existing = persistenceHelper.findExisting(scope, normalized)
                    .orElseThrow(() -> original);
            
            try {
                return persistenceHelper.insertSourceOnly(existing, documentChunk, evidenceText, confidence, scope, description);
            } catch (DataIntegrityViolationException srcEx) {
                if (!KnowledgeGraphDuplicate.isDuplicate(srcEx)) throw srcEx;
                log.warn("Concurrent concept source creation detected on fallback for concept {}. Attempting to fetch existing.", existing.getId());
                return persistenceHelper.findExistingSource(existing.getId(), documentChunk.getId())
                        .orElseThrow(() -> srcEx);
            }
        }
    }
}
