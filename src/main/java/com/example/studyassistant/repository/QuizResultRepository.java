package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {
    List<QuizResult> findByUserId(Long userId);

    @Query("select coalesce(avg(result.score), 0.0) from QuizResult result join result.quiz where result.user.id = :userId")
    double averageScoreByUserId(@Param("userId") Long userId);
    java.util.Optional<QuizResult> findByUserIdAndQuizIdAndSubmissionToken(Long userId, Long quizId, String submissionToken);
    List<QuizResult> findByQuizId(Long quizId);
}
