package com.example.studyassistant.service;

import com.example.studyassistant.dto.QuizSubmissionRequest;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL integration tests for the Quiz → Mastery Trusted Evidence flow.
 * All cleanup is scoped to test-owned IDs only. No global deletes.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class QuizMasteryEvidenceMySQLIntegrationTest {

    // =========================================================
    // Injected services / repos
    // =========================================================

    @Autowired private QuizSubmissionService quizSubmissionService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizResultRepository quizResultRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;
    @Autowired private StudentLearningEventRepository eventRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    // =========================================================
    // Test-owned fixture IDs (tracked for scoped cleanup)
    // =========================================================

    // Shared across tests – populated in @BeforeEach
    private Long ownerId;
    private Long notebookId;
    private Long documentId;
    private Long chunkId;

    private Long conceptAId;
    private Long conceptBId;
    private Long srcAId;     // ConceptSource for A (always active)
    // ConceptSource for B is per-test; tracked below
    private Long srcBId;

    private Long quizId;
    private Long q1Id, q2Id, q3Id, q4Id, q5Id;

    // M10 fixtures
    private Long conceptM10Id;
    private Long srcM10Id;
    private Long quizM10Id;
    private Long q10_1Id, q10_2Id, q10_3Id;

    // Additional per-test tracked IDs for cleanup
    private final List<Long> testQuizResultIds   = new ArrayList<>();
    private final List<Long> testQuizAttemptIds  = new ArrayList<>();
    private final List<Long> testEventIds        = new ArrayList<>();
    private final List<Long> testMasteryIds      = new ArrayList<>();
    private final List<Long> extraConceptSrcIds  = new ArrayList<>(); // e.g. M8 partial cleanup

    // =========================================================
    // @BeforeEach — create unique fixtures, no global wipe
    // =========================================================

    @BeforeEach
    void setUp() {
        String runId = UUID.randomUUID().toString();

        User owner = new User();
        owner.setEmail("owner-" + runId + "@test.com");
        owner.setUsername("user-" + runId);
        owner.setPassword("hash");
        owner.setRole("USER");
        owner = userRepository.saveAndFlush(owner);
        ownerId = owner.getId();

        Notebook notebook = new Notebook();
        notebook.setUser(owner);
        notebook.setTitle("Notebook " + runId);
        notebook = notebookRepository.saveAndFlush(notebook);
        notebookId = notebook.getId();

        Document document = new Document();
        document.setUser(owner);
        document.setNotebook(notebook);
        document.setFileName("Doc.pdf");
        document.setFilePath("/path/" + runId);
        document.setStatus("READY");
        document.setActiveIndexVersion("v1-" + runId);
        document = documentRepository.saveAndFlush(document);
        documentId = document.getId();

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(1);
        chunk.setContent("content-" + runId);
        chunk.setTokenCount(10);
        chunk.setIndexVersion("v1-" + runId);
        chunk = documentChunkRepository.saveAndFlush(chunk);
        chunkId = chunk.getId();

        // Concept A — always actively grounded
        Concept conceptA = new Concept();
        conceptA.setNotebook(notebook);
        conceptA.setCanonicalName("ConceptA-" + runId);
        conceptA.setNormalizedName("concept_a_" + runId);
        conceptA = conceptRepository.saveAndFlush(conceptA);
        conceptAId = conceptA.getId();

        ConceptSource srcA = new ConceptSource();
        srcA.setConcept(conceptA);
        srcA.setDocumentChunk(chunk);
        srcA.setEvidenceText("A");
        srcA = conceptSourceRepository.saveAndFlush(srcA);
        srcAId = srcA.getId();

        // Concept B — actively grounded by default (M8 overrides per-test)
        Concept conceptB = new Concept();
        conceptB.setNotebook(notebook);
        conceptB.setCanonicalName("ConceptB-" + runId);
        conceptB.setNormalizedName("concept_b_" + runId);
        conceptB = conceptRepository.saveAndFlush(conceptB);
        conceptBId = conceptB.getId();

        ConceptSource srcB = new ConceptSource();
        srcB.setConcept(conceptB);
        srcB.setDocumentChunk(chunk);
        srcB.setEvidenceText("B");
        srcB = conceptSourceRepository.saveAndFlush(srcB);
        srcBId = srcB.getId();

        // Quiz with 5 questions
        Quiz quiz = new Quiz();
        quiz.setTitle("Test Quiz " + runId);
        quiz.setDocument(document);
        quiz = quizRepository.saveAndFlush(quiz);
        quizId = quiz.getId();

        QuizQuestion q1 = buildQuestion(quiz, conceptA, "Q1", "A");
        q1Id = quizQuestionRepository.saveAndFlush(q1).getId();

        QuizQuestion q2 = buildQuestion(quiz, conceptA, "Q2", "A");
        q2Id = quizQuestionRepository.saveAndFlush(q2).getId();

        QuizQuestion q3 = buildQuestion(quiz, conceptB, "Q3", "A");
        q3Id = quizQuestionRepository.saveAndFlush(q3).getId();

        QuizQuestion q4 = buildQuestion(quiz, null, "Q4", "A");
        q4Id = quizQuestionRepository.saveAndFlush(q4).getId();

        QuizQuestion q5 = buildQuestion(quiz, conceptB, "Q5", "A");
        q5Id = quizQuestionRepository.saveAndFlush(q5).getId();

        // Concept M10 — single concept, three questions
        Concept conceptM10 = new Concept();
        conceptM10.setNotebook(notebook);
        conceptM10.setCanonicalName("ConceptM10-" + runId);
        conceptM10.setNormalizedName("concept_m10_" + runId);
        conceptM10 = conceptRepository.saveAndFlush(conceptM10);
        conceptM10Id = conceptM10.getId();

        ConceptSource srcM10 = new ConceptSource();
        srcM10.setConcept(conceptM10);
        srcM10.setDocumentChunk(chunk);
        srcM10.setEvidenceText("M10");
        srcM10 = conceptSourceRepository.saveAndFlush(srcM10);
        srcM10Id = srcM10.getId();

        Quiz quizM10 = new Quiz();
        quizM10.setTitle("M10 Quiz " + runId);
        quizM10.setDocument(document);
        quizM10 = quizRepository.saveAndFlush(quizM10);
        quizM10Id = quizM10.getId();

        q10_1Id = quizQuestionRepository.saveAndFlush(buildQuestion(quizM10, conceptM10, "Q10_1", "A")).getId();
        q10_2Id = quizQuestionRepository.saveAndFlush(buildQuestion(quizM10, conceptM10, "Q10_2", "A")).getId();
        q10_3Id = quizQuestionRepository.saveAndFlush(buildQuestion(quizM10, conceptM10, "Q10_3", "A")).getId();
    }

    // =========================================================
    // @AfterEach — scoped cleanup only, child → parent
    // =========================================================

    @AfterEach
    void tearDown() {
        // Delete test-owned events
        if (!testEventIds.isEmpty()) {
            transactionTemplate.execute(status -> {
                em.createQuery("DELETE FROM StudentLearningEvent e WHERE e.id IN :ids")
                  .setParameter("ids", testEventIds)
                  .executeUpdate();
                return null;
            });
        }
        // Delete ALL events for test user/concepts (covers concurrent workers)
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM StudentLearningEvent e WHERE e.user.id = :uid " +
                "AND e.concept.id IN :cids")
              .setParameter("uid", ownerId)
              .setParameter("cids", List.of(conceptAId, conceptBId, conceptM10Id))
              .executeUpdate();
            return null;
        });

        // Delete mastery rows for test user
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM StudentConceptMastery m WHERE m.user.id = :uid " +
                "AND m.concept.id IN :cids")
              .setParameter("uid", ownerId)
              .setParameter("cids", List.of(conceptAId, conceptBId, conceptM10Id))
              .executeUpdate();
            return null;
        });

        // Delete QuizResults for test quizzes
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM QuizResult r WHERE r.quiz.id IN :qids")
              .setParameter("qids", List.of(quizId, quizM10Id))
              .executeUpdate();
            return null;
        });

        // Delete QuizAttempts for test quizzes
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM QuizAttempt a WHERE a.quiz.id IN :qids")
              .setParameter("qids", List.of(quizId, quizM10Id))
              .executeUpdate();
            return null;
        });

        // Extra concept sources created by M8 (if any)
        for (Long id : extraConceptSrcIds) {
            transactionTemplate.execute(status -> {
                em.createQuery("DELETE FROM ConceptSource cs WHERE cs.id = :id")
                  .setParameter("id", id)
                  .executeUpdate();
                return null;
            });
        }
        extraConceptSrcIds.clear();

        // Quiz questions for test quizzes
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM QuizQuestion q WHERE q.quiz.id IN :qids")
              .setParameter("qids", List.of(quizId, quizM10Id))
              .executeUpdate();
            return null;
        });

        // Quizzes
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM Quiz q WHERE q.id IN :qids")
              .setParameter("qids", List.of(quizId, quizM10Id))
              .executeUpdate();
            return null;
        });

        // ConceptSources for test concepts
        transactionTemplate.execute(status -> {
            em.createQuery(
                "DELETE FROM ConceptSource cs WHERE cs.concept.id IN :cids")
              .setParameter("cids", List.of(conceptAId, conceptBId, conceptM10Id))
              .executeUpdate();
            return null;
        });

        // Concepts
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM Concept c WHERE c.id IN :cids")
              .setParameter("cids", List.of(conceptAId, conceptBId, conceptM10Id))
              .executeUpdate();
            return null;
        });

        // DocumentChunk
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM DocumentChunk dc WHERE dc.id = :id")
              .setParameter("id", chunkId)
              .executeUpdate();
            return null;
        });

        // Document
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM Document d WHERE d.id = :id")
              .setParameter("id", documentId)
              .executeUpdate();
            return null;
        });

        // Notebook
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM Notebook n WHERE n.id = :id")
              .setParameter("id", notebookId)
              .executeUpdate();
            return null;
        });

        // User
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM User u WHERE u.id = :id")
              .setParameter("id", ownerId)
              .executeUpdate();
            return null;
        });

        testEventIds.clear();
        testQuizResultIds.clear();
        testQuizAttemptIds.clear();
        testMasteryIds.clear();
    }

    // =========================================================
    // M5 — SUCCESSFUL FULL INTEGRATION
    // =========================================================

    @Test
    void testM5_SuccessfulFullIntegration() {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest request = new QuizSubmissionRequest();
        request.setSubmissionToken(token);

        Map<Long, String> answers = new HashMap<>();
        answers.put(q1Id, "A"); // correct  → ConceptA evidence 1.0
        answers.put(q2Id, "B"); // incorrect → ConceptA evidence 0.0
        answers.put(q3Id, "A"); // correct  → ConceptB evidence 1.0
        answers.put(q4Id, "A"); // null concept → no evidence
        // q5 deliberately missing → no evidence (EXPLICIT-ANSWER-ONLY policy)
        request.setAnswers(answers);

        QuizResult result = quizSubmissionService.submitQuiz(ownerId, quizId, request);

        assertNotNull(result.getId());
        assertEquals(token, result.getSubmissionToken());

        // 1 QuizResult, 1 COMPLETED QuizAttempt
        assertEquals(1, quizResultRepository.findByQuizId(quizId).size());
        assertEquals(1, quizAttemptRepository.findByQuizId(quizId).size());

        // 3 StudentLearningEvents (Q1, Q2, Q3 only)
        List<StudentLearningEvent> events = eventRepository
            .findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId);
        events.addAll(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptBId));
        assertEquals(3, events.size(), "Only Q1, Q2, Q3 should produce evidence");

        events.forEach(e -> {
            assertEquals("QUIZ_QUESTION_ANSWERED", e.getEventType());
            assertEquals("QUIZ_QUESTION", e.getSourceType());
            assertNotNull(e.getOccurredAt());
            assertTrue(e.getIdempotencyKey().startsWith("QUIZ:" + quizId + ":SUB:" + token + ":Q:"));
        });

        // Concept A: evidence_count=2, EMA = 1.0 then 1.0*0.7+0.0*0.3 = 0.70
        StudentConceptMastery masteryA = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptAId).orElseThrow();
        assertEquals(2, masteryA.getEvidenceCount());
        assertEquals(0.70, masteryA.getMasteryEstimate(), 0.001);

        // Concept B: evidence_count=1, EMA = 1.0
        StudentConceptMastery masteryB = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptBId).orElseThrow();
        assertEquals(1, masteryB.getEvidenceCount());
        assertEquals(1.0, masteryB.getMasteryEstimate(), 0.001);
    }

    // =========================================================
    // M6 — IDEMPOTENT REPLAY
    // =========================================================

    @Test
    void testM6_IdempotentReplay() {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest request = new QuizSubmissionRequest();
        request.setSubmissionToken(token);
        request.setAnswers(Map.of(q1Id, "A")); // ConceptA correct

        QuizResult r1 = quizSubmissionService.submitQuiz(ownerId, quizId, request);
        assertNotNull(r1.getId());

        // Capture baseline counts after first submission
        long eventsBefore = eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId).size();
        long masteryCountBefore = masteryRepository.findByUserIdAndConceptId(ownerId, conceptAId)
            .map(m -> (long) m.getEvidenceCount()).orElse(0L);
        int attemptsBefore = quizAttemptRepository.findByQuizId(quizId).size();

        // Replay identical submission
        QuizResult r2 = quizSubmissionService.submitQuiz(ownerId, quizId, request);

        assertEquals(r1.getId(), r2.getId(), "Idempotent replay must return same QuizResult ID");
        assertEquals(eventsBefore,
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId).size(),
            "No new StudentLearningEvents on replay");
        assertEquals(masteryCountBefore,
            masteryRepository.findByUserIdAndConceptId(ownerId, conceptAId)
                .map(m -> (long) m.getEvidenceCount()).orElse(0L),
            "No mastery mutation on replay");
        assertEquals(attemptsBefore, quizAttemptRepository.findByQuizId(quizId).size(),
            "No new QuizAttempt on replay");
    }

    // =========================================================
    // M7 — CONCURRENT SAME TOKEN, SAME PAYLOAD
    // =========================================================

    @Test
    void testM7_ConcurrentSameTokenSamePayload() throws Exception {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest request = new QuizSubmissionRequest();
        request.setSubmissionToken(token);
        request.setAnswers(Map.of(q1Id, "A")); // ConceptA correct

        AtomicReference<QuizResult> result1 = new AtomicReference<>();
        AtomicReference<QuizResult> result2 = new AtomicReference<>();

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            QuizResult r = quizSubmissionService.submitQuiz(ownerId, quizId, request);
            result1.set(r);
        });

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            QuizResult r = quizSubmissionService.submitQuiz(ownerId, quizId, request);
            result2.set(r);
        });

        // BOUNDED wait — fail test if concurrency hangs
        try {
            CompletableFuture.allOf(t1, t2).get(20, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("M7 concurrent submissions timed out after 20s");
        } catch (ExecutionException e) {
            fail("M7 concurrent submission threw unexpected exception: " + e.getCause());
        }

        // Both calls must resolve to the same logical QuizResult
        assertNotNull(result1.get(), "Thread 1 must return a QuizResult");
        assertNotNull(result2.get(), "Thread 2 must return a QuizResult");
        assertEquals(result1.get().getId(), result2.get().getId(),
            "Both concurrent submissions must resolve to the same QuizResult");

        // Exactly one persisted set
        assertEquals(1, quizResultRepository.findByQuizId(quizId).size());
        assertEquals(1, quizAttemptRepository.findByQuizId(quizId).size());
        assertEquals(1,
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId).size());

        StudentConceptMastery mastery = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptAId).orElseThrow();
        assertEquals(1, mastery.getEvidenceCount());
    }

    // =========================================================
    // M8 — LATE MASTERY FAILURE → FULL TRANSACTION ROLLBACK
    // =========================================================
    // ConceptB is created WITHOUT any active ConceptSource from the start.
    // ConceptA (ID < ConceptB ID guaranteed by sequential insert) succeeds.
    // ConceptB fails MasteryUpdateEngine's grounding validation.
    // The unchecked exception propagates through TransactionTemplate → ROLLBACK.
    //
    // Note: Concept A has active grounding via srcAId created in @BeforeEach.
    // For M8 we delete ONLY srcBId (the shared B grounding), scoped by its exact ID.

    @Test
    void testM8_LateMasteryFailureFullRollback() {
        // Remove B's grounding by deleting exactly srcBId — scoped, not global
        transactionTemplate.execute(status -> {
            em.createQuery("DELETE FROM ConceptSource cs WHERE cs.id = :id")
              .setParameter("id", srcBId)
              .executeUpdate();
            return null;
        });
        // After tearDown, the conceptB ConceptSource cleanup will find nothing — safe.

        // Ensure ID order: conceptAId < conceptBId (adapter sorts conceptId ASC)
        // Both were inserted sequentially in setUp; DB auto-increment guarantees this.
        assertTrue(conceptAId < conceptBId,
            "Fixture invariant: Concept A must have lower ID than Concept B for deterministic adapter ordering");

        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest request = new QuizSubmissionRequest();
        request.setSubmissionToken(token);

        Map<Long, String> answers = new HashMap<>();
        answers.put(q1Id, "A"); // ConceptA — will succeed in mastery
        answers.put(q3Id, "A"); // ConceptB — will fail (no grounding)
        request.setAnswers(answers);

        // Expect failure (unchecked from MasteryUpdateEngine)
        assertThrows(Exception.class,
            () -> quizSubmissionService.submitQuiz(ownerId, quizId, request),
            "Submission must throw when mastery update fails");

        // Full rollback verification
        assertEquals(0, quizResultRepository.findByQuizId(quizId).size(),
            "QuizResult must be rolled back");
        assertEquals(0, quizAttemptRepository.findByQuizId(quizId).size(),
            "QuizAttempt must be rolled back");
        assertEquals(0,
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId).size(),
            "ConceptA StudentLearningEvent must be rolled back");
        assertTrue(masteryRepository.findByUserIdAndConceptId(ownerId, conceptAId).isEmpty(),
            "ConceptA mastery must be rolled back");
        assertTrue(masteryRepository.findByUserIdAndConceptId(ownerId, conceptBId).isEmpty(),
            "ConceptB mastery must remain absent");
    }

    // =========================================================
    // M9 — OVERLAPPING CONCEPT CONCURRENCY
    // =========================================================

    @Test
    void testM9_OverlappingConceptConcurrency() throws Exception {
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();

        // S1 answers ConceptA + ConceptB questions
        QuizSubmissionRequest req1 = new QuizSubmissionRequest();
        req1.setSubmissionToken(token1);
        req1.setAnswers(Map.of(q1Id, "A", q3Id, "B")); // A correct, B incorrect

        // S2 answers the same questions with opposite order in the map
        // Adapter will normalize to conceptId ASC regardless
        QuizSubmissionRequest req2 = new QuizSubmissionRequest();
        req2.setSubmissionToken(token2);
        req2.setAnswers(Map.of(q3Id, "A", q1Id, "B")); // B correct, A incorrect

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(
            () -> quizSubmissionService.submitQuiz(ownerId, quizId, req1));
        CompletableFuture<Void> t2 = CompletableFuture.runAsync(
            () -> quizSubmissionService.submitQuiz(ownerId, quizId, req2));

        // BOUNDED wait
        try {
            CompletableFuture.allOf(t1, t2).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("M9 overlapping-concept concurrent submissions timed out after 30s");
        } catch (ExecutionException e) {
            fail("M9 concurrent submission threw unexpected exception: " + e.getCause());
        }

        // Both submissions must have committed
        assertEquals(2, quizResultRepository.findByQuizId(quizId).size(),
            "Both independent submissions must commit");
        assertEquals(2, quizAttemptRepository.findByQuizId(quizId).size());

        // Each submission produces 2 events (ConceptA + ConceptB) = 4 total
        List<StudentLearningEvent> eventsA =
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptAId);
        List<StudentLearningEvent> eventsB =
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptBId);
        assertEquals(2, eventsA.size(), "ConceptA must have 2 evidence events");
        assertEquals(2, eventsB.size(), "ConceptB must have 2 evidence events");

        // Mastery evidence counts reflect both submissions
        StudentConceptMastery masteryA = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptAId).orElseThrow();
        assertEquals(2, masteryA.getEvidenceCount());

        StudentConceptMastery masteryB = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptBId).orElseThrow();
        assertEquals(2, masteryB.getEvidenceCount());

        // NOTE: This test verifies that the known Concept lock-order inversion scenario
        // completes successfully under deterministic conceptId ordering.
        // It does NOT claim to prove all MySQL/InnoDB deadlocks impossible.
    }

    // =========================================================
    // M10 — SAME CONCEPT, DETERMINISTIC EMA ORDER
    // =========================================================

    @Test
    void testM10_SameConceptDeterministicEmaOrder() {
        // Verify DB-generated IDs have the expected ascending order
        assertTrue(q10_1Id < q10_2Id && q10_2Id < q10_3Id,
            "Fixture invariant: question IDs must be strictly ascending for EMA order");

        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest request = new QuizSubmissionRequest();
        request.setSubmissionToken(token);

        Map<Long, String> answers = new HashMap<>();
        answers.put(q10_1Id, "A"); // lowest questionId  → correct  → 1.0
        answers.put(q10_2Id, "B"); // middle questionId  → incorrect → 0.0
        answers.put(q10_3Id, "A"); // highest questionId → correct  → 1.0
        request.setAnswers(answers);

        quizSubmissionService.submitQuiz(ownerId, quizM10Id, request);

        StudentConceptMastery mastery = masteryRepository
            .findByUserIdAndConceptId(ownerId, conceptM10Id).orElseThrow();
        assertEquals(3, mastery.getEvidenceCount());

        // EMA calculation (conceptId ASC, then questionId ASC):
        // Step 1: 1.0              (cold start = score)
        // Step 2: 1.0*0.70 + 0.0*0.30 = 0.70
        // Step 3: 0.70*0.70 + 1.0*0.30 = 0.79
        assertEquals(0.79, mastery.getMasteryEstimate(), 0.001);

        // Verify three events with correct source IDs
        List<StudentLearningEvent> events =
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(ownerId, conceptM10Id);
        assertEquals(3, events.size());

        // Sort by sourceId (questionId string) for stable assertion
        events.sort((a, b) -> Long.compare(Long.parseLong(a.getSourceId()), Long.parseLong(b.getSourceId())));

        assertEquals(String.valueOf(q10_1Id), events.get(0).getSourceId());
        assertEquals(String.valueOf(q10_2Id), events.get(1).getSourceId());
        assertEquals(String.valueOf(q10_3Id), events.get(2).getSourceId());

        // Verify event metadata for all three
        events.forEach(e -> {
            assertEquals("QUIZ_QUESTION_ANSWERED", e.getEventType());
            assertEquals("QUIZ_QUESTION", e.getSourceType());
            assertNotNull(e.getOccurredAt());
            assertTrue(e.getIdempotencyKey().startsWith(
                "QUIZ:" + quizM10Id + ":SUB:" + token + ":Q:"));
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private QuizQuestion buildQuestion(Quiz quiz, Concept concept, String text, String correct) {
        QuizQuestion q = new QuizQuestion();
        q.setQuiz(quiz);
        q.setConcept(concept);
        q.setQuestion(text);
        q.setAnswerA("A");
        q.setAnswerB("B");
        q.setAnswerC("C");
        q.setAnswerD("D");
        q.setCorrectAnswer(correct);
        return q;
    }
}
