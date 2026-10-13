package com.example.studyassistant.repository;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.domain.flashcard.FlashcardRating;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlashcardFsrsMappingTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private FlashcardReviewStateRepository stateRepo;

    @Autowired
    private FlashcardReviewEventRepository eventRepo;

    @Autowired
    private FlashcardRepository flashcardRepo;

    @Test
    void testStateUniqueContract() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("hash");
        user.setEmail(UUID.randomUUID().toString() + "@example.com");
        user.setRole("USER");
        em.persist(user);

        Flashcard f = new Flashcard();
        f.setQuestion("Q");
        f.setAnswer("A");
        em.persist(f);

        FlashcardReviewState state1 = new FlashcardReviewState();
        state1.setUser(user);
        state1.setFlashcard(f);
        state1.setState(FlashcardScheduleStatus.LEARNING);
        state1.setDifficulty(5.0);
        state1.setStability(2.0);
        state1.setDueAt(Instant.parse("2026-01-01T00:00:00Z"));
        em.persist(state1);

        Optional<FlashcardReviewState> found = stateRepo.findByUserIdAndFlashcardId(user.getId(), f.getId());
        assertThat(found).isPresent();
    }

    @Test
    void testEventIdempotencyMapping() {
        User user = new User();
        user.setUsername("testuser2");
        user.setPassword("hash");
        user.setEmail(UUID.randomUUID().toString() + "@example.com");
        user.setRole("USER");
        em.persist(user);

        Flashcard f = new Flashcard();
        f.setQuestion("Q");
        f.setAnswer("A");
        em.persist(f);

        String token = UUID.randomUUID().toString();

        FlashcardReviewEvent event = new FlashcardReviewEvent();
        event.setUser(user);
        event.setFlashcard(f);
        event.setReviewToken(token);
        event.setRating(FlashcardRating.GOOD);
        event.setReviewedAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setStateAfter(FlashcardScheduleStatus.LEARNING);
        event.setDifficultyAfter(5.0);
        event.setStabilityAfter(2.0);
        event.setDueAfter(Instant.parse("2026-01-02T00:00:00Z"));
        event.setAlgorithmVersion("FSRS-6");
        event.setSchedulerVersion("java-fsrs-1.0.0");
        event.setSchedulerConfigVersion("FLASHCARD_FSRS_V1");
        
        // nullable before values
        event.setStateBefore(null);
        event.setStepBefore(null);

        em.persist(event);

        Optional<FlashcardReviewEvent> found = eventRepo.findByUserIdAndReviewToken(user.getId(), token);
        assertThat(found).isPresent();
        assertThat(found.get().getStateBefore()).isNull();
    }
}
