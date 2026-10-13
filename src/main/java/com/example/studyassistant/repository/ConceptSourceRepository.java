package com.example.studyassistant.repository;

import com.example.studyassistant.entity.ConceptSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptSourceRepository extends JpaRepository<ConceptSource, Long> {
    
    List<ConceptSource> findByConceptId(Long conceptId);

    long countByDocumentChunkId(Long documentChunkId);
    
    @EntityGraph(attributePaths = {"concept"})
    List<ConceptSource> findByDocumentChunkId(Long documentChunkId);
    
    Optional<ConceptSource> findByConceptIdAndDocumentChunkId(Long conceptId, Long documentChunkId);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
           "FROM ConceptSource cs " +
           "JOIN cs.concept c " +
           "JOIN c.notebook cn " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE c.id = :conceptId " +
           "AND cn.id = :notebookId " +
           "AND cn.user.id = :userId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND d.activeIndexVersion IS NOT NULL " +
           "AND dc.indexVersion = d.activeIndexVersion")
    boolean existsActiveGrounding(@org.springframework.data.repository.query.Param("conceptId") Long conceptId, 
                                  @org.springframework.data.repository.query.Param("notebookId") Long notebookId, 
                                  @org.springframework.data.repository.query.Param("userId") Long userId);
}
