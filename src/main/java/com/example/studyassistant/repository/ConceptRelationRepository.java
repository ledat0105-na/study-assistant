package com.example.studyassistant.repository;

import com.example.studyassistant.entity.ConceptRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConceptRelationRepository extends JpaRepository<ConceptRelation, Long> {
    
    // Outgoing
    List<ConceptRelation> findBySourceConceptId(Long sourceConceptId);
    
    // Incoming
    List<ConceptRelation> findByTargetConceptId(Long targetConceptId);
    
    // For a concept (both outgoing and incoming)
    @Query("SELECT cr FROM ConceptRelation cr WHERE cr.sourceConcept.id = :conceptId OR cr.targetConcept.id = :conceptId")
    List<ConceptRelation> findByConceptId(@Param("conceptId") Long conceptId);
}
