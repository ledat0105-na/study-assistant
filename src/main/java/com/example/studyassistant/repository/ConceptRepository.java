package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptRepository extends JpaRepository<Concept, Long> {
    
    List<Concept> findByNotebookId(Long notebookId);

        @Query("""
                SELECT c FROM Concept c
                WHERE c.notebook.id = :notebookId
                    AND c.notebook.user.id = :userId
                    AND EXISTS (
                            SELECT cs.id FROM ConceptSource cs
                            JOIN cs.documentChunk dc
                            JOIN dc.document d
                            WHERE cs.concept.id = c.id
                                AND d.notebook.id = :notebookId
                                AND d.status = 'READY'
                                AND d.activeIndexVersion IS NOT NULL
                                AND dc.indexVersion = d.activeIndexVersion
                    )
                ORDER BY c.id ASC
                """)
        List<Concept> findActivelyGroundedByNotebookAndUser(
                        @Param("notebookId") Long notebookId,
                        @Param("userId") Long userId);
    
    Optional<Concept> findByNotebookIdAndNormalizedName(Long notebookId, String normalizedName);
    Optional<Concept> findByDocumentIdAndNormalizedName(Long documentId, String normalizedName);
    boolean existsByDocumentId(Long documentId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Concept c WHERE c.id = :conceptId")
    Optional<Concept> findByIdWithPessimisticWrite(@org.springframework.data.repository.query.Param("conceptId") Long conceptId);
}
