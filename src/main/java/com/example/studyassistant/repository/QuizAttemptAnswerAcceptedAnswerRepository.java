package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizAttemptAnswerAcceptedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptAnswerAcceptedAnswerRepository extends JpaRepository<QuizAttemptAnswerAcceptedAnswer, Long> {
    List<QuizAttemptAnswerAcceptedAnswer> findByAttemptAnswerIdOrderByAcceptedOrderAsc(Long attemptAnswerId);
}
