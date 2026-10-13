package com.example.studyassistant.repository;

import com.example.studyassistant.entity.StudentConceptMastery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface StudentConceptMasteryRepository extends JpaRepository<StudentConceptMastery, Long> {

    Optional<StudentConceptMastery> findByUserIdAndConceptId(Long userId, Long conceptId);

    List<StudentConceptMastery> findByUserIdAndConceptIdIn(Long userId, List<Long> conceptIds);

    /**
     * PESSIMISTIC_WRITE locked read on the StudentConceptMastery row.
     *
     * Purpose: close the concurrent cold-start INSERT race under MySQL REPEATABLE_READ.
     *
     * Under REPEATABLE_READ, a normal consistent read (findByUserIdAndConceptId) uses a snapshot
     * established at the start of the transaction. If Transaction A has committed a new mastery
     * row after Transaction B's snapshot was taken, B's consistent read still sees "no row" and
     * attempts a duplicate INSERT → constraint violation.
     *
     * A PESSIMISTIC_WRITE read is a CURRENT READ (SELECT ... FOR UPDATE). It always reads the
     * latest committed data regardless of the transaction's snapshot age. Crucially, it also
     * blocks until any other transaction holding a lock on the same row (or the "gap" for a
     * non-existent row in InnoDB gap locking) releases. This means:
     *
     *   Transaction B waits on the Concept PESSIMISTIC_WRITE lock until A commits.
     *   B then calls this method → current read → sees A's committed mastery row.
     *   B correctly takes the UPDATE branch instead of attempting a second INSERT.
     *
     * The Concept PESSIMISTIC_WRITE lock (in MasteryUpdateEngine) is still required as the
     * global serialization boundary. Without it, two threads could both pass the mastery
     * locking read simultaneously (before either has inserted), defeating the protection.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT m FROM StudentConceptMastery m
        WHERE m.user.id = :userId
          AND m.concept.id = :conceptId
        """)
    Optional<StudentConceptMastery> findByUserIdAndConceptIdWithPessimisticWrite(
            @Param("userId") Long userId,
            @Param("conceptId") Long conceptId);

}

