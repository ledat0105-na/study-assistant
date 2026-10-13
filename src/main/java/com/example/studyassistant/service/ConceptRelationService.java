package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.ConceptRelationRepository;
import com.example.studyassistant.repository.ConceptRelationSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptRelationService {

    private final ConceptRelationRepository conceptRelationRepository;
    private final ConceptRelationSourceRepository conceptRelationSourceRepository;
    private final ConceptRelationPersistenceHelper persistenceHelper;

    public ConceptRelationSource getOrCreateWithSource(
            Concept sourceConcept,
            Concept targetConcept,
            RelationType relationType,
            Double relationConfidence,
            DocumentChunk chunk,
            String evidenceText,
            Double evidenceConfidence) {
        return getOrCreateWithSource(sourceConcept, targetConcept, relationType, relationConfidence,
                chunk, evidenceText, evidenceConfidence, KnowledgeScope.fromChunk(chunk));
    }

    public ConceptRelationSource getOrCreateWithSource(
            Concept sourceConcept, Concept targetConcept, RelationType relationType, Double relationConfidence,
            DocumentChunk chunk, String evidenceText, Double evidenceConfidence, KnowledgeScope scope) {
        KnowledgeScope.validateRelation(sourceConcept, targetConcept);
        scope.validate(chunk.getDocument(), chunk);
        if (!scope.contains(sourceConcept) || !scope.contains(targetConcept)) {
            throw new IllegalArgumentException("Relation does not belong to target scope");
        }

        // Optional Pre-check to avoid exception overhead for existing relations
        Optional<ConceptRelation> preCheckRelation = persistenceHelper.findExistingRelation(sourceConcept.getId(), targetConcept.getId(), relationType);
        if (preCheckRelation.isPresent()) {
            ConceptRelation relation = preCheckRelation.get();
            
            // Optional Source Pre-check
            Optional<ConceptRelationSource> preCheckSource = persistenceHelper.findExistingSource(relation.getId(), chunk.getId());
            if (preCheckSource.isPresent()) {
                return preCheckSource.get();
            }

            try {
                return persistenceHelper.insertSourceOnly(relation, chunk, evidenceText, evidenceConfidence, scope);
            } catch (DataIntegrityViolationException e) {
                if (!KnowledgeGraphDuplicate.isDuplicate(e)) throw e;
                return persistenceHelper.findExistingSource(relation.getId(), chunk.getId())
                        .orElseThrow(() -> e); // Rethrow original on unrelated integrity error
            }
        }

        try {
            return persistenceHelper.insertRelationAndSource(
                    sourceConcept, targetConcept, relationType, relationConfidence,
                    chunk, evidenceText, evidenceConfidence, scope);
        } catch (DataIntegrityViolationException e) {
                if (!KnowledgeGraphDuplicate.isDuplicate(e)) throw e;
            // Recover duplicate race
            log.info("Recovering duplicate relation or source race for {} -> {}", sourceConcept.getId(), targetConcept.getId());
            ConceptRelation relation = persistenceHelper.findExistingRelation(sourceConcept.getId(), targetConcept.getId(), relationType)
                    .orElseThrow(() -> e); // Rethrow original on unrelated integrity error

            try {
                return persistenceHelper.insertSourceOnly(relation, chunk, evidenceText, evidenceConfidence, scope);
            } catch (DataIntegrityViolationException e2) {
                if (!KnowledgeGraphDuplicate.isDuplicate(e2)) throw e2;
                return persistenceHelper.findExistingSource(relation.getId(), chunk.getId())
                        .orElseThrow(() -> e2); // Rethrow original on unrelated integrity error
            }
        }
    }
}
