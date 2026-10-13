package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizQuestionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionSourceRepository extends JpaRepository<QuizQuestionSource, Long> {
    List<QuizQuestionSource> findByQuestionIdOrderBySourceOrderAsc(Long questionId);
}
