package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Tag("Standard")
public class StudentKnowledgeModelFoundationTest {

    @Autowired
    private StudentConceptMasteryRepository masteryRepository;

    @Autowired
    private StudentLearningEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    private User user;
    private Concept concept;
    private Notebook notebook;

    @BeforeEach
    void setUp() {
        masteryRepository.deleteAll();
        eventRepository.deleteAll();
        conceptRepository.deleteAll();
        notebookRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("testuser_skm");
        user.setEmail("test_skm@example.com");
        user.setPassword("password");
        user.setFullName("Test SKM User");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Test Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Test Concept");
        concept.setNormalizedName("test concept");
        concept.setDescription("Test Description");
        concept.setConceptType("CONCEPT");
        concept = conceptRepository.saveAndFlush(concept);
    }

    @Test
    void testMasteryMappingBoots() { // A
        StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.5, 1);
        mastery = masteryRepository.saveAndFlush(mastery);
        assertThat(mastery.getId()).isNotNull();
    }

    @Test
    void testEventMappingBoots() { // B
        StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-123", "QUIZ_RESPONSE", 
                1.0, "QUIZ_ATTEMPT", "QA-100", LocalDateTime.now());
        event = eventRepository.saveAndFlush(event);
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void testUserConceptMasteryIdentity() { // C
        StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.5, 1);
        masteryRepository.saveAndFlush(mastery);
        
        Optional<StudentConceptMastery> found = masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getMasteryEstimate()).isEqualTo(0.5);
    }

    @Test
    void testIdempotencyIdentity() { // D
        StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-123", "QUIZ_RESPONSE", 
                1.0, "QUIZ_ATTEMPT", "QA-100", LocalDateTime.now());
        eventRepository.saveAndFlush(event);
        
        Optional<StudentLearningEvent> found = eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(user.getId(), "uuid-123", concept.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getNormalizedScore()).isEqualTo(1.0);
    }

    @Test
    void testNoMasteryRowForUnseenConcept() { // E
        Optional<StudentConceptMastery> found = masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId());
        assertThat(found).isEmpty(); // lookup returns empty
        // Database remains without mastery row - verified by isEmpty()
    }

    @Test
    void testEntityFieldsMapCorrectly() { // F
        LocalDateTime now = LocalDateTime.now();
        StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.75, 5);
        mastery.setLastEvidenceAt(now);
        mastery = masteryRepository.saveAndFlush(mastery);
        
        StudentConceptMastery foundMastery = masteryRepository.findById(mastery.getId()).get();
        assertThat(foundMastery.getUser().getId()).isEqualTo(user.getId());
        assertThat(foundMastery.getConcept().getId()).isEqualTo(concept.getId());
        assertThat(foundMastery.getMasteryEstimate()).isEqualTo(0.75);
        assertThat(foundMastery.getEvidenceCount()).isEqualTo(5);
        assertThat(foundMastery.getLastEvidenceAt()).isNotNull();
        assertThat(foundMastery.getCreatedAt()).isNotNull();
        assertThat(foundMastery.getUpdatedAt()).isNotNull();

        StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-123", "QUIZ_RESPONSE", 
                0.8, "QUIZ_ATTEMPT", "QA-100", now);
        event = eventRepository.saveAndFlush(event);
        
        StudentLearningEvent foundEvent = eventRepository.findById(event.getId()).get();
        assertThat(foundEvent.getUser().getId()).isEqualTo(user.getId());
        assertThat(foundEvent.getConcept().getId()).isEqualTo(concept.getId());
        assertThat(foundEvent.getIdempotencyKey()).isEqualTo("uuid-123");
        assertThat(foundEvent.getEventType()).isEqualTo("QUIZ_RESPONSE");
        assertThat(foundEvent.getNormalizedScore()).isEqualTo(0.8);
        assertThat(foundEvent.getSourceType()).isEqualTo("QUIZ_ATTEMPT");
        assertThat(foundEvent.getSourceId()).isEqualTo("QA-100");
        assertThat(foundEvent.getOccurredAt()).isNotNull();
        assertThat(foundEvent.getCreatedAt()).isNotNull();
    }

    @Test
    void testEventScoreBoundary0Accepted() { // G
        StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-0", "Q", 
                0.0, "S", "1", LocalDateTime.now());
        event = eventRepository.saveAndFlush(event);
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void testEventScoreBoundary1Accepted() { // H
        StudentLearningEvent event = new StudentLearningEvent(user, concept, "uuid-1", "Q", 
                1.0, "S", "1", LocalDateTime.now());
        event = eventRepository.saveAndFlush(event);
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void testMasteryBoundary0Accepted() { // I
        StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 0.0, 1);
        mastery = masteryRepository.saveAndFlush(mastery);
        assertThat(mastery.getId()).isNotNull();
    }

    @Test
    void testMasteryBoundary1Accepted() { // J
        StudentConceptMastery mastery = new StudentConceptMastery(user, concept, 1.0, 1);
        mastery = masteryRepository.saveAndFlush(mastery);
        assertThat(mastery.getId()).isNotNull();
    }
}
