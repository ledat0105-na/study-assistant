package com.example.studyassistant.repository;

import com.example.studyassistant.domain.flashcard.FlashcardRating;
import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.entity.FlashcardReviewEvent;
import com.example.studyassistant.entity.FlashcardReviewState;
import com.example.studyassistant.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.FlashcardSource;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@Transactional
class FlashcardFsrsMySQLIntegrationTest {
    
    @Autowired private FlashcardReviewStateRepository stateRepo;
    @Autowired private FlashcardReviewEventRepository eventRepo;
    @Autowired private FlashcardRepository flashcardRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private FlashcardSourceRepository sourceRepo;
    @Autowired private DocumentChunkRepository chunkRepo;
    @Autowired private DocumentRepository documentRepo;
    @Autowired private TopicRepository topicRepo;
    @Autowired private NotebookRepository notebookRepo;
    @Autowired private ConceptRepository conceptRepo;
    @Autowired private jakarta.persistence.EntityManager em;
    
    @Test
    void testM1UtcInstantRoundtrip() {
        User user = createUser();
        Flashcard f = createFlashcard();
        Instant exactTime = Instant.parse("2026-01-01T12:34:56.123456Z");
        
        FlashcardReviewState state = new FlashcardReviewState();
        state.setUser(user);
        state.setFlashcard(f);
        state.setState(FlashcardScheduleStatus.LEARNING);
        state.setDifficulty(5.0);
        state.setStability(2.0);
        state.setDueAt(exactTime);
        stateRepo.saveAndFlush(state);
        
        FlashcardReviewState loaded = stateRepo.findById(state.getId()).get();
        assertThat(loaded.getDueAt()).isEqualTo(exactTime);
    }

    @Test
    void testM2UniqueStateEnforced() {
        User user = createUser();
        Flashcard f = createFlashcard();
        
        FlashcardReviewState s1 = new FlashcardReviewState();
        s1.setUser(user);
        s1.setFlashcard(f);
        s1.setState(FlashcardScheduleStatus.LEARNING);
        s1.setDifficulty(5.0);
        s1.setStability(2.0);
        s1.setDueAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        stateRepo.saveAndFlush(s1);
        
        FlashcardReviewState s2 = new FlashcardReviewState();
        s2.setUser(user);
        s2.setFlashcard(f);
        s2.setState(FlashcardScheduleStatus.REVIEW);
        s2.setDifficulty(5.0);
        s2.setStability(2.0);
        s2.setDueAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        
        assertThrows(DataIntegrityViolationException.class, () -> stateRepo.saveAndFlush(s2));
    }

    @Test
    void testM3UniqueReviewTokenEnforced() {
        User user = createUser();
        Flashcard f = createFlashcard();
        String token = UUID.randomUUID().toString();
        
        createEvent(user, f, token);
        assertThrows(DataIntegrityViolationException.class, () -> createEvent(user, f, token));
    }

    @Test
    void testM4DuplicateChunkRejected() {
        User user = createUser();
        DocumentChunk chunk = createChunk(user);
        Flashcard f = createFlashcard();
        
        FlashcardSource s1 = new FlashcardSource();
        s1.setFlashcard(f);
        s1.setDocumentChunk(chunk);
        s1.setSourceOrder(1);
        sourceRepo.saveAndFlush(s1);
        
        FlashcardSource s2 = new FlashcardSource();
        s2.setFlashcard(f);
        s2.setDocumentChunk(chunk);
        s2.setSourceOrder(2);
        
        assertThrows(DataIntegrityViolationException.class, () -> sourceRepo.saveAndFlush(s2));
    }
    
    @Test
    void testM5DuplicateSourceOrderRejected() {
        User user = createUser();
        DocumentChunk chunk1 = createChunk(user);
        DocumentChunk chunk2 = createChunk(user);
        Flashcard f = createFlashcard();
        
        FlashcardSource s1 = new FlashcardSource();
        s1.setFlashcard(f);
        s1.setDocumentChunk(chunk1);
        s1.setSourceOrder(1);
        sourceRepo.saveAndFlush(s1);
        
        FlashcardSource s2 = new FlashcardSource();
        s2.setFlashcard(f);
        s2.setDocumentChunk(chunk2);
        s2.setSourceOrder(1);
        
        assertThrows(DataIntegrityViolationException.class, () -> sourceRepo.saveAndFlush(s2));
    }

    @Test
    void testM6PessimisticWrite() {
        Flashcard f = createFlashcard();
        Optional<Flashcard> locked = flashcardRepo.findByIdForUpdate(f.getId());
        assertThat(locked).isPresent();
    }
    
    @Test
    void testM7SoftDeletePreservesHistory() {
        User user = createUser();
        Flashcard f = createFlashcard();
        FlashcardReviewState state = createState(user, f);
        FlashcardReviewEvent event = createEvent(user, f, UUID.randomUUID().toString());
        
        f.setDeletedAt(Instant.now());
        flashcardRepo.saveAndFlush(f);
        
        assertThat(flashcardRepo.findById(f.getId())).isPresent();
        assertThat(stateRepo.findById(state.getId())).isPresent();
        assertThat(eventRepo.findById(event.getId())).isPresent();
    }
    
    @Test
    void testM8PhysicalDeleteRestrict() {
        User user = createUser();
        Flashcard f = createFlashcard();
        createState(user, f);
        
        em.flush();
        em.clear();
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            flashcardRepo.deleteById(f.getId());
            flashcardRepo.flush();
        });
    }
    
    @Test
    void testM9ConceptSetNull() {
        User user = createUser();
        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Test Notebook");
        notebookRepo.saveAndFlush(notebook);

        Concept c = new Concept();
        c.setNotebook(notebook);
        c.setCanonicalName("C1");
        c.setNormalizedName("c1");
        conceptRepo.saveAndFlush(c);
        
        Flashcard f = createFlashcard();
        f.setConcept(c);
        flashcardRepo.saveAndFlush(f);
        
        em.flush();
        em.clear();
        
        conceptRepo.deleteById(c.getId());
        conceptRepo.flush();
        
        em.clear(); // Clear session to ensure we read from DB
        Flashcard loaded = flashcardRepo.findById(f.getId()).get();
        assertThat(loaded.getConcept()).isNull();
    }

    @Test
    void testM10LegacyFlashcardNoState() {
        Flashcard f = createFlashcard();
        assertThat(f.getId()).isNotNull();
        assertThat(stateRepo.findByUserIdAndFlashcardId(1L, f.getId())).isEmpty();
    }
    
    private User createUser() {
        User user = new User();
        user.setUsername("testuser_" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("hash");
        user.setRole("USER");
        return userRepo.saveAndFlush(user);
    }
    
    private DocumentChunk createChunk(User user) {
        Document doc = new Document();
        doc.setUser(user);
        doc.setFileName("Test Doc");
        doc.setFilePath("/tmp/test");
        doc.setStatus("READY");
        documentRepo.saveAndFlush(doc);
        
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(doc);
        chunk.setChunkIndex(0);
        chunk.setContent("Chunk Content");
        chunk.setTokenCount(10);
        chunk.setIndexVersion("v1");
        return chunkRepo.saveAndFlush(chunk);
    }
    
    private Flashcard createFlashcard() {
        Flashcard f = new Flashcard();
        f.setQuestion("Q");
        f.setAnswer("A");
        return flashcardRepo.saveAndFlush(f);
    }
    
    private FlashcardReviewState createState(User user, Flashcard f) {
        FlashcardReviewState state = new FlashcardReviewState();
        state.setUser(user);
        state.setFlashcard(f);
        state.setState(FlashcardScheduleStatus.LEARNING);
        state.setDifficulty(5.0);
        state.setStability(2.0);
        state.setDueAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return stateRepo.saveAndFlush(state);
    }
    
    private FlashcardReviewEvent createEvent(User user, Flashcard f, String token) {
        FlashcardReviewEvent event = new FlashcardReviewEvent();
        event.setUser(user);
        event.setFlashcard(f);
        event.setReviewToken(token);
        event.setRating(FlashcardRating.GOOD);
        event.setReviewedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        event.setStateAfter(FlashcardScheduleStatus.LEARNING);
        event.setDifficultyAfter(5.0);
        event.setStabilityAfter(2.0);
        event.setDueAfter(Instant.now().truncatedTo(ChronoUnit.MICROS));
        event.setAlgorithmVersion("FSRS-6");
        event.setSchedulerVersion("java-fsrs-1.0.0");
        event.setSchedulerConfigVersion("FLASHCARD_FSRS_V1");
        return eventRepo.saveAndFlush(event);
    }
}
