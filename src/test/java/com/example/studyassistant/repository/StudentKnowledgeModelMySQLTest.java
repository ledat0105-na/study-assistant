package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class StudentKnowledgeModelMySQLTest {

    @Autowired
    private StudentConceptMasteryRepository masteryRepository;

    @Autowired
    private StudentLearningEventRepository eventRepository;

    // Child-first cleanup: these must be cleared before concepts/notebooks/users
    @Autowired
    private ConceptRelationSourceRepository conceptRelationSourceRepository;

    @Autowired
    private ConceptRelationRepository conceptRelationRepository;

    @Autowired
    private ConceptSourceRepository conceptSourceRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;
    private User otherUser;
    private Notebook notebook;
    private Concept concept;
    private Concept otherConcept;

    @BeforeEach
    void setUp() {
        // Child-first proven order (from GraphAugmentedRagIntegrationTest / GlobalBudgetIntegrationTest).
        // ConceptRelationSource references ConceptRelation and DocumentChunk → must go first.
        // ConceptRelation references Concept → must go before concepts.
        // ConceptSource references Concept and DocumentChunk → must go before concepts/chunks.
        // DocumentChunk references Document → before documents.
        // Document references Notebook → before notebooks.
        // Concepts and Notebooks reference each other bidirectionally → concepts before notebooks (concepts FK -> notebooks).
        // StudentConceptMastery/StudentLearningEvent reference User and Concept → first.
        masteryRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        conceptRelationSourceRepository.deleteAllInBatch();
        conceptRelationRepository.deleteAllInBatch();
        conceptSourceRepository.deleteAllInBatch();
        documentChunkRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        conceptRepository.deleteAllInBatch();
        notebookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create fixtures
        user = new User();
        user.setUsername("mysql_skm_user");
        user.setEmail("mysql_skm@example.com");
        user.setPassword("password");
        user.setFullName("MySQL SKM User");
        user = userRepository.saveAndFlush(user);

        otherUser = new User();
        otherUser.setUsername("mysql_skm_other");
        otherUser.setEmail("mysql_skm_other@example.com");
        otherUser.setPassword("password");
        otherUser.setFullName("MySQL SKM Other");
        otherUser = userRepository.saveAndFlush(otherUser);

        notebook = new Notebook();
        notebook.setTitle("MySQL Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("MySQL Concept");
        concept.setNormalizedName("mysql concept");
        concept.setDescription("MySQL Description");
        concept.setConceptType("CONCEPT");
        concept = conceptRepository.saveAndFlush(concept);

        otherConcept = new Concept();
        otherConcept.setNotebook(notebook);
        otherConcept.setCanonicalName("MySQL Concept 2");
        otherConcept.setNormalizedName("mysql concept 2");
        otherConcept.setDescription("MySQL Description 2");
        otherConcept.setConceptType("CONCEPT");
        otherConcept = conceptRepository.saveAndFlush(otherConcept);
    }

    private void runInTransaction(Runnable runnable) {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            runnable.run();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    @Test
    void testMasteryInsertValid() { // K
        runInTransaction(() -> {
            StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.5, 1);
            masteryRepository.saveAndFlush(mastery);
            assertThat(mastery.getId()).isNotNull();
        });
    }

    @Test
    void testEventInsertValid() { // L
        runInTransaction(() -> {
            StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-mysql-1", "QUIZ",
                    1.0, "SRC", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event);
            assertThat(event.getId()).isNotNull();
        });
    }

    @Test
    void testDuplicateMasterySameUserConcept() { // M
        runInTransaction(() -> {
            StudentConceptMastery mastery1 = new StudentConceptMastery(user, concept, 0.5, 1);
            masteryRepository.saveAndFlush(mastery1);
        });

        // Expected: DB rejects second insert for same (user, concept) pair.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentConceptMastery mastery2 = new StudentConceptMastery(user, concept, 0.8, 2);
                masteryRepository.saveAndFlush(mastery2);
            });
        });
    }

    @Test
    void testSameConceptDifferentUser() { // N
        runInTransaction(() -> {
            StudentConceptMastery mastery1 = new StudentConceptMastery(user, concept, 0.5, 1);
            masteryRepository.saveAndFlush(mastery1);
        });

        // DB FK accepts structurally: different user but same concept.
        // This documents that DB cannot enforce ownership equality (user must own concept.notebook).
        // Domain ownership must be enforced at service level in SKM1.5.
        runInTransaction(() -> {
            StudentConceptMastery mastery2 = new StudentConceptMastery(otherUser, concept, 0.8, 2);
            masteryRepository.saveAndFlush(mastery2);
            assertThat(mastery2.getId()).isNotNull();
        });
    }

    @Test
    void testDuplicateIdempotencySameUserConcept() { // O
        runInTransaction(() -> {
            StudentLearningEvent event1 = new StudentLearningEvent(user, concept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event1);
        });

        // Expected: DB rejects second insert with same (user, idempotency_key, concept) triple.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentLearningEvent event2 = new StudentLearningEvent(user, concept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
                eventRepository.saveAndFlush(event2);
            });
        });
    }

    @Test
    void testSameIdempotencyDifferentConcept() { // P
        runInTransaction(() -> {
            StudentLearningEvent event1 = new StudentLearningEvent(user, concept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event1);
        });

        // Different concept_id → unique constraint not violated.
        runInTransaction(() -> {
            StudentLearningEvent event2 = new StudentLearningEvent(user, otherConcept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event2);
            assertThat(event2.getId()).isNotNull();
        });
    }

    @Test
    void testSameIdempotencyDifferentUser() { // Q
        runInTransaction(() -> {
            StudentLearningEvent event1 = new StudentLearningEvent(user, concept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event1);
        });

        // Different user_id → unique constraint not violated.
        runInTransaction(() -> {
            StudentLearningEvent event2 = new StudentLearningEvent(otherUser, concept, "dup-key", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event2);
            assertThat(event2.getId()).isNotNull();
        });
    }

    @Test
    void testMasteryEstimateLessThanZero() { // R
        // Expected: DB CHECK rejects mastery_estimate < 0.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentConceptMastery mastery = new StudentConceptMastery(user, concept, -0.1, 1);
                masteryRepository.saveAndFlush(mastery);
            });
        });
    }

    @Test
    void testMasteryEstimateGreaterThanOne() { // S
        // Expected: DB CHECK rejects mastery_estimate > 1.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 1.1, 1);
                masteryRepository.saveAndFlush(mastery);
            });
        });
    }

    @Test
    void testNormalizedScoreLessThanZero() { // T
        // Expected: DB CHECK rejects normalized_score < 0.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentLearningEvent event = new StudentLearningEvent(user, concept, "key-t", "Q", -0.1, "S", "1", LocalDateTime.now());
                eventRepository.saveAndFlush(event);
            });
        });
    }

    @Test
    void testNormalizedScoreGreaterThanOne() { // U
        // Expected: DB CHECK rejects normalized_score > 1.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentLearningEvent event = new StudentLearningEvent(user, concept, "key-u", "Q", 1.1, "S", "1", LocalDateTime.now());
                eventRepository.saveAndFlush(event);
            });
        });
    }

    @Test
    void testNegativeEvidenceCount() { // V
        // Expected: DB CHECK rejects evidence_count < 0.
        assertThrows(DataIntegrityViolationException.class, () -> {
            runInTransaction(() -> {
                StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.5, -1);
                masteryRepository.saveAndFlush(mastery);
            });
        });
    }

    @Test
    void testDeletingUserCascades() { // W
        // Insert mastery and event for otherUser (who owns no notebooks).
        // Cross-user structural FK acceptance is a known DB limitation.
        // Ownership enforcement belongs to the SKM service/write boundary.
        runInTransaction(() -> {
            StudentConceptMastery mastery = new StudentConceptMastery(otherUser, concept, 0.5, 1);
            masteryRepository.saveAndFlush(mastery);
            StudentLearningEvent event = new StudentLearningEvent(otherUser, concept, "k", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event);
        });

        // Verify rows exist before delete.
        assertThat(userRepository.findById(otherUser.getId())).isPresent();
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(notebookRepository.findById(notebook.getId())).isPresent();
        assertThat(conceptRepository.findById(concept.getId())).isPresent();
        assertThat(masteryRepository.findByUserIdAndConceptId(otherUser.getId(), concept.getId())).isPresent();
        assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(otherUser.getId(), concept.getId())).hasSize(1);

        // Delete otherUser → should cascade mastery and events.
        runInTransaction(() -> {
            userRepository.deleteById(otherUser.getId());
            userRepository.flush();
        });

        // Verify cascade: both mastery and event rows must be gone.
        // But primary user, notebook, and concept must still exist.
        runInTransaction(() -> {
            assertThat(userRepository.findById(otherUser.getId())).isEmpty();
            assertThat(masteryRepository.findAll()).isEmpty();
            assertThat(eventRepository.findAll()).isEmpty();
            
            assertThat(userRepository.findById(user.getId())).isPresent();
            assertThat(notebookRepository.findById(notebook.getId())).isPresent();
            assertThat(conceptRepository.findById(concept.getId())).isPresent();
        });
    }

    @Test
    void testDeletingConceptCascades() { // X
        // Insert mastery and event for concept.
        runInTransaction(() -> {
            StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.5, 1);
            masteryRepository.saveAndFlush(mastery);
            StudentLearningEvent event = new StudentLearningEvent(user, concept, "k", "Q", 1.0, "S", "1", LocalDateTime.now());
            eventRepository.saveAndFlush(event);
        });

        // Verify rows exist before delete.
        assertThat(masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId())).isPresent();
        assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(user.getId(), concept.getId())).hasSize(1);

        // Delete concept → should cascade mastery and events.
        runInTransaction(() -> {
            conceptRepository.deleteById(concept.getId());
            conceptRepository.flush();
        });

        // Verify cascade: rows specifically for this concept must be gone.
        runInTransaction(() -> {
            assertThat(masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId())).isEmpty();
            assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(user.getId(), concept.getId())).isEmpty();
        });
    }

    @Test
    void testUnseenConceptNoMasteryRow() { // Y
        // Read mastery for concept that has never had evidence.
        // Must return empty. Must NOT create any row.
        runInTransaction(() -> {
            Optional<StudentConceptMastery> found = masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId());
            assertThat(found).isEmpty();
        });

        // Verify that the lookup itself did not create a row.
        runInTransaction(() -> {
            assertThat(masteryRepository.findAll()).isEmpty();
        });
    }
}
