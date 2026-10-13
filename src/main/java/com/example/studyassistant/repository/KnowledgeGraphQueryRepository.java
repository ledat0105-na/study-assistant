package com.example.studyassistant.repository;

import com.example.studyassistant.entity.ConceptRelation;
import com.example.studyassistant.repository.projection.GraphEdgeProjection;
import com.example.studyassistant.repository.projection.GraphNodeProjection;
import com.example.studyassistant.repository.projection.RelationEvidenceProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.Set;

public interface KnowledgeGraphQueryRepository extends Repository<ConceptRelation, Long> {

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.conceptRelation.sourceConcept.id as sourceConceptId, " +
           "crs.conceptRelation.sourceConcept.canonicalName as sourceCanonicalName, " +
           "crs.conceptRelation.targetConcept.id as targetConceptId, " +
           "crs.conceptRelation.targetConcept.canonicalName as targetCanonicalName, " +
           "crs.conceptRelation.relationType as relationType, " +
           "crs.conceptRelation.confidence as relationConfidence, " +
           "COUNT(DISTINCT crs.id) as activeEvidenceCount " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE (crs.conceptRelation.sourceConcept.id IN :seedIds OR crs.conceptRelation.targetConcept.id IN :seedIds) " +
           "AND crs.conceptRelation.sourceConcept.notebook.id = :notebookId " +
           "AND crs.conceptRelation.targetConcept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY crs.conceptRelation.id, " +
           "crs.conceptRelation.sourceConcept.id, crs.conceptRelation.sourceConcept.canonicalName, " +
           "crs.conceptRelation.targetConcept.id, crs.conceptRelation.targetConcept.canonicalName, " +
           "crs.conceptRelation.relationType, crs.conceptRelation.confidence")
    List<com.example.studyassistant.repository.projection.GraphContextRelationProjection> findGraphContextRelations(
            @Param("notebookId") Long notebookId, 
            @Param("seedIds") Set<Long> seedIds);

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.id as relationSourceId, " +
           "crs.documentChunk.id as chunkId, " +
           "d.id as documentId, " +
           "crs.evidenceText as evidenceText, " +
           "crs.confidence as confidence, " +
           "dc.pageStart as pageStart, " +
           "dc.pageEnd as pageEnd " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE crs.conceptRelation.id IN :relationIds " +
           "AND crs.conceptRelation.sourceConcept.notebook.id = :notebookId " +
           "AND crs.conceptRelation.targetConcept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion")
    List<com.example.studyassistant.repository.projection.GraphContextEvidenceProjection> findGraphContextEvidence(
            @Param("notebookId") Long notebookId, 
            @Param("relationIds") Set<Long> relationIds);

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "dc.id as chunkId, " +
           "cs.confidence as conceptSourceConfidence " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE cs.concept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "AND dc.id IN :chunkIds")
    List<com.example.studyassistant.repository.projection.GraphSeedCandidateProjection> findSeedCandidatesByChunkIds(
            @Param("notebookId") Long notebookId, 
            @Param("chunkIds") Set<Long> chunkIds);

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "cs.concept.description as description, " +
           "COUNT(DISTINCT cs.id) as sourceCount " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE cs.concept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY cs.concept.id, cs.concept.canonicalName, cs.concept.conceptType, cs.concept.description " +
           "ORDER BY COUNT(DISTINCT cs.id) DESC, cs.concept.id ASC")
    List<GraphNodeProjection> findGroundedNodesByNotebookId(@Param("notebookId") Long notebookId);

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.conceptRelation.sourceConcept.id as sourceConceptId, " +
           "crs.conceptRelation.targetConcept.id as targetConceptId, " +
           "crs.conceptRelation.relationType as relationType, " +
           "crs.conceptRelation.confidence as edgeConfidence, " +
           "COUNT(DISTINCT crs.id) as evidenceCount " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE crs.conceptRelation.sourceConcept.notebook.id = :notebookId " +
           "AND crs.conceptRelation.targetConcept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY crs.conceptRelation.id, crs.conceptRelation.sourceConcept.id, " +
           "crs.conceptRelation.targetConcept.id, crs.conceptRelation.relationType, " +
           "crs.conceptRelation.confidence " +
           "ORDER BY COUNT(DISTINCT crs.id) DESC, crs.conceptRelation.id ASC")
    List<GraphEdgeProjection> findGroundedEdgesByNotebookId(@Param("notebookId") Long notebookId);

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.documentChunk.id as chunkId, " +
           "d.id as documentId, " +
           "d.fileName as documentTitle, " +
           "crs.evidenceText as evidenceText, " +
           "crs.confidence as confidence " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE crs.conceptRelation.id = :relationId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "ORDER BY d.id ASC, dc.id ASC")
    List<RelationEvidenceProjection> findRelationEvidence(@Param("notebookId") Long notebookId, @Param("relationId") Long relationId);

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.documentChunk.id as chunkId, " +
           "d.id as documentId, " +
           "d.fileName as documentTitle, " +
           "crs.evidenceText as evidenceText, " +
           "crs.confidence as confidence " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE crs.conceptRelation.id IN :relationIds " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "ORDER BY crs.conceptRelation.id ASC, d.id ASC, dc.id ASC")
    List<RelationEvidenceProjection> findRelationEvidenceInBatches(@Param("notebookId") Long notebookId, @Param("relationIds") List<Long> relationIds);

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "cs.concept.description as description, " +
           "COUNT(DISTINCT cs.id) as sourceCount " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "WHERE cs.concept.id = :conceptId " +
           "AND cs.concept.notebook.id = :notebookId " +
           "AND d.notebook.id = :notebookId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "GROUP BY cs.concept.id, cs.concept.canonicalName, cs.concept.conceptType, cs.concept.description")
    List<GraphNodeProjection> findGroundedNodeByIdAndNotebookId(@Param("conceptId") Long conceptId, @Param("notebookId") Long notebookId);

    @Query("SELECT cs.concept.id as conceptId, " +
           "cs.concept.canonicalName as canonicalName, " +
           "cs.concept.conceptType as conceptType, " +
           "cs.concept.description as description, " +
           "COUNT(DISTINCT cs.id) as sourceCount " +
           "FROM ConceptSource cs " +
           "JOIN cs.documentChunk dc " +
           "JOIN dc.document d " +
           "LEFT JOIN d.notebook dn " +
           "WHERE d.id = :documentId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "AND ((dn.id IS NOT NULL AND cs.concept.notebook.id = dn.id AND cs.concept.document IS NULL) " +
           "OR (dn.id IS NULL AND cs.concept.notebook IS NULL AND cs.concept.document.id = d.id)) " +
           "GROUP BY cs.concept.id, cs.concept.canonicalName, cs.concept.conceptType, cs.concept.description " +
           "ORDER BY COUNT(DISTINCT cs.id) DESC, cs.concept.id ASC")
    List<GraphNodeProjection> findGroundedNodesByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT crs.conceptRelation.id as relationId, " +
           "crs.conceptRelation.sourceConcept.id as sourceConceptId, " +
           "crs.conceptRelation.targetConcept.id as targetConceptId, " +
           "crs.conceptRelation.relationType as relationType, " +
           "crs.conceptRelation.confidence as edgeConfidence, " +
           "COUNT(DISTINCT crs.id) as evidenceCount " +
           "FROM ConceptRelationSource crs " +
           "JOIN crs.documentChunk dc " +
           "JOIN dc.document d " +
           "LEFT JOIN d.notebook dn " +
           "WHERE d.id = :documentId " +
           "AND d.status = 'READY' " +
           "AND dc.indexVersion = d.activeIndexVersion " +
           "AND ((dn.id IS NOT NULL AND crs.conceptRelation.sourceConcept.notebook.id = dn.id " +
           "AND crs.conceptRelation.targetConcept.notebook.id = dn.id) " +
           "OR (dn.id IS NULL AND crs.conceptRelation.sourceConcept.document.id = d.id " +
           "AND crs.conceptRelation.targetConcept.document.id = d.id)) " +
           "GROUP BY crs.conceptRelation.id, crs.conceptRelation.sourceConcept.id, " +
           "crs.conceptRelation.targetConcept.id, crs.conceptRelation.relationType, " +
           "crs.conceptRelation.confidence " +
           "ORDER BY COUNT(DISTINCT crs.id) DESC, crs.conceptRelation.id ASC")
    List<GraphEdgeProjection> findGroundedEdgesByDocumentId(@Param("documentId") Long documentId);
}
