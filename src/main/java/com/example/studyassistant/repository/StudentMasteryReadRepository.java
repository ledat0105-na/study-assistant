package com.example.studyassistant.repository;

import com.example.studyassistant.entity.ConceptSource;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryDetailProjection;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import org.springframework.data.domain.Slice;
import java.util.Optional;

public interface StudentMasteryReadRepository extends Repository<ConceptSource, Long> {

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "scm.id as masteryId, " +
           "scm.masteryEstimate as masteryEstimate, " +
           "scm.evidenceCount as evidenceCount, " +
           "scm.lastEvidenceAt as lastEvidenceAt " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "LEFT JOIN StudentConceptMastery scm ON scm.concept.id = cs.concept.id AND scm.user.id = :userId " +
           "WHERE cs.concept.notebook.id = :notebookId " +
           "AND cs.concept.notebook.user.id = :userId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY cs.concept.id, cs.concept.canonicalName, cs.concept.conceptType, scm.id, scm.masteryEstimate, scm.evidenceCount, scm.lastEvidenceAt " +
           "ORDER BY cs.concept.canonicalName ASC, cs.concept.id ASC")
    Slice<GroundedConceptMasteryProjection> findGroundedConceptMasteriesByNotebookId(
            @Param("userId") Long userId,
            @Param("notebookId") Long notebookId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "cs.concept.description as description, " +
           "scm.id as masteryId, " +
           "scm.masteryEstimate as masteryEstimate, " +
           "scm.evidenceCount as evidenceCount, " +
           "scm.lastEvidenceAt as lastEvidenceAt " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "LEFT JOIN StudentConceptMastery scm ON scm.concept.id = cs.concept.id AND scm.user.id = :userId " +
           "WHERE cs.concept.id = :conceptId " +
           "AND cs.concept.notebook.id = :notebookId " +
           "AND cs.concept.notebook.user.id = :userId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY cs.concept.id, cs.concept.canonicalName, cs.concept.conceptType, cs.concept.description, scm.id, scm.masteryEstimate, scm.evidenceCount, scm.lastEvidenceAt")
    Optional<GroundedConceptMasteryDetailProjection> findGroundedConceptMasteryDetail(
            @Param("userId") Long userId,
            @Param("notebookId") Long notebookId,
            @Param("conceptId") Long conceptId);
}
