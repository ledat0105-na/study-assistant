package com.example.studyassistant.domain.flashcard.generation;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.llm.LlmProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Import({GroundedFlashcardGenerationService.class, FlashcardGenerationProperties.class, BoundedActiveChunkSelector.class,
        com.example.studyassistant.config.LegacyJackson2Config.class, FlashcardBatchH2Test.TransactionConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlashcardBatchH2Test {
    @org.springframework.boot.test.context.TestConfiguration
    static class TransactionConfig {
        @Bean TransactionTemplate transactionTemplate(org.springframework.transaction.PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }
    @Autowired GroundedFlashcardGenerationService service;
    @Autowired UserRepository users;
    @Autowired DocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired FlashcardRepository cards;
    @MockitoSpyBean FlashcardSourceRepository sources;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean LlmProvider llm;
    User owner;
    Document doc;
    AtomicInteger calls;
    @BeforeEach void fixture() {
        owner = new User(); String unique = UUID.randomUUID().toString();
        owner.setUsername(unique); owner.setEmail(unique + "@flashcard.test"); owner.setPassword("test");
        owner = users.saveAndFlush(owner);
        doc = new Document(); doc.setUser(owner); doc.setFileName("Thẻ học"); doc.setFilePath("unused-h2-only");
        doc.setStatus("READY"); doc.setActiveIndexVersion("v1"); doc = documents.saveAndFlush(doc);
        for (int i = 0; i < 2; i++) {
            DocumentChunk c = new DocumentChunk(); c.setDocument(doc); c.setChunkIndex(i); c.setContent("Nội dung " + i);
            c.setTokenCount(10); c.setIndexVersion("v1"); chunks.saveAndFlush(c);
        }
        calls = new AtomicInteger();
        when(llm.generate(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            int batch = calls.incrementAndGet();
            return "{\"cards\":[{\"question\":\"Câu " + batch + "\",\"answer\":\"Đáp án\",\"sources\":[\"[S" + batch + "]\"]}]}";
        });
    }
    GroundedFlashcardGenerationCommand command() {
        return new GroundedFlashcardGenerationCommand(owner.getId(), doc.getId(), null, 16);
    }
    @Test void batchesPersistTogetherWithTheirOwnGroundedSources() {
        var result = service.generate(command());
        assertEquals(2, calls.get()); assertEquals(2, result.persistedCount());
        assertEquals(2, cards.findByDocumentId(doc.getId()).size());
        assertNotEquals(result.cards().get(0).sources().get(0).documentChunkId(), result.cards().get(1).sources().get(0).documentChunkId());
    }
    @Test void secondSourceFailureRollsBackCardsAndFirstSource() {
        long sourceCount = sources.count(); AtomicInteger writes = new AtomicInteger();
        doAnswer(inv -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("Deliberate source failure");
            FlashcardSource source = inv.getArgument(0); entityManager.persist(source); return source;
        }).when(sources).save(any());
        assertThrows(IllegalStateException.class, () -> service.generate(command()));
        assertEquals(2, calls.get()); assertTrue(cards.findByDocumentId(doc.getId()).isEmpty());
        assertEquals(sourceCount, sources.count());
    }

    @Test void quickBatchUsesOneAiCallAndSamplesAcrossLongDocument() {
        for (int i=2;i<50;i++) {
            DocumentChunk c = new DocumentChunk(); c.setDocument(doc); c.setChunkIndex(i); c.setContent("Nội dung " + i);
            c.setTokenCount(10); c.setIndexVersion("v1"); chunks.saveAndFlush(c);
        }
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            calls.incrementAndGet();
            String prompt = inv.getArgument(0);
            assertTrue(prompt.contains("Nội dung 0"));
            assertTrue(prompt.contains("Nội dung 49"));
            return "{\"cards\":[{\"question\":\"Câu ngắn\",\"answer\":\"Đáp án\",\"sources\":[\"S1\"]}]}";
        }).when(llm).generate(anyString(), anyString(), anyDouble());
        var result = service.generateQuickBatch(new GroundedFlashcardGenerationCommand(owner.getId(), doc.getId(), null, null));
        assertEquals(1, calls.get()); assertEquals(8, result.requestedCount());
        assertEquals(1, result.persistedCount());
        assertEquals(1, sources.findByFlashcardIdOrderBySourceOrderAsc(result.cards().get(0).flashcardId()).size());
    }
    @Test void quickBatchPreservesIndexGateBeforeCallingAi() {
        doc.setStatus("PROCESSING"); documents.saveAndFlush(doc);
        assertThrows(com.example.studyassistant.domain.flashcard.generation.exception.DocumentNotIndexedException.class,
                () -> service.generateQuickBatch(command()));
        assertEquals(0, calls.get());
    }
}
