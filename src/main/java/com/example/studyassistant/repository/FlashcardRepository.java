package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByTopicId(Long topicId);
    List<Flashcard> findByDocumentId(Long documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flashcard f WHERE f.id = :id")
    Optional<Flashcard> findByIdForUpdate(@Param("id") Long id);
}
