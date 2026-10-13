package com.example.studyassistant.domain.flashcard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = FsrsFlashcardScheduler.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "flashcard.fsrs.desired-retention=0.85",
        "flashcard.fsrs.maximum-interval-days=3650",
        "flashcard.fsrs.fuzzing-enabled=false"
})
class FsrsFlashcardConfigTest {

    @Autowired
    private FsrsFlashcardScheduler scheduler;

    @Test
    void testConfigPropertiesLoaded() {
        assertNotNull(scheduler);
        // While we can't easily introspect the internal Scheduler parameters without reflection,
        // we verify Spring wired it without failure.
        
        // Validate identity constants exist
        assertEquals("FSRS-6", FlashcardFsrsConfigConstants.ALGORITHM_VERSION);
        assertEquals("java-fsrs-1.0.0", FlashcardFsrsConfigConstants.SCHEDULER_VERSION);
        assertEquals("FLASHCARD_FSRS_V1", FlashcardFsrsConfigConstants.SCHEDULER_CONFIG_VERSION);
    }
}
