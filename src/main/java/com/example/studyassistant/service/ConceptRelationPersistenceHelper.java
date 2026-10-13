package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.ConceptRelationRepository;
import com.example.studyassistant.repository.ConceptRelationSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConceptRelationPersistenceHelper {

    private final ConceptRelationRepository relationRepository;
    private final ConceptRelationSourceRepository sourceRepository;
    private final KnowledgeGraphEvidenceValidator evidenceValidator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptRelationSource insertRelationAndSource(
            Concept sourceConcept, Concept targetConcept, RelationType relationType, Double relationConfidence,
            DocumentChunk chunk, String evidenceText, Double evidenceConfidence) {
        return insertRelationAndSource(sourceConcept, targetConcept, relationType, relationConfidence,
                chunk, evidenceText, evidenceConfidence, KnowledgeScope.fromChunk(chunk));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptRelationSource insertRelationAndSource(
            Concept sourceConcept, Concept targetConcept, RelationType relationType, Double relationConfidence,
            DocumentChunk chunk, String evidenceText, Double evidenceConfidence, KnowledgeScope scope) {
        KnowledgeScope.validateRelation(sourceConcept, targetConcept);
        chunk = evidenceValidator.validate(scope, sourceConcept, chunk, evidenceText);
        if (!scope.contains(targetConcept)) throw new IllegalArgumentException("Target concept outside scope");

        // Use findByConceptId instead if findBySourceConceptIdAndTargetConceptIdAndRelationType doesn't exist.
        // Actually, let's just let it throw DataIntegrityViolationException if it exists, or check manually.
        // Wait, repository doesn't have findBySourceConceptIdAndTargetConceptIdAndRelationType yet.
        // Let's just create it directly, and rely on uk_source_target_type to throw DataIntegrityViolationException.
        
        ConceptRelation relation = new ConceptRelation();
        relation.setSourceConcept(sourceConcept);
        relation.setTargetConcept(targetConcept);
        relation.setRelationType(relationType);
        relation.setConfidence(relationConfidence);
        relation = relationRepository.saveAndFlush(relation);

        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relation);
        src.setDocumentChunk(chunk);
        src.setEvidenceText(evidenceText);
        src.setConfidence(evidenceConfidence);
        return sourceRepository.saveAndFlush(src);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ConceptRelation> findExistingRelation(Long sourceId, Long targetId, RelationType type) {
        return relationRepository.findBySourceConceptId(sourceId).stream()
                .filter(r -> r.getTargetConcept().getId().equals(targetId) && r.getRelationType() == type)
                .findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptRelationSource insertSourceOnly(ConceptRelation relation, DocumentChunk chunk, String evidenceText, Double evidenceConfidence) {
        return insertSourceOnly(relation, chunk, evidenceText, evidenceConfidence, KnowledgeScope.fromChunk(chunk));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConceptRelationSource insertSourceOnly(ConceptRelation relation, DocumentChunk chunk, String evidenceText, Double evidenceConfidence, KnowledgeScope scope) {
        relation = relationRepository.findById(relation.getId())
                .orElseThrow(() -> new IllegalArgumentException("Relation not found"));
        KnowledgeScope.validateRelation(relation.getSourceConcept(), relation.getTargetConcept());
        chunk = evidenceValidator.validate(scope, relation.getSourceConcept(), chunk, evidenceText);
        if (!scope.contains(relation.getTargetConcept())) throw new IllegalArgumentException("Target concept outside scope");
        ConceptRelationSource src = new ConceptRelationSource();
        src.setConceptRelation(relation);
        src.setDocumentChunk(chunk);
        src.setEvidenceText(evidenceText);
        src.setConfidence(evidenceConfidence);
        return sourceRepository.saveAndFlush(src);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ConceptRelationSource> findExistingSource(Long relationId, Long chunkId) {
        return sourceRepository.findByConceptRelationIdAndDocumentChunkId(relationId, chunkId);
    }
}
