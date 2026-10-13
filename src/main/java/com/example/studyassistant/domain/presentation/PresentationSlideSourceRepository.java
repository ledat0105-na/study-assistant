package com.example.studyassistant.domain.presentation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresentationSlideSourceRepository extends JpaRepository<PresentationSlideSource, Long> {
    List<PresentationSlideSource> findBySlideIdOrderBySourceOrderAsc(Long slideId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PresentationSlideSource s WHERE s.slide.id = :slideId")
    void deleteBySlideId(Long slideId);
}
