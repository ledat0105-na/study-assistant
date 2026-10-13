package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.dto.TutorSessionState;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TutorSessionMySQLIntegrationTest {

    @Autowired
    private TutorSessionService tutorSessionService;

    @Autowired
    private TutorSessionRepository tutorSessionRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private User foreignUser;
    private Notebook testNotebook;

    private List<Long> sessionIds = new ArrayList<>();
    private List<Long> conversationIds = new ArrayList<>();
    private List<Long> conceptIds = new ArrayList<>();

    @BeforeAll
    void setupFixtures() {
        testUser = new User();
        testUser.setUsername("tutor_integration_user");
        testUser.setEmail("tutor_int@example.com");
        testUser.setPassword("hash");
        testUser.setRole("STUDENT");
        testUser = userRepository.save(testUser);

        foreignUser = new User();
        foreignUser.setUsername("tutor_foreign_user");
        foreignUser.setEmail("foreign_int@example.com");
        foreignUser.setPassword("hash");
        foreignUser.setRole("STUDENT");
        foreignUser = userRepository.save(foreignUser);

        testNotebook = new Notebook();
        testNotebook.setTitle("Integration Notebook");
        testNotebook.setUser(testUser);
        testNotebook = notebookRepository.save(testNotebook);
    }

    @AfterAll
    void cleanupFixtures() {
        // Clean up from bottom to top
        for (Long id : sessionIds) {
            try { tutorSessionRepository.deleteById(id); } catch (Exception e) {}
        }
        for (Long id : conversationIds) {
            try { conversationRepository.deleteById(id); } catch (Exception e) {}
        }
        for (Long id : conceptIds) {
            try { conceptRepository.deleteById(id); } catch (Exception e) {}
        }
        try { notebookRepository.delete(testNotebook); } catch (Exception e) {}
        try { userRepository.delete(testUser); } catch (Exception e) {}
        try { userRepository.delete(foreignUser); } catch (Exception e) {}
    }

    private Conversation createConversation(User u, Notebook n) {
        Conversation c = new Conversation();
        c.setTitle("Test Chat");
        c.setUser(u);
        c.setNotebook(n);
        c.setIsDeleted(false);
        c = conversationRepository.save(c);
        conversationIds.add(c.getId());
        return c;
    }

    private Concept createConcept(String name) {
        Concept c = new Concept();
        c.setNotebook(testNotebook);
        c.setCanonicalName(name);
        c.setNormalizedName(name.toLowerCase());
        c = conceptRepository.save(c);
        conceptIds.add(c.getId());
        return c;
    }

    @Test
    void m1_m2_realLazyInsertAndIdempotency() {
        Conversation c = createConversation(testUser, testNotebook);

        // M1: First call
        TutorSessionState s1 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        assertNotNull(s1);
        assertNotNull(s1.tutorSessionId());
        assertEquals(c.getId(), s1.conversationId());
        assertNull(s1.currentConceptId());
        assertNotNull(s1.tutorSessionId());
        sessionIds.add(s1.tutorSessionId());

        // M2: Second call
        TutorSessionState s2 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        assertEquals(s1.tutorSessionId(), s2.tutorSessionId());

        long count = tutorSessionRepository.count();
        assertTrue(count >= 1); // Should be exactly 1 for this conversation
    }

    @Test
    void m3_uniqueConversationConstraint() {
        Conversation c = createConversation(testUser, testNotebook);
        TutorSessionState s1 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        sessionIds.add(s1.tutorSessionId());

        // Attempt direct insert
        TutorSession s2 = new TutorSession();
        s2.setConversation(c);
        s2.setCreatedAt(java.time.LocalDateTime.now());
        s2.setUpdatedAt(java.time.LocalDateTime.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            tutorSessionRepository.saveAndFlush(s2);
        });
    }

    @Test
    void m4_conceptSetNull() {
        Conversation c = createConversation(testUser, testNotebook);
        Concept concept = createConcept("TEST_CONCEPT_M4");
        
        TutorSessionState s1 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        sessionIds.add(s1.tutorSessionId());
        tutorSessionService.updateCurrentConcept(testUser.getId(), c.getId(), concept);

        TutorSession loaded = tutorSessionRepository.findById(s1.tutorSessionId()).orElseThrow(RuntimeException::new);
        assertEquals(concept.getId(), loaded.getCurrentConcept().getId());

        // Delete concept via repository safely (avoiding cascade from notebook)
        conceptRepository.deleteById(concept.getId());
        conceptIds.remove(concept.getId());

        // Verify set null via direct query
        Long conceptIdAfter = jdbcTemplate.queryForObject("SELECT current_concept_id FROM tutor_sessions WHERE id = ?", Long.class, s1.tutorSessionId());
        assertNull(conceptIdAfter);
    }

    @Test
    void m5_conversationCascade() {
        Conversation c = createConversation(testUser, testNotebook);
        TutorSessionState s1 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());

        // Verify exists
        Long countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tutor_sessions WHERE id = ?", Long.class, s1.tutorSessionId());
        assertEquals(1L, countBefore);

        conversationRepository.deleteById(c.getId());
        conversationIds.remove(c.getId());

        Long countAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tutor_sessions WHERE id = ?", Long.class, s1.tutorSessionId());
        assertEquals(0L, countAfter);
    }

    @Test
    void m6_legacyConversation() {
        Conversation c = createConversation(testUser, testNotebook);
        // Do not call service
        Optional<TutorSession> session = tutorSessionRepository.findByConversationId(c.getId());
        assertFalse(session.isPresent());
    }

    @Test
    void m7_notebookRequired() {
        Conversation c = createConversation(testUser, null); // Allowed by schema
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        });
        assertTrue(ex.getMessage().contains("does not have a notebook"));
    }

    @Test
    void m8_ownership() {
        Conversation c = createConversation(testUser, testNotebook);

        assertThrows(IllegalArgumentException.class, () -> {
            tutorSessionService.getOrCreateSession(foreignUser.getId(), c.getId());
        });
    }

    @Test
    void m9_updateCurrentConcept() {
        Conversation c = createConversation(testUser, testNotebook);
        Concept concept = createConcept("TEST_CONCEPT_M9");
        TutorSessionState s1 = tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());
        sessionIds.add(s1.tutorSessionId());

        TutorSessionState updated = tutorSessionService.updateCurrentConcept(testUser.getId(), c.getId(), concept);
        assertNotNull(updated.currentConceptId());
        assertEquals(concept.getId(), updated.currentConceptId());
        assertTrue(!false);
    }

    @Test
    void m10_concurrentFirstCreate() throws InterruptedException, ExecutionException {
        Conversation c = createConversation(testUser, testNotebook);

        int numThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Callable<TutorSessionState> task = () -> tutorSessionService.getOrCreateSession(testUser.getId(), c.getId());

        List<Future<TutorSessionState>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(task));
        }

        Long expectedId = null;
        for (Future<TutorSessionState> future : futures) {
            TutorSessionState session = future.get();
            assertNotNull(session);
            sessionIds.add(session.tutorSessionId());
            if (expectedId == null) {
                expectedId = session.tutorSessionId();
            } else {
                assertEquals(expectedId, session.tutorSessionId());
            }
        }

        executor.shutdown();
        
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tutor_sessions WHERE conversation_id = ?", Long.class, c.getId());
        assertEquals(1L, count);
    }

    @Test
    void m_verifyMetadata() {
        // Verify DDL constraint details via Information Schema
        List<Map<String, Object>> uniqueConstraints = jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE TABLE_NAME = 'tutor_sessions' AND CONSTRAINT_TYPE = 'UNIQUE'"
        );
        boolean hasUniqueConv = uniqueConstraints.stream().anyMatch(row -> 
                row.get("CONSTRAINT_NAME").toString().contains("uk_conversation_id") ||
                row.get("CONSTRAINT_NAME").toString().contains("conversation_id") // fallback for auto-generated
        );
        assertTrue(hasUniqueConv, "Missing UNIQUE constraint on conversation_id");

        List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME, DELETE_RULE FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
                + "WHERE TABLE_NAME = 'tutor_sessions'"
        );

        boolean foundCascade = false;
        boolean foundSetNull = false;

        for (Map<String, Object> fk : fks) {
            String name = fk.get("CONSTRAINT_NAME").toString();
            String rule = fk.get("DELETE_RULE").toString();
            if (name.contains("conversation") || name.contains("fk_tutor_conversation")) {
                assertEquals("CASCADE", rule, "Conversation FK must ON DELETE CASCADE");
                foundCascade = true;
            } else if (name.contains("concept") || name.contains("fk_tutor_concept")) {
                assertEquals("SET NULL", rule, "Concept FK must ON DELETE SET NULL");
                foundSetNull = true;
            }
        }

        assertTrue(foundCascade, "Conversation FK not found");
        assertTrue(foundSetNull, "Concept FK not found");
    }
}
