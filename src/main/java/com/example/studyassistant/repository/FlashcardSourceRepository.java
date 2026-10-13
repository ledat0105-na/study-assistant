package com.example.studyassistant.repository;

import com.example.studyassistant.entity.FlashcardSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardSourceRepository extends JpaRepository<FlashcardSource, Long> {
    List<FlashcardSource> findByFlashcardIdOrderBySourceOrderAsc(Long flashcardId);
}
