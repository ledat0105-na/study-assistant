package com.example.studyassistant.domain.presentation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PresentationDeckRepository extends JpaRepository<PresentationDeck, Long> {
    @Query("""
            SELECT d FROM PresentationDeck d
            WHERE d.owner.id = :ownerId AND d.status <> :archived
            ORDER BY d.updatedAt DESC, d.id DESC
            """)
    Page<PresentationDeck> findWorkspaceDecks(@Param("ownerId") Long ownerId,
                                               @Param("archived") PresentationDeckStatus archived,
                                               Pageable pageable);

    Optional<PresentationDeck> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<PresentationDeck> findByIdAndOwnerIdAndStatusNot(
            Long id, Long ownerId, PresentationDeckStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM PresentationDeck d WHERE d.id = :deckId AND d.owner.id = :ownerId")
    Optional<PresentationDeck> findOwnedByIdForUpdate(@Param("deckId") Long deckId,
                                                        @Param("ownerId") Long ownerId);

    long countByOwnerIdAndStatusNot(Long ownerId, PresentationDeckStatus status);
}
