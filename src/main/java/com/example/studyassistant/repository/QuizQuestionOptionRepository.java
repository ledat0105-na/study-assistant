package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizQuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionOptionRepository extends JpaRepository<QuizQuestionOption, Long> {
    List<QuizQuestionOption> findByQuestionIdOrderByOptionOrderAsc(Long questionId);
}
