package com.example.studyassistant.domain.flashcard.queue;

import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * MySQL integration tests for FlashcardStudyQueueService.
 *
 * CLEANUP POLICY (SF1.5.3-hardened):
 *   - Fixture-owned only: only exact IDs persisted by this test are deleted.
 *   - No deleteAll / deleteAllInBatch / TRUNCATE.
 *   - IDs tracked immediately after each saveAndFlush.
 *   - Cleanup uses native SQL DELETE WHERE id IN (:ids), flushed after each layer.
 *   - No silent exception swallowing: if a tracked owned row cannot be deleted, cleanup fails visibly.
 *   - After cleanup, all owned IDs are verified absent (existsById = false).
 *   - Unrelated sentinel rows are never touched.
 *
 * FK deletion order (child before parent):
 *   flashcard_review_events
 *   flashcard_review_states
 *   flashcards
 *   topics
 *   documents
 *   concepts
 *   notebooks
 *   users
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
class FlashcardStudyQueueMySQLIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public AdaptiveRecommendationService adaptiveRecommendationServiceMock() {
            return mock(AdaptiveRecommendationService.class);
        }

        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired private FlashcardStudyQueueService service;
    @Autowired private UserRepository userRepo;
    @Autowired private NotebookRepository notebookRepo;
    @Autowired private DocumentRepository docRepo;
    @Autowired private TopicRepository topicRepo;
    @Autowired private FlashcardRepository flashcardRepo;
    @Autowired private FlashcardReviewStateRepository stateRepo;
    @Autowired private FlashcardReviewEventRepository eventRepo;
    @Autowired private ConceptRepository conceptRepo;
    @Autowired private AdaptiveRecommendationService adaptiveServiceMock;
    @Autowired private Clock clock;
    @Autowired private TransactionTemplate txTemplate;
    @PersistenceContext private EntityManager em;

    // ── Fixture-owned ID trackers ─────────────────────────────────────────────
    private List<Long> ownedEventIds;
    private List<Long> ownedStateIds;
    private List<Long> ownedFlashcardIds;
    private List<Long> ownedTopicIds;
    private List<Long> ownedDocumentIds;
    private List<Long> ownedConceptIds;
    private List<Long> ownedNotebookIds;
    private List<Long> ownedUserIds;

    private User u;
    private Notebook nb;

    @BeforeEach
    void initTrackers() {
        ownedEventIds     = new ArrayList<>();
        ownedStateIds     = new ArrayList<>();
        ownedFlashcardIds = new ArrayList<>();
        ownedTopicIds     = new ArrayList<>();
        ownedDocumentIds  = new ArrayList<>();
        ownedConceptIds   = new ArrayList<>();
        ownedNotebookIds  = new ArrayList<>();
        ownedUserIds      = new ArrayList<>();
        u  = null;
        nb = null;
    }

    /**
     * Deletes all fixture-owned rows in FK-safe child-before-parent order.
     *
     * - Uses native SQL DELETE WHERE id IN (:ids) for atomicity per layer.
     * - Flushes after each layer so FK violations surface immediately.
     * - No exception swallowing: any failure propagates and fails the test.
     * - Empty ID lists are skipped.
     *
     * Verifies all owned IDs absent after deletion.
     * Does NOT touch any rows outside the tracked ID sets.
     */
    private void deleteFixtureOwned() {
        txTemplate.executeWithoutResult(tx -> {
            deleteIds("flashcard_review_events", ownedEventIds);
            em.flush();
            deleteIds("flashcard_review_states", ownedStateIds);
            em.flush();
            deleteIds("flashcards", ownedFlashcardIds);
            em.flush();
            deleteIds("topics", ownedTopicIds);
            em.flush();
            deleteIds("documents", ownedDocumentIds);
            em.flush();
            deleteIds("concepts", ownedConceptIds);
            em.flush();
            deleteIds("notebooks", ownedNotebookIds);
            em.flush();
            deleteIds("users", ownedUserIds);
            em.flush();
        });

        // Verify owned IDs are absent. Uses exact-ID existence check — does not touch unrelated rows.
        for (Long id : ownedEventIds)     assertThat(eventRepo.existsById(id)).as("event %d must be gone", id).isFalse();
        for (Long id : ownedStateIds)     assertThat(stateRepo.existsById(id)).as("state %d must be gone", id).isFalse();
        for (Long id : ownedFlashcardIds) assertThat(flashcardRepo.existsById(id)).as("flashcard %d must be gone", id).isFalse();
        for (Long id : ownedTopicIds)     assertThat(topicRepo.existsById(id)).as("topic %d must be gone", id).isFalse();
        for (Long id : ownedDocumentIds)  assertThat(docRepo.existsById(id)).as("document %d must be gone", id).isFalse();
        for (Long id : ownedConceptIds)   assertThat(conceptRepo.existsById(id)).as("concept %d must be gone", id).isFalse();
        for (Long id : ownedNotebookIds)  assertThat(notebookRepo.existsById(id)).as("notebook %d must be gone", id).isFalse();
        for (Long id : ownedUserIds)      assertThat(userRepo.existsById(id)).as("user %d must be gone", id).isFalse();
    }

    /** Issues a native DELETE WHERE id IN (:ids) for the given table. No-op when ids is empty. */
    private void deleteIds(String table, List<Long> ids) {
        if (ids.isEmpty()) return;
        em.createNativeQuery("DELETE FROM " + table + " WHERE id IN (:ids)")
          .setParameter("ids", ids)
          .executeUpdate();
    }

    @AfterEach
    void cleanup() {
        reset(adaptiveServiceMock);
        deleteFixtureOwned();
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private User createUser() {
        User u = new User();
        u.setUsername("qtest_" + UUID.randomUUID());
        u.setEmail("qtest_" + UUID.randomUUID() + "@example.com");
        u.setPassword("hash");
        u.setRole("USER");
        User saved = userRepo.saveAndFlush(u);
        ownedUserIds.add(saved.getId());
        return saved;
    }

    private Notebook createNotebook(User owner) {
        Notebook n = new Notebook();
        n.setTitle("QTest Notebook " + UUID.randomUUID());
        n.setUser(owner);
        Notebook saved = notebookRepo.saveAndFlush(n);
        ownedNotebookIds.add(saved.getId());
        return saved;
    }

    private Document createDocument(User owner, Notebook notebook) {
        Document d = new Document();
        d.setUser(owner);
        d.setNotebook(notebook);
        d.setFileName("qtest_doc_" + UUID.randomUUID());
        d.setFilePath("/qtest");
        d.setStatus("READY");
        Document saved = docRepo.saveAndFlush(d);
        ownedDocumentIds.add(saved.getId());
        return saved;
    }

    private Topic createTopic(Document d) {
        Topic t = new Topic();
        t.setDocument(d);
        t.setName("QTest Topic " + UUID.randomUUID());
        Topic saved = topicRepo.saveAndFlush(t);
        ownedTopicIds.add(saved.getId());
        return saved;
    }

    private Concept createConcept() {
        Concept c = new Concept();
        String unique = UUID.randomUUID().toString().replace("-", "");
        c.setCanonicalName("C_" + unique);
        c.setNormalizedName("c_" + unique);
        c.setNotebook(nb);
        Concept saved = conceptRepo.saveAndFlush(c);
        ownedConceptIds.add(saved.getId());
        return saved;
    }

    private Flashcard createFlashcardDirect(Document d, Concept c) {
        Flashcard f = new Flashcard();
        f.setDocument(d);
        f.setQuestion("Q");
        f.setAnswer("A");
        f.setConcept(c);
        Flashcard saved = flashcardRepo.saveAndFlush(f);
        ownedFlashcardIds.add(saved.getId());
        return saved;
    }

    private Flashcard createFlashcardTopic(Topic t, Concept c) {
        Flashcard f = new Flashcard();
        f.setTopic(t);
        f.setQuestion("Q");
        f.setAnswer("A");
        f.setConcept(c);
        Flashcard saved = flashcardRepo.saveAndFlush(f);
        ownedFlashcardIds.add(saved.getId());
        return saved;
    }

    private FlashcardReviewState createState(User owner, Flashcard f, Instant dueAt) {
        FlashcardReviewState s = new FlashcardReviewState();
        s.setUser(owner);
        s.setFlashcard(f);
        s.setDueAt(dueAt);
        s.setState(FlashcardScheduleStatus.LEARNING);
        s.setDifficulty(0.3);
        s.setStability(1.0);
        FlashcardReviewState saved = stateRepo.saveAndFlush(s);
        ownedStateIds.add(saved.getId());
        return saved;
    }

    private void setupUserAndNotebook() {
        this.u  = createUser();
        this.nb = createNotebook(this.u);
    }

    // ── Cleanup certification test ────────────────────────────────────────────

    /**
     * DX: Verifies that deleteFixtureOwned() removes exactly owned rows and leaves unrelated rows intact.
     * Creates a sentinel user outside tracked IDs, runs cleanup, asserts sentinel still exists.
     */
    @Test
    void testDX_CleanupVerifiesOwnedRowsAbsentSentinelPreserved() {
        // Create a sentinel user that this test does NOT add to ownedUserIds.
        User sentinel = new User();
        sentinel.setUsername("sentinel_" + UUID.randomUUID());
        sentinel.setEmail("sentinel_" + UUID.randomUUID() + "@example.com");
        sentinel.setPassword("hash");
        sentinel.setRole("USER");
        sentinel = userRepo.saveAndFlush(sentinel);
        final Long sentinelId = sentinel.getId();

        try {
            // Create owned fixture rows
            setupUserAndNotebook();
            Document d = createDocument(u, nb);
            Flashcard f = createFlashcardDirect(d, null);
            createState(u, f, Instant.parse("2026-01-01T10:00:00Z"));

            // Record owned IDs for post-cleanup assertions
            List<Long> snapStates    = new ArrayList<>(ownedStateIds);
            List<Long> snapCards     = new ArrayList<>(ownedFlashcardIds);
            List<Long> snapDocs      = new ArrayList<>(ownedDocumentIds);
            List<Long> snapNotebooks = new ArrayList<>(ownedNotebookIds);
            List<Long> snapUsers     = new ArrayList<>(ownedUserIds);

            // Run cleanup explicitly (will also run again in @AfterEach with empty lists — harmless)
            deleteFixtureOwned();
            // Clear tracking so @AfterEach is a no-op
            ownedEventIds.clear(); ownedStateIds.clear(); ownedFlashcardIds.clear();
            ownedTopicIds.clear(); ownedDocumentIds.clear(); ownedConceptIds.clear();
            ownedNotebookIds.clear(); ownedUserIds.clear();

            // Owned rows must be absent
            for (Long id : snapStates)    assertThat(stateRepo.existsById(id)).as("state %d absent", id).isFalse();
            for (Long id : snapCards)     assertThat(flashcardRepo.existsById(id)).as("flashcard %d absent", id).isFalse();
            for (Long id : snapDocs)      assertThat(docRepo.existsById(id)).as("document %d absent", id).isFalse();
            for (Long id : snapNotebooks) assertThat(notebookRepo.existsById(id)).as("notebook %d absent", id).isFalse();
            for (Long id : snapUsers)     assertThat(userRepo.existsById(id)).as("user %d absent", id).isFalse();

            // Sentinel must still be present
            assertThat(userRepo.existsById(sentinelId)).as("sentinel user must survive cleanup").isTrue();
        } finally {
            // Always clean sentinel regardless of test outcome
            userRepo.deleteById(sentinelId);
        }
    }

    // ── D1-D20 Tests ──────────────────────────────────────────────────────────

    @Test
    void testD1_RealDueCardReturned() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        createState(u, f, Instant.parse("2026-01-01T10:00:00Z"));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).hasSize(1);
        assertThat(res.dueCards().get(0).flashcardId()).isEqualTo(f.getId());
    }

    @Test
    void testD2_DueAtExactlyNowReturned() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        createState(u, f, Instant.parse("2026-01-01T12:00:00Z"));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).hasSize(1);
    }

    @Test
    void testD3_FutureStateExcluded() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        createState(u, f, Instant.parse("2026-01-01T14:00:00Z"));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).isEmpty();
    }

    @Test
    void testD4_NoStateCardReturnedAsNew() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).hasSize(1);
        assertThat(res.newCards().get(0).flashcardId()).isEqualTo(f.getId());
    }

    @Test
    void testD5_ReviewedFutureCardNotNew() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        createState(u, f, Instant.parse("2026-01-01T14:00:00Z"));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testD6_DeletedDueCardExcluded() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        createState(u, f, Instant.parse("2026-01-01T10:00:00Z"));
        f.setDeletedAt(Instant.now());
        flashcardRepo.saveAndFlush(f);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).isEmpty();
    }

    @Test
    void testD7_DeletedNewCardExcluded() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f = createFlashcardDirect(d, null);
        f.setDeletedAt(Instant.now());
        flashcardRepo.saveAndFlush(f);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testD8_ForeignOwnerCardExcluded() {
        setupUserAndNotebook();
        User foreignUser = createUser();
        Document foreignDoc = createDocument(foreignUser, nb);
        createFlashcardDirect(foreignDoc, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testD9_WrongNotebookCardExcluded() {
        setupUserAndNotebook();
        Notebook wrongNb = createNotebook(u);
        Document wrongDoc = createDocument(u, wrongNb);
        createFlashcardDirect(wrongDoc, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testD10_DirectDocumentScopedCorrectly() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        createFlashcardDirect(d, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).hasSize(1);
    }

    @Test
    void testD11_TopicDocumentScopedCorrectly() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Topic t = createTopic(d);
        createFlashcardTopic(t, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).hasSize(1);
    }

    @Test
    void testD12_AmbiguousTwoDocumentCardExcluded() {
        setupUserAndNotebook();
        Document d1 = createDocument(u, nb);
        Document d2 = createDocument(u, nb);
        Topic t = createTopic(d2);

        Flashcard f = new Flashcard();
        f.setDocument(d1);
        f.setTopic(t);
        f.setQuestion("Q");
        f.setAnswer("A");
        Flashcard saved = flashcardRepo.saveAndFlush(f);
        ownedFlashcardIds.add(saved.getId());

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).isEmpty();
    }

    @Test
    void testD13_DueOrderingDeterministic() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f1 = createFlashcardDirect(d, null);
        Flashcard f2 = createFlashcardDirect(d, null);
        Flashcard f3 = createFlashcardDirect(d, null);

        createState(u, f1, Instant.parse("2026-01-01T10:00:00Z"));
        createState(u, f2, Instant.parse("2026-01-01T10:00:00Z"));
        createState(u, f3, Instant.parse("2026-01-01T09:00:00Z"));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).extracting("flashcardId").containsExactly(
                f3.getId(),
                Math.min(f1.getId(), f2.getId()),
                Math.max(f1.getId(), f2.getId())
        );
    }

    @Test
    void testD14_FallbackNewOrderingDeterministic() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f1 = createFlashcardDirect(d, null);
        Flashcard f2 = createFlashcardDirect(d, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(
                Math.min(f1.getId(), f2.getId()),
                Math.max(f1.getId(), f2.getId())
        );
    }

    @Test
    void testD15_NoStateEventRowsCreatedByQueueCall() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        createFlashcardDirect(d, null);

        long stateCountBefore = stateRepo.count();
        long eventCountBefore = eventRepo.count();

        service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));

        assertThat(stateRepo.count()).isEqualTo(stateCountBefore);
        assertThat(eventRepo.count()).isEqualTo(eventCountBefore);
    }

    @Test
    void testD16_LimitBehavior() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        for (int i = 0; i < 3; i++) {
            createFlashcardDirect(d, null);
        }

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 2));
        assertThat(res.newCards()).hasSize(2);
    }

    @Test
    void testD17_AdaptivePriorityAffectsNewOnly() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Concept c1 = createConcept();
        Concept c2 = createConcept();

        Flashcard f1 = createFlashcardDirect(d, c1);
        Flashcard f2 = createFlashcardDirect(d, c2);

        AdaptiveRecommendationDto rec = new AdaptiveRecommendationDto();
        rec.setConceptId(c2.getId());
        when(adaptiveServiceMock.getRecommendations(eq(u.getId()), eq(nb.getId()), anyInt())).thenReturn(List.of(rec));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(f2.getId(), f1.getId());
    }

    @Test
    void testD18_AdaptiveDoesNotAlterDueOrder() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Concept c1 = createConcept();
        Concept c2 = createConcept();

        Flashcard f1 = createFlashcardDirect(d, c1);
        Flashcard f2 = createFlashcardDirect(d, c2);

        createState(u, f1, Instant.parse("2026-01-01T10:00:00Z"));
        createState(u, f2, Instant.parse("2026-01-01T11:00:00Z"));

        AdaptiveRecommendationDto rec = new AdaptiveRecommendationDto();
        rec.setConceptId(c2.getId());
        when(adaptiveServiceMock.getRecommendations(eq(u.getId()), eq(nb.getId()), anyInt())).thenReturn(List.of(rec));

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.dueCards()).extracting("flashcardId").containsExactly(f1.getId(), f2.getId());
    }

    @Test
    void testD19_LegacyConceptNullNewCardSupported() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        Flashcard f1 = createFlashcardDirect(d, null);

        FlashcardStudyQueueResult res = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        assertThat(res.newCards()).extracting("flashcardId").containsExactly(f1.getId());
    }

    @Test
    void testD20_RepeatedQueueReadIsIdempotentAndReadOnly() {
        setupUserAndNotebook();
        Document d = createDocument(u, nb);
        createFlashcardDirect(d, null);

        FlashcardStudyQueueResult res1 = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));
        FlashcardStudyQueueResult res2 = service.getQueue(new FlashcardStudyQueueQuery(u.getId(), nb.getId(), 50, 50));

        assertThat(res1.newCards()).isEqualTo(res2.newCards());
    }
}
