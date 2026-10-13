package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Tag("Integration")
public class ConceptServiceConcurrencyTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private ConceptPersistenceHelper persistenceHelperSpy;

    private User user;
    private Notebook notebook;

    @BeforeEach
    void setUp() {
        // Safe setup: Create test-owned data only
        user = new User();
        user.setUsername("concurrency_testuser_" + System.currentTimeMillis());
        user.setEmail("concurrency_" + System.currentTimeMillis() + "@example.com");
        user.setPassword("hashedpass");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Concurrency Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);
    }

    @AfterEach
    void tearDown() {
        // Safe teardown: Delete only test-owned data respecting FK
        if (notebook != null && notebook.getId() != null) {
            conceptRepository.deleteAll(conceptRepository.findByNotebookId(notebook.getId()));
            notebookRepository.delete(notebook);
        }
        if (user != null && user.getId() != null) {
            userRepository.delete(user);
        }
    }

    @Test
    void getOrCreate_ConcurrentDuplicateCreation() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger insertAttempts = new AtomicInteger(0);

        // Spy intercepts the insert to guarantee both threads reach it
        doAnswer(invocation -> {
            insertAttempts.incrementAndGet();
            barrier.await(5, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(persistenceHelperSpy).insert(any(Concept.class));

        Callable<Concept> task1 = () -> conceptService.getOrCreate(notebook, "Machine Learning", "Desc 1", "TOPIC");
        Callable<Concept> task2 = () -> conceptService.getOrCreate(notebook, "MACHINE   LEARNING", "Desc 2", "TOPIC");

        Future<Concept> future1 = executor.submit(task1);
        Future<Concept> future2 = executor.submit(task2);

        Concept concept1 = null;
        Concept concept2 = null;

        try {
            concept1 = future1.get(10, TimeUnit.SECONDS);
            concept2 = future2.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DataIntegrityViolationException || cause instanceof UnexpectedRollbackException) {
                org.junit.jupiter.api.Assertions.fail("Concurrency failure: " + cause.getClass().getSimpleName(), e);
            }
            throw e;
        } finally {
            executor.shutdownNow();
            Mockito.reset(persistenceHelperSpy);
        }

        // Prove both reached the INSERT path (meaning both observed initial absence)
        assertThat(insertAttempts.get()).isEqualTo(2);

        // Prove both operations completed successfully and returned identical non-null IDs
        assertThat(concept1).isNotNull();
        assertThat(concept1.getId()).isNotNull();
        assertThat(concept2).isNotNull();
        assertThat(concept1.getId()).isEqualTo(concept2.getId());

        // Prove querying the database afterward shows exactly ONE Concept
        List<Concept> dbConcepts = conceptRepository.findByNotebookId(notebook.getId());
        assertThat(dbConcepts).hasSize(1);
        assertThat(dbConcepts.get(0).getId()).isEqualTo(concept1.getId());
        assertThat(dbConcepts.get(0).getNormalizedName()).isEqualTo("machine learning");
    }

    @Test
    void getOrCreate_OuterTransactionRegression() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger insertAttempts = new AtomicInteger(0);

        // Spy intercepts the insert
        doAnswer(invocation -> {
            insertAttempts.incrementAndGet();
            barrier.await(5, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(persistenceHelperSpy).insert(any(Concept.class));

        // Task 1: Just a normal caller
        Callable<Concept> task1 = () -> conceptService.getOrCreate(notebook, "Deep Learning", "Desc 1", "TOPIC");
        
        // Task 2: Wrapped in an outer transaction!
        Callable<Concept> task2 = () -> {
            return transactionTemplate.execute(status -> {
                // Since this runs in an outer transaction (REPEATABLE READ by default),
                // if ConceptService didn't use REQUIRES_NEW for the fallback read, 
                // it would inherit this transaction's snapshot and fail to see the new row!
                return conceptService.getOrCreate(notebook, "deep learning", "Desc 2", "TOPIC");
            });
        };

        Future<Concept> future1 = executor.submit(task1);
        Future<Concept> future2 = executor.submit(task2);

        Concept concept1 = null;
        Concept concept2 = null;

        try {
            concept1 = future1.get(10, TimeUnit.SECONDS);
            concept2 = future2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            Mockito.reset(persistenceHelperSpy);
        }

        assertThat(insertAttempts.get()).isEqualTo(2);
        assertThat(concept1).isNotNull();
        assertThat(concept2).isNotNull();
        assertThat(concept1.getId()).isEqualTo(concept2.getId());
    }
}
