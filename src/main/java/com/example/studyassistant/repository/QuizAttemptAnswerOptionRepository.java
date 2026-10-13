package com.example.studyassistant.repository;

import com.example.studyassistant.entity.QuizAttemptAnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptAnswerOptionRepository extends JpaRepository<QuizAttemptAnswerOption, Long> {
    List<QuizAttemptAnswerOption> findByAttemptAnswerIdOrderByOptionOrderAsc(Long attemptAnswerId);
}
