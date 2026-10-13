package com.example.studyassistant.domain.presentation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationOutlineRevisionRepository extends JpaRepository<PresentationOutlineRevision, Long> {
    List<PresentationOutlineRevision> findByDeckIdOrderByRevisionNumberDesc(Long deckId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PresentationOutlineRevision> findFirstByDeckIdOrderByRevisionNumberDesc(Long deckId);

    Optional<PresentationOutlineRevision> findByIdAndDeckId(Long id, Long deckId);
    Optional<PresentationOutlineRevision> findByDeckIdAndRevisionNumber(Long deckId, Integer revisionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PresentationOutlineRevision r WHERE r.id = :revisionId AND r.deckId = :deckId")
    Optional<PresentationOutlineRevision> findByIdAndDeckIdForUpdate(
            @Param("revisionId") Long revisionId, @Param("deckId") Long deckId);

    @Query("SELECT COALESCE(MAX(r.revisionNumber), 0) FROM PresentationOutlineRevision r WHERE r.deckId = :deckId")
    int findMaxRevisionNumber(@Param("deckId") Long deckId);
}
