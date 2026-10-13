package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizQuestionConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionConceptRepository extends JpaRepository<QuizQuestionConcept, Long> {
    List<QuizQuestionConcept> findByQuestionId(Long questionId);
    List<QuizQuestionConcept> findByConceptId(Long conceptId);
}
