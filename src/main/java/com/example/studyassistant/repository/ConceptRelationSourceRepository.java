package com.example.studyassistant.repository;

import com.example.studyassistant.entity.ConceptRelationSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptRelationSourceRepository extends JpaRepository<ConceptRelationSource, Long> {
    
    List<ConceptRelationSource> findByConceptRelationId(Long conceptRelationId);
    
    List<ConceptRelationSource> findByDocumentChunkId(Long documentChunkId);
    
    Optional<ConceptRelationSource> findByConceptRelationIdAndDocumentChunkId(Long conceptRelationId, Long documentChunkId);
    
    boolean existsByConceptRelationIdAndDocumentChunkId(Long conceptRelationId, Long documentChunkId);
}
