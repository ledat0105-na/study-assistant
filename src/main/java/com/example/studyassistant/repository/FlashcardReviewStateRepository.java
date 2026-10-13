package com.example.studyassistant.repository;

import com.example.studyassistant.entity.FlashcardReviewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlashcardReviewStateRepository extends JpaRepository<FlashcardReviewState, Long> {
    Optional<FlashcardReviewState> findByUserIdAndFlashcardId(Long userId, Long flashcardId);
}
