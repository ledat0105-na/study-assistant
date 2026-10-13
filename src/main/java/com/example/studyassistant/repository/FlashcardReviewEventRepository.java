package com.example.studyassistant.repository;

import com.example.studyassistant.entity.FlashcardReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlashcardReviewEventRepository extends JpaRepository<FlashcardReviewEvent, Long> {
    Optional<FlashcardReviewEvent> findByUserIdAndReviewToken(Long userId, String reviewToken);
    List<FlashcardReviewEvent> findByUserIdAndFlashcardIdOrderByReviewedAtAsc(Long userId, Long flashcardId);
}
