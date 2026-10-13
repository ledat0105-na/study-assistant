package com.example.studyassistant.repository;

import com.example.studyassistant.entity.StudentLearningEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentLearningEventRepository extends JpaRepository<StudentLearningEvent, Long> {
    
    Optional<StudentLearningEvent> findByUserIdAndIdempotencyKeyAndConceptId(Long userId, String idempotencyKey, Long conceptId);

    List<StudentLearningEvent> findByUserIdAndConceptIdOrderByOccurredAtAsc(Long userId, Long conceptId);
}
