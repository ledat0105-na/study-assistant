package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizQuestionAcceptedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionAcceptedAnswerRepository extends JpaRepository<QuizQuestionAcceptedAnswer, Long> {
    List<QuizQuestionAcceptedAnswer> findByQuestionIdOrderByAcceptedOrderAsc(Long questionId);
}
