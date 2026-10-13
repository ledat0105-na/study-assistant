package com.example.studyassistant.domain.flashcard.queue;

import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.entity.FlashcardReviewState;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class FlashcardStudyQueueRepository {

    private final EntityManager em;

    public FlashcardStudyQueueRepository(EntityManager em) {
        this.em = em;
    }

    private static final String COMMON_SCOPE_JOIN_AND_WHERE = """
            LEFT JOIN f.document d
            LEFT JOIN f.topic t
            LEFT JOIN t.document td
            WHERE f.deletedAt IS NULL
              AND (
                   (f.document IS NOT NULL AND f.topic IS NULL AND d.notebook.id = :notebookId AND d.user.id = :userId)
                OR (f.topic IS NOT NULL AND f.document IS NULL AND td.notebook.id = :notebookId AND td.user.id = :userId)
                OR (f.document IS NOT NULL AND f.topic IS NOT NULL AND d.id = td.id AND d.notebook.id = :notebookId AND d.user.id = :userId)
              )
    """;

    public List<FlashcardReviewState> findDueStates(long userId, long notebookId, Instant now, int limit) {
        String jpql = "SELECT s FROM FlashcardReviewState s JOIN s.flashcard f " + COMMON_SCOPE_JOIN_AND_WHERE + """
              AND s.user.id = :userId
              AND s.dueAt <= :now
            ORDER BY s.dueAt ASC, f.id ASC
        """;
        return em.createQuery(jpql, FlashcardReviewState.class)
                .setParameter("userId", userId)
                .setParameter("notebookId", notebookId)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Flashcard> findNewCardsByConcept(long userId, long notebookId, long conceptId, int limit) {
        String jpql = "SELECT f FROM Flashcard f " + COMMON_SCOPE_JOIN_AND_WHERE + """
              AND f.concept.id = :conceptId
              AND NOT EXISTS (
                 SELECT 1 FROM FlashcardReviewState s
                 WHERE s.flashcard = f AND s.user.id = :userId
              )
            ORDER BY f.id ASC
        """;
        return em.createQuery(jpql, Flashcard.class)
                .setParameter("userId", userId)
                .setParameter("notebookId", notebookId)
                .setParameter("conceptId", conceptId)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Flashcard> findNewCardsFallback(long userId, long notebookId, int limit) {
        String jpql = "SELECT f FROM Flashcard f " + COMMON_SCOPE_JOIN_AND_WHERE + """
              AND NOT EXISTS (
                 SELECT 1 FROM FlashcardReviewState s
                 WHERE s.flashcard = f AND s.user.id = :userId
              )
            ORDER BY f.id ASC
        """;
        return em.createQuery(jpql, Flashcard.class)
                .setParameter("userId", userId)
                .setParameter("notebookId", notebookId)
                .setMaxResults(limit)
                .getResultList();
    }
}
