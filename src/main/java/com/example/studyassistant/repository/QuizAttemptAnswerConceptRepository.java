package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizAttemptAnswerConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.studyassistant.repository.projection.RecentConceptEvidenceProjection;
import java.time.LocalDateTime;

import java.util.List;

@Repository
public interface QuizAttemptAnswerConceptRepository extends JpaRepository<QuizAttemptAnswerConcept, Long> {
    List<QuizAttemptAnswerConcept> findByAttemptAnswerId(Long attemptAnswerId);
    List<QuizAttemptAnswerConcept> findByConceptId(Long conceptId);

        @Query("""
                SELECT ac.concept.id AS conceptId,
                             COUNT(DISTINCT answer.id) AS evidenceCount,
                             COUNT(DISTINCT CASE WHEN answer.isCorrect = TRUE THEN answer.id ELSE NULL END) AS correctCount
                FROM QuizAttemptAnswerConcept ac
                JOIN ac.attemptAnswer answer
                JOIN answer.attempt attempt
                JOIN attempt.quiz quiz
                WHERE ac.concept.id IN :conceptIds
                    AND ac.concept.notebook.id = :notebookId
                    AND quiz.notebook.id = :notebookId
                    AND attempt.user.id = :userId
                    AND attempt.status = 'COMPLETED'
                    AND answer.gradedAt >= :windowStart
                    AND answer.gradedAt <= :windowEnd
                GROUP BY ac.concept.id
                """)
        List<RecentConceptEvidenceProjection> aggregateRecentEvidence(
                        @Param("conceptIds") List<Long> conceptIds,
                        @Param("notebookId") Long notebookId,
                        @Param("userId") Long userId,
                        @Param("windowStart") LocalDateTime windowStart,
                        @Param("windowEnd") LocalDateTime windowEnd);
}
