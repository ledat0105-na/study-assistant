package com.example.studyassistant.service;

import com.example.studyassistant.dto.QuizSubmissionRequest;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.Quiz;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizQuestion;
import com.example.studyassistant.entity.QuizResult;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.QuizAttemptRepository;
import com.example.studyassistant.repository.QuizQuestionRepository;
import com.example.studyassistant.repository.QuizRepository;
import com.example.studyassistant.repository.QuizResultRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Tag("Integration")
@SpringBootTest
@ActiveProfiles("integration")
public class QuizSubmissionServiceMySQLIntegrationTest {

    @Autowired private QuizSubmissionService quizSubmissionService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizResultRepository quizResultRepository;

    @MockitoSpyBean
    private QuizAttemptRepository quizAttemptRepository;

    private User owner;
    private Notebook notebook;
    private Document document;
    private Quiz quiz;
    private QuizQuestion q1;
    private QuizQuestion q2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = new User();
        owner.setUsername("u_" + suffix);
        owner.setEmail("u_" + suffix + "@ex.com");
        owner.setPassword("hash");
        owner.setFullName("User");
        userRepository.save(owner);

        notebook = new Notebook();
        notebook.setUser(owner);
        notebook.setTitle("NB");
        notebookRepository.save(notebook);

        document = new Document();
        document.setUser(owner);
        document.setNotebook(notebook);
        document.setFileName("f.pdf");
        document.setFilePath("/f.pdf");
        document.setStatus("READY");
        documentRepository.save(document);

        quiz = new Quiz();
        quiz.setTitle("Q");
        quiz.setDocument(document);
        quizRepository.save(quiz);

        q1 = new QuizQuestion();
        q1.setQuiz(quiz);
        q1.setQuestion("Q1");
        q1.setAnswerA("A"); q1.setAnswerB("B"); q1.setAnswerC("C"); q1.setAnswerD("D");
        q1.setCorrectAnswer("A");
        quizQuestionRepository.save(q1);

        q2 = new QuizQuestion();
        q2.setQuiz(quiz);
        q2.setQuestion("Q2");
        q2.setAnswerA("A"); q2.setAnswerB("B"); q2.setAnswerC("C"); q2.setAnswerD("D");
        q2.setCorrectAnswer("B");
        quizQuestionRepository.save(q2);
    }

    @AfterEach
    void tearDown() {
        // Scoped cleanup
        quizAttemptRepository.deleteAll(quizAttemptRepository.findByQuizId(quiz.getId()));
        quizResultRepository.deleteAll(quizResultRepository.findByQuizId(quiz.getId()));
        quizQuestionRepository.deleteAll(quizQuestionRepository.findByQuizId(quiz.getId()));
        quizRepository.delete(quiz);
        documentRepository.delete(document);
        notebookRepository.delete(notebook);
        userRepository.delete(owner);
    }

    @Test
    void testM1_ConcurrentSameTokenSamePayload() throws Exception {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest req1 = new QuizSubmissionRequest();
        req1.setSubmissionToken(token);
        req1.setAnswers(Map.of(q1.getId(), "A", q2.getId(), "B"));

        QuizSubmissionRequest req2 = new QuizSubmissionRequest();
        req2.setSubmissionToken(token);
        req2.setAnswers(Map.of(q1.getId(), "A", q2.getId(), "B"));

        CountDownLatch startLatch = new CountDownLatch(1);
        
        CompletableFuture<QuizResult> t1 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                return quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<QuizResult> t2 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                return quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startLatch.countDown(); // Go!
        
        QuizResult res1 = t1.get(10, TimeUnit.SECONDS);
        QuizResult res2 = t2.get(10, TimeUnit.SECONDS);

        assertNotNull(res1);
        assertNotNull(res2);
        assertEquals(res1.getId(), res2.getId());

        long resultCount = quizResultRepository.findByQuizId(quiz.getId()).size();
        assertEquals(1, resultCount);

        long attemptCount = quizAttemptRepository.findByQuizId(quiz.getId()).size();
        assertEquals(1, attemptCount);
    }

    @Test
    void testM2_ConcurrentSameTokenDifferentPayload() throws Exception {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest req1 = new QuizSubmissionRequest();
        req1.setSubmissionToken(token);
        req1.setAnswers(Map.of(q1.getId(), "A"));

        QuizSubmissionRequest req2 = new QuizSubmissionRequest();
        req2.setSubmissionToken(token);
        req2.setAnswers(Map.of(q1.getId(), "B"));

        CountDownLatch startLatch = new CountDownLatch(1);
        
        AtomicReference<QuizResult> successResult = new AtomicReference<>();
        AtomicReference<Exception> conflictException = new AtomicReference<>();

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                QuizResult r = quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req1);
                successResult.set(r);
            } catch (Exception e) {
                conflictException.set(e);
            }
        });

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                QuizResult r = quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req2);
                successResult.set(r);
            } catch (Exception e) {
                conflictException.set(e);
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(t1, t2).get(10, TimeUnit.SECONDS);

        assertNotNull(successResult.get());
        assertNotNull(conflictException.get());
        assertTrue(conflictException.get() instanceof ResponseStatusException);
        assertEquals(409, ((ResponseStatusException) conflictException.get()).getStatusCode().value());

        long resultCount = quizResultRepository.findByQuizId(quiz.getId()).size();
        assertEquals(1, resultCount);

        long attemptCount = quizAttemptRepository.findByQuizId(quiz.getId()).size();
        assertEquals(1, attemptCount);
    }

    @Test
    void testM3_TransactionAtomicRollback() {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken(token);
        req.setAnswers(Map.of(q1.getId(), "A"));

        doThrow(new RuntimeException("Simulated DB failure")).when(quizAttemptRepository).saveAndFlush(any());

        try {
            quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req);
            fail("Should throw exception");
        } catch (RuntimeException e) {
            assertEquals("Simulated DB failure", e.getMessage());
        }

        long resultCount = quizResultRepository.findByQuizId(quiz.getId()).size();
        assertEquals(0, resultCount);

        long attemptCount = quizAttemptRepository.findByQuizId(quiz.getId()).size();
        assertEquals(0, attemptCount);
    }

    @Test
    void testM4_CompletedAtConsistency() {
        String token = UUID.randomUUID().toString();
        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken(token);
        req.setAnswers(Map.of(q1.getId(), "A"));

        // First successful submit
        QuizResult firstResult = quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req);
        assertNotNull(firstResult);
        assertNotNull(firstResult.getCompletedAt(), "First submission completedAt must not be null");

        // Idempotent replay
        QuizResult replayResult = quizSubmissionService.submitQuiz(owner.getId(), quiz.getId(), req);
        assertNotNull(replayResult);
        assertNotNull(replayResult.getCompletedAt(), "Replay completedAt must not be null");

        assertEquals(firstResult.getCompletedAt(), replayResult.getCompletedAt(), "Timestamps must be identical");
    }
}
