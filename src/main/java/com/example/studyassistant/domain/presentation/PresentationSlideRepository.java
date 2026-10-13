package com.example.studyassistant.domain.presentation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresentationSlideRepository extends JpaRepository<PresentationSlide, Long> {
    List<PresentationSlide> findByDeckIdOrderBySlideOrderAsc(Long deckId);
    Optional<PresentationSlide> findByIdAndDeckId(Long id, Long deckId);
    int countByDeckId(Long deckId);

    Optional<PresentationSlide> findByDeckIdAndSlideOrder(Long deckId, Integer slideOrder);

    Optional<PresentationSlide> findByDeckIdAndGenerationKeyAndGeneratedFromOutlineRevisionId(
        Long deckId, String generationKey, Long generatedFromOutlineRevisionId);
}
