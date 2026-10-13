package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest(properties = "spring.jpa.show-sql=false")
@org.springframework.context.annotation.Import({GroundedQuizGenerationService.class, QuizGenerationProperties.class,
        QuizScopeValidator.class, QuizActiveChunkSelector.class, com.example.studyassistant.config.LegacyJackson2Config.class,
        GroundedQuizGenerationServiceAtomicityIntegrationTest.TransactionConfig.class})
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@Tag("Integration")
public class GroundedQuizGenerationServiceAtomicityIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TransactionConfig {
        @org.springframework.context.annotation.Bean
        TransactionTemplate transactionTemplate(org.springframework.transaction.PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired
    private GroundedQuizGenerationService service;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotebookRepository notebookRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository chunkRepository;
    @Autowired
    private QuizRepository quizRepository;
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private QuizQuestionSourceRepository quizQuestionSourceRepository;

    @MockitoBean
    private LlmProvider llmProvider;
    
    // We spy on the service to assert inside the mocked LLM method
    @MockitoSpyBean
    private QuizQuestionOptionRepository quizQuestionOptionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private Notebook testNotebook;
    private Document testDoc;
    private DocumentChunk testChunk;
    private GroundedQuizGenerationCommand cmd;

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("atom_user_" + System.currentTimeMillis());
            testUser.setPassword("pass");
            testUser.setEmail("atom_" + System.currentTimeMillis() + "@example.com");
            testUser = userRepository.save(testUser);

            testNotebook = new Notebook();
            testNotebook.setTitle("Atom Notebook");
            testNotebook.setUser(testUser);
            testNotebook = notebookRepository.save(testNotebook);

            testDoc = new Document();
            testDoc.setUser(testUser);
            testDoc.setNotebook(testNotebook);
            testDoc.setFileName("atom.pdf");
            testDoc.setFilePath("/tmp/atom.pdf");
            testDoc.setStatus("READY");
            testDoc.setActiveIndexVersion("V1");
            testDoc = documentRepository.save(testDoc);

            testChunk = new DocumentChunk();
            testChunk.setDocument(testDoc);
            testChunk.setChunkIndex(0);
            testChunk.setContent("Atom content");
            testChunk.setTokenCount(10);
            testChunk.setIndexVersion("V1");
            testChunk = chunkRepository.save(testChunk);

            return null;
        });

        cmd = new GroundedQuizGenerationCommand(
                testUser.getId(),
                QuizScopeType.DOCUMENT,
                testNotebook.getId(),
                testDoc.getId(),
                null,
                null,
                QuizMode.PRACTICE,
                10,
                List.of(QuizQuestionType.MCQ_SINGLE),
                "Atom Quiz",
                null,
                null
        );
    }

    @AfterEach
    void cleanup() {
        // Safe exact cleanup without broad deletes
        transactionTemplate.execute(status -> {
            if (testChunk != null) chunkRepository.findById(testChunk.getId()).ifPresent(chunkRepository::delete);
            if (testDoc != null) documentRepository.findById(testDoc.getId()).ifPresent(documentRepository::delete);
            if (testNotebook != null) notebookRepository.findById(testNotebook.getId()).ifPresent(notebookRepository::delete);
            if (testUser != null) userRepository.findById(testUser.getId()).ifPresent(userRepository::delete);
            return null;
        });
    }

    @Test
    void testLlmOutsideTxAndPersistenceInsideTxAndRollbackOnFailure() {
        // Ensure starting state has no quizzes for this notebook
        assertThat(quizRepository.count()).isEqualTo(0);

        String mockJson = "{\"questions\": [{\"type\": \"MCQ_SINGLE\", \"questionText\": \"Q1\", \"points\": 1.0, \"sources\": [\"S1\"], \"options\": [{\"text\": \"A\", \"isCorrect\": true}, {\"text\": \"B\", \"isCorrect\": false}]}]}";

        // Verify LLM is called OUTSIDE transaction
        when(llmProvider.generate(any(), any(), anyDouble())).thenAnswer(inv -> {
            boolean isTxActive = TransactionSynchronizationManager.isActualTransactionActive();
            assertThat(isTxActive).as("LLM must be called outside transaction").isFalse();
            return mockJson.replace("Q1", "Q" + System.nanoTime());
        });

        // Deliberate failure inside the persistence phase
        java.util.concurrent.atomic.AtomicInteger optionSaves = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> {
            boolean isTxActive = TransactionSynchronizationManager.isActualTransactionActive();
            assertThat(isTxActive).as("Persistence must happen inside transaction").isTrue();
            if (optionSaves.incrementAndGet() == 3) throw new RuntimeException("Deliberate persistence failure");
            QuizQuestionOption option = inv.getArgument(0);
            entityManager.persist(option);
            return option;
        }).when(quizQuestionOptionRepository).save(any());

        // Run generation, expect failure
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generate(cmd));
        assertThat(ex.getMessage()).contains("Deliberate persistence failure");

        // Verify NO partial quiz remains (transaction rollback)
        transactionTemplate.execute(status -> {
            long count = quizRepository.count();
            assertThat(count).as("Entire generation batch must be rolled back").isEqualTo(0);
            assertThat(quizQuestionRepository.count()).isZero();
            assertThat(quizQuestionOptionRepository.count()).isZero();
            assertThat(quizQuestionSourceRepository.count()).isZero();
            return null;
        });
        
        verify(llmProvider, times(2)).generate(any(), any(), anyDouble());
    }

    @Test
    void testFailFastIfTransactionIsActive() {
        // If we call service.generate inside an active transaction, it MUST fail closed.
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transactionTemplate.execute(status -> {
                return service.generate(cmd);
            });
        });
        assertThat(ex.getMessage()).contains("Quiz LLM generation must run outside a database transaction");
        
        // Ensure LLM was NOT called
        verify(llmProvider, never()).generate(any(), any(), anyDouble());
    }
}
