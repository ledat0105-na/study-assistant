package com.example.studyassistant.repository.adaptive;

import com.example.studyassistant.entity.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdaptiveCandidateRepository extends JpaRepository<Concept, Long> {

    // Query 1: Fetch concepts with their mastery snapshots for actively grounded concepts.
    // DTO mapping will be done in the service layer using the Object[] result to avoid complex JPQL constructor expressions.
    @Query("""
        SELECT 
            c.id AS conceptId, 
            c.canonicalName AS conceptName,
            m.masteryEstimate AS masteryEstimate,
            m.evidenceCount AS evidenceCount,
            m.lastEvidenceAt AS lastEvidenceAt
        FROM Concept c
        LEFT JOIN StudentConceptMastery m ON m.concept.id = c.id AND m.user.id = :userId
        WHERE c.notebook.id = :notebookId
          AND EXISTS (
              SELECT 1 FROM ConceptSource cs 
              JOIN cs.documentChunk dc 
              JOIN dc.document d 
              WHERE cs.concept.id = c.id 
                AND d.notebook.id = :notebookId 
                AND d.status = 'READY' 
                AND d.activeIndexVersion IS NOT NULL 
                AND dc.indexVersion = d.activeIndexVersion
          )
        """)
    List<Object[]> findCandidateFacts(@Param("notebookId") Long notebookId, @Param("userId") Long userId);

    // Query 2: Fetch prerequisite mappings for the grounded concepts in the notebook.
    // Prerequisite direction: sourceConcept (A) is prerequisite of targetConcept (B).
    // Enforces:
    // 1. relation is active
    // 2. targetConcept (B) is active
    // 3. sourceConcept (A) is active
    @Query("""
        SELECT 
            r.targetConcept.id AS dependentConceptId,
            r.sourceConcept.id AS prerequisiteConceptId
        FROM ConceptRelation r
        WHERE r.relationType = 'PREREQUISITE_OF'
          AND r.targetConcept.notebook.id = :notebookId
          AND r.sourceConcept.notebook.id = :notebookId
          AND EXISTS (
              SELECT 1 FROM ConceptRelationSource crs 
              JOIN crs.documentChunk dc 
              JOIN dc.document d 
              WHERE crs.conceptRelation.id = r.id 
                AND d.notebook.id = :notebookId 
                AND d.status = 'READY' 
                AND d.activeIndexVersion IS NOT NULL 
                AND dc.indexVersion = d.activeIndexVersion
          )
          AND EXISTS (
              SELECT 1 FROM ConceptSource csA JOIN csA.documentChunk dcA JOIN dcA.document dA
              WHERE csA.concept.id = r.sourceConcept.id AND dA.notebook.id = :notebookId AND dA.status = 'READY' AND dA.activeIndexVersion IS NOT NULL AND dcA.indexVersion = dA.activeIndexVersion
          )
          AND EXISTS (
              SELECT 1 FROM ConceptSource csB JOIN csB.documentChunk dcB JOIN dcB.document dB
              WHERE csB.concept.id = r.targetConcept.id AND dB.notebook.id = :notebookId AND dB.status = 'READY' AND dB.activeIndexVersion IS NOT NULL AND dcB.indexVersion = dB.activeIndexVersion
          )
        """)
    List<Object[]> findActivePrerequisiteEdges(@Param("notebookId") Long notebookId);

    // Query 3: Fetch all active source locators. We fetch pairs and use deterministic selection in Java
    // to avoid creating synthetic pairs from independent MIN() aggregations.
    @Query("""
        SELECT 
            cs.concept.id AS conceptId,
            d.id AS documentId,
            dc.chunkIndex AS chunkIndex
        FROM ConceptSource cs
        JOIN cs.documentChunk dc
        JOIN dc.document d
        WHERE d.notebook.id = :notebookId
          AND d.status = 'READY'
          AND d.activeIndexVersion IS NOT NULL
          AND dc.indexVersion = d.activeIndexVersion
        """)
    List<Object[]> findActiveSourceOrderFacts(@Param("notebookId") Long notebookId);
}
