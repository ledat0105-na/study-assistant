package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import com.example.studyassistant.entity.QuizMode;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdAndQuizIdOrderByStartedAtDesc(Long userId, Long quizId);
    Optional<QuizAttempt> findByIdAndUserId(Long id, Long userId);
    List<QuizAttempt> findByQuizId(Long quizId);
    Optional<QuizAttempt> findByUserIdAndSubmissionToken(Long userId, String submissionToken);
    boolean existsByUserIdAndSubmissionTokenAndIdNot(Long userId, String submissionToken, Long id);
        @Query("""
                SELECT a FROM QuizAttempt a
                JOIN a.quiz q
                WHERE a.user.id = :userId
                    AND q.notebook.id = :notebookId
                    AND a.status = :completedStatus
                    AND a.modeSnapshot = :examMode
                    AND a.scoringVersion = :scoringVersion
                    AND a.score IS NOT NULL
                    AND a.score BETWEEN 0 AND 100
                    AND a.completedAt IS NOT NULL
                ORDER BY a.completedAt DESC, a.id DESC
                """)
        List<QuizAttempt> findEligibleReadinessExamAttempts(
                        @Param("userId") Long userId,
                        @Param("notebookId") Long notebookId,
                        @Param("completedStatus") String completedStatus,
                        @Param("examMode") QuizMode examMode,
                        @Param("scoringVersion") String scoringVersion,
                        Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM QuizAttempt a WHERE a.id = :id AND a.user.id = :userId")
    Optional<QuizAttempt> findByIdAndUserIdWithPessimisticWrite(@Param("id") Long id, @Param("userId") Long userId);

}
