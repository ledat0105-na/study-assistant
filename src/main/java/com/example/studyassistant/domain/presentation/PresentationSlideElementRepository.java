package com.example.studyassistant.domain.presentation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationSlideElementRepository extends JpaRepository<PresentationSlideElement, Long> {
    @Query("""
            select e
            from PresentationSlideElement e
            where e.slideId = :slideId
            order by e.zIndex asc, e.id asc
            """)
    List<PresentationSlideElement> findBySlideIdOrderByZIndexAscIdAsc(@Param("slideId") Long slideId);
    Optional<PresentationSlideElement> findByIdAndDeckIdAndSlideId(Long id, Long deckId, Long slideId);
    boolean existsByMediaAssetId(Long mediaAssetId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PresentationSlideElement e WHERE e.slideId = :slideId")
    void deleteBySlideId(Long slideId);
}
