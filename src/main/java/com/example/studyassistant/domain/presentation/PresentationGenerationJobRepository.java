package com.example.studyassistant.domain.presentation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationGenerationJobRepository extends JpaRepository<PresentationGenerationJob, Long> {
    Optional<PresentationGenerationJob> findByOwnerIdAndDeckIdAndJobTypeAndIdempotencyKey(
            Long ownerId, Long deckId, PresentationJobType jobType, String idempotencyKey);

    Optional<PresentationGenerationJob> findByIdAndOwnerIdAndDeckId(Long id, Long ownerId, Long deckId);

    List<PresentationGenerationJob> findByOwnerIdAndDeckIdAndJobType(Long ownerId, Long deckId, PresentationJobType jobType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM PresentationGenerationJob j
            WHERE j.owner.id = :ownerId AND j.deckId = :deckId
              AND j.jobType = :jobType AND j.idempotencyKey = :idempotencyKey
            """)
    Optional<PresentationGenerationJob> findIdempotencyIdentityForUpdate(
            @Param("ownerId") Long ownerId,
            @Param("deckId") Long deckId,
            @Param("jobType") PresentationJobType jobType,
            @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM PresentationGenerationJob j WHERE j.id = :id")
    Optional<PresentationGenerationJob> findByIdForUpdate(@Param("id") Long id);

    List<PresentationGenerationJob> findByDeckIdAndJobTypeInAndStatusIn(
            Long deckId, List<PresentationJobType> jobTypes, List<PresentationJobStatus> statuses);

    boolean existsByDeckIdAndJobTypeInAndStatusIn(
            Long deckId, List<PresentationJobType> jobTypes, List<PresentationJobStatus> statuses);

    List<PresentationGenerationJob> findByStatusIn(List<PresentationJobStatus> statuses);
}
