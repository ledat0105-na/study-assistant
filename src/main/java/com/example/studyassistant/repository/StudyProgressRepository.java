package com.example.studyassistant.repository;

import com.example.studyassistant.entity.StudyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudyProgressRepository extends JpaRepository<StudyProgress, Long> {
    @Query("select progress from StudyProgress progress join fetch progress.topic topic join fetch topic.document doc where progress.user.id = :userId and doc.status <> 'DELETED'")
    List<StudyProgress> findByUserId(@Param("userId") Long userId);

    @Query("""
            select count(progress)
            from StudyProgress progress
            join progress.topic topic
            join topic.document doc
            where progress.user.id = :userId and progress.status = :status and doc.status <> 'DELETED'
            """)
    long countValidByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
    Optional<StudyProgress> findByUserIdAndTopicId(Long userId, Long topicId);
}
