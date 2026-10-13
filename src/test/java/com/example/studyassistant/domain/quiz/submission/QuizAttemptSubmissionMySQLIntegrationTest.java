package com.example.studyassistant.domain.quiz.submission;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.MasteryUpdateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@Import(QuizAttemptSubmissionMySQLIntegrationTest.TestClockConfiguration.class)
public class QuizAttemptSubmissionMySQLIntegrationTest {

    @Autowired private QuizAttemptSubmissionService service;

    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository attemptAnswerRepository;
    @Autowired private QuizAttemptAnswerOptionRepository attemptOptionRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private StudentLearningEventRepository eventRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private QuizAttemptAnswerConceptRepository attemptConceptRepository;
    @Autowired private QuizAttemptAnswerAcceptedAnswerRepository attemptAcceptedAnswerRepository;
    @Autowired private QuizQuestionOptionRepository questionOptionRepository;
    @Autowired private QuizQuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;
    @Autowired private QuizQuestionConceptRepository questionConceptRepository;
    @Autowired private QuizQuestionSourceRepository questionSourceRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private QuizQuestionOptionRepository masterOptionRepository;
    @Autowired private AdjustableClock testClock;
    @MockitoSpyBean private MasteryUpdateEngine masteryUpdateEngine;

    @Autowired private TransactionTemplate transactionTemplate;

    private User testUser;
    private Notebook testNotebook;
    private Document testDocument;
    private DocumentChunk testChunk;
    private ConceptSource testConceptSource;
    private Quiz testQuiz;
    private Concept testConcept;
    private QuizQuestion testQuestion;
    private QuizAttempt testAttempt;
    private QuizAttemptAnswer testAnswer;

    @BeforeEach
    void setup() {
        testClock.setInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("sub_user_" + System.currentTimeMillis());
            testUser.setPassword("pass");
            testUser.setEmail("sub_" + System.currentTimeMillis() + "@example.com");
            testUser = userRepository.save(testUser);

            testNotebook = new Notebook();
            testNotebook.setTitle("Sub Notebook");
            testNotebook.setUser(testUser);
            testNotebook = notebookRepository.save(testNotebook);

            testDocument = new Document();
            testDocument.setUser(testUser);
            testDocument.setNotebook(testNotebook);
            testDocument.setFileName("ei16-fixture.txt");
            testDocument.setFilePath("test-fixtures/ei16-fixture.txt");
            testDocument.setStatus("READY");
            testDocument.setActiveIndexVersion("ei16-active-v1");
            testDocument = documentRepository.save(testDocument);

            testChunk = new DocumentChunk();
            testChunk.setDocument(testDocument);
            testChunk.setChunkIndex(0);
            testChunk.setContent("Fixture evidence for the EI1.6 concept.");
            testChunk.setTokenCount(7);
            testChunk.setIndexVersion(testDocument.getActiveIndexVersion());
            testChunk = documentChunkRepository.save(testChunk);

            testConcept = new Concept();
            testConcept.setNotebook(testNotebook);
            testConcept.setCanonicalName("Test Concept");
            testConcept.setNormalizedName("test concept");
            testConcept = conceptRepository.save(testConcept);

            testConceptSource = new ConceptSource();
            testConceptSource.setConcept(testConcept);
            testConceptSource.setDocumentChunk(testChunk);
            testConceptSource.setEvidenceText("Fixture evidence for the EI1.6 concept.");
            conceptSourceRepository.save(testConceptSource);

            testQuiz = new Quiz();
            testQuiz.setNotebook(testNotebook);
            testQuiz.setTitle("Sub Quiz");
            testQuiz.setStatus(QuizStatus.PUBLISHED);
            testQuiz.setMode(QuizMode.PRACTICE);
            testQuiz = quizRepository.save(testQuiz);

            testQuestion = new QuizQuestion();
            testQuestion.setQuiz(testQuiz);
            testQuestion.setQuestionOrder(1);
            testQuestion.setQuestionType(QuizQuestionType.MCQ_SINGLE);
            testQuestion.setPoints(1.0);
            testQuestion.setQuestion("Q1");
            testQuestion = questionRepository.save(testQuestion);

            QuizQuestionOption masterOption = new QuizQuestionOption();
            masterOption.setQuestion(testQuestion);
            masterOption.setOptionOrder(1);
            masterOption.setOptionText("Master option A");
            masterOption.setIsCorrect(true);
            masterOptionRepository.save(masterOption);

            QuizQuestionConcept masterConcept = new QuizQuestionConcept();
            masterConcept.setQuestion(testQuestion);
            masterConcept.setConcept(testConcept);
            questionConceptRepository.save(masterConcept);

            testAttempt = new QuizAttempt();
            testAttempt.setUser(testUser);
            testAttempt.setQuiz(testQuiz);
            testAttempt.setStatus("IN_PROGRESS");
            testAttempt.setStartedAt(LocalDateTime.now().minusMinutes(5));
            testAttempt.setScoringVersion("QUIZ_SCORING_V1");
            testAttempt.setModeSnapshot(QuizMode.PRACTICE);
            testAttempt.setMasteryApplied(false);
            testAttempt = attemptRepository.save(testAttempt);

            testAnswer = new QuizAttemptAnswer();
            testAnswer.setAttempt(testAttempt);
            testAnswer.setQuestion(testQuestion);
            testAnswer.setQuestionOrder(1);
            testAnswer.setQuestionTypeSnapshot(QuizQuestionType.MCQ_SINGLE);
            testAnswer.setPointsPossible(1.0);
            testAnswer.setQuestionTextSnapshot("Q1 Snapshot");
            testAnswer = attemptAnswerRepository.save(testAnswer);

            QuizAttemptAnswerOption opt1 = new QuizAttemptAnswerOption();
            opt1.setAttemptAnswer(testAnswer);
            opt1.setOptionOrder(1);
            opt1.setOptionTextSnapshot("Option A");
            opt1.setIsCorrectSnapshot(true);
            opt1.setStudentSelected(false);
            attemptOptionRepository.save(opt1);

            QuizAttemptAnswerConcept qc = new QuizAttemptAnswerConcept();
            qc.setAttemptAnswer(testAnswer);
            qc.setConcept(testConcept);
            attemptConceptRepository.save(qc);

            return null;
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(testUser.getId(), testConcept.getId())
                .forEach(eventRepository::delete);
            masteryRepository.findByUserIdAndConceptId(testUser.getId(), testConcept.getId())
                .ifPresent(masteryRepository::delete);
            attemptConceptRepository.findByAttemptAnswerId(testAnswer.getId()).forEach(attemptConceptRepository::delete);
            attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(testAnswer.getId()).forEach(attemptOptionRepository::delete);
            attemptAcceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(testAnswer.getId())
                .forEach(attemptAcceptedAnswerRepository::delete);
            attemptAnswerRepository.delete(testAnswer);
            attemptRepository.delete(testAttempt);
            questionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(testQuestion.getId()).forEach(questionOptionRepository::delete);
            questionAcceptedAnswerRepository.findByQuestionIdOrderByAcceptedOrderAsc(testQuestion.getId())
                .forEach(questionAcceptedAnswerRepository::delete);
            questionConceptRepository.findByQuestionId(testQuestion.getId()).forEach(questionConceptRepository::delete);
            questionSourceRepository.findByQuestionIdOrderBySourceOrderAsc(testQuestion.getId()).forEach(questionSourceRepository::delete);
            questionRepository.delete(testQuestion);
            quizRepository.delete(testQuiz);
            conceptSourceRepository.findByConceptId(testConcept.getId()).forEach(conceptSourceRepository::delete);
            conceptRepository.delete(testConcept);
            documentChunkRepository.findByDocumentIdAndIndexVersion(testDocument.getId(), testDocument.getActiveIndexVersion())
                .forEach(documentChunkRepository::delete);
            documentRepository.delete(testDocument);
            notebookRepository.delete(testNotebook);
            userRepository.delete(testUser);
            return null;
        });
    }


    @Test
    void testExpiredCommittedBeforeConflict() {
        transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(testAttempt.getId()).orElseThrow();
            attempt.setModeSnapshot(QuizMode.EXAM);
            attempt.setTimeLimitSecondsSnapshot(60); // 1 minute
            attempt.setStartedAt(LocalDateTime.now().minusMinutes(10)); // started 10 mins ago -> late
            attemptRepository.save(attempt);
            return null;
        });
        
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(
            testUser.getId(), testAttempt.getId(), token, List.of()
        );
        
        Exception e = assertThrows(IllegalStateException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("EXPIRED");
        
        // Assert it committed the EXPIRED state
        transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(testAttempt.getId()).orElseThrow();
            assertThat(attempt.getStatus()).isEqualTo("EXPIRED");
            return null;
        });
    }

    @Test
    void testMasteryExactlyOnce() {
        assertActiveGrounding();
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(
            testUser.getId(), testAttempt.getId(), token, List.of(
                new QuizAttemptSubmitAnswerPayload(1, List.of(1), null)
            )
        );

        QuizAttemptResultResponse firstResponse = service.submit(cmd);
        assertThat(firstResponse.status()).isEqualTo("COMPLETED");
        FixtureEvidenceState stateBeforeReplay = readFixtureEvidenceState();
        assertOneCorrectFixtureEvidence(stateBeforeReplay, token);
        assertAttemptMasteryApplied();

        // Exact replay
        QuizAttemptResultResponse replayResponse = service.submit(cmd);
        assertThat(replayResponse).isEqualTo(firstResponse);
        assertThat(readFixtureEvidenceState()).isEqualTo(stateBeforeReplay);
        assertAttemptMasteryApplied();
    }

    @Test
    void testSuccessfulSubmissionAndIdempotentReplay() {
        assertActiveGrounding();
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(
            testUser.getId(), testAttempt.getId(), token, List.of(
                new QuizAttemptSubmitAnswerPayload(1, List.of(1), null)
            )
        );

        // First submit
        QuizAttemptResultResponse resp1 = service.submit(cmd);
        assertThat(resp1.status()).isEqualTo("COMPLETED");
        assertThat(resp1.score()).isEqualTo(100.0);
        assertThat(resp1.perQuestionResults()).hasSize(1);
        assertThat(resp1.perQuestionResults().get(0).isCorrect()).isTrue();
        assertAttemptMasteryApplied();
        PersistedSubmissionState persistedBeforeReplay = readPersistedSubmissionState();
        assertThat(persistedBeforeReplay.status()).isEqualTo("COMPLETED");
        assertThat(persistedBeforeReplay.score()).isEqualTo(100.0);
        assertThat(persistedBeforeReplay.submissionToken()).isEqualTo(token);
        assertThat(persistedBeforeReplay.submissionHash()).hasSize(64);
        assertThat(persistedBeforeReplay.correctAnswers()).isEqualTo(1);
        assertThat(persistedBeforeReplay.totalQuestions()).isEqualTo(1);
        assertThat(persistedBeforeReplay.answerCorrect()).isTrue();
        assertThat(persistedBeforeReplay.pointsEarned()).isEqualTo(1.0);
        assertThat(persistedBeforeReplay.selected()).isTrue();
        FixtureEvidenceState stateBeforeReplay = readFixtureEvidenceState();
        assertOneCorrectFixtureEvidence(stateBeforeReplay, token);

        // Exact replay
        QuizAttemptResultResponse resp2 = service.submit(cmd);
        assertThat(resp2).isEqualTo(resp1);
        assertThat(readPersistedSubmissionState()).isEqualTo(persistedBeforeReplay);
        assertThat(readFixtureEvidenceState()).isEqualTo(stateBeforeReplay);
        assertAttemptMasteryApplied();
    }

    @Test
    void testSameTokenDifferentPayloadConflictsWithoutChangingPersistedState() {
        assertActiveGrounding();
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand firstCommand = answerCommand(token, List.of(1));
        QuizAttemptResultResponse firstResponse = service.submit(firstCommand);
        PersistedSubmissionState beforeConflict = readPersistedSubmissionState();
        FixtureEvidenceState evidenceBeforeConflict = readFixtureEvidenceState();

        QuizAttemptSubmitCommand changedPayload = new QuizAttemptSubmitCommand(
                testUser.getId(), testAttempt.getId(), token, List.of());
        IllegalStateException conflict = assertThrows(IllegalStateException.class, () -> service.submit(changedPayload));

        assertThat(conflict.getMessage()).contains("Conflict");
        assertThat(readPersistedSubmissionState()).isEqualTo(beforeConflict);
        assertThat(readFixtureEvidenceState()).isEqualTo(evidenceBeforeConflict);
        assertThat(service.submit(firstCommand)).isEqualTo(firstResponse);
    }

    @Test
    void testConcurrentSameAttemptSubmissionCompletesOnce() throws Exception {
        assertActiveGrounding();
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand command = answerCommand(token, List.of(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<QuizAttemptResultResponse> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.submit(command);
            });
            Future<QuizAttemptResultResponse> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.submit(command);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            QuizAttemptResultResponse firstResult = first.get(30, TimeUnit.SECONDS);
            QuizAttemptResultResponse secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult).isEqualTo(secondResult);
            assertResponseHasAuthoritativeGrading(firstResult);
            assertResponseHasAuthoritativeGrading(secondResult);

            PersistedSubmissionState persisted = readPersistedSubmissionState();
            assertThat(persisted.status()).isEqualTo("COMPLETED");
            assertThat(persisted.submissionToken()).isEqualTo(token);
            assertThat(persisted.submissionHash()).hasSize(64);
            assertThat(persisted.masteryApplied()).isTrue();
            assertThat(persisted.score()).isEqualTo(100.0);
            assertThat(persisted.correctAnswers()).isEqualTo(1);
            assertThat(persisted.pointsEarned()).isEqualTo(1.0);
            assertThat(persisted.answerCorrect()).isTrue();
            assertThat(persisted.selected()).isTrue();
            FixtureEvidenceState evidence = readFixtureEvidenceState();
            assertOneCorrectFixtureEvidence(evidence, token);
            assertThat(evidence.eventIds()).hasSize(1);
            assertAttemptMasteryApplied();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void testExactDeadlineEqualityIsAccepted() {
        LocalDateTime deadline = LocalDateTime.now(testClock);
        configureExamDeadline(deadline.minusSeconds(60));

        QuizAttemptResultResponse response = service.submit(answerCommand(UUID.randomUUID().toString(), List.of(1)));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(readPersistedSubmissionState().status()).isEqualTo("COMPLETED");
    }

    @Test
    void testPositiveNanosecondAfterDeadlineExpiresWithoutGradingOrMastery() {
        LocalDateTime deadline = LocalDateTime.now(testClock);
        configureExamDeadline(deadline.minusSeconds(60));
        testClock.setInstant(testClock.instant().plusNanos(1));

        assertThrows(IllegalStateException.class,
                () -> service.submit(answerCommand(UUID.randomUUID().toString(), List.of(1))));

        PersistedSubmissionState persisted = readPersistedSubmissionState();
        assertThat(persisted.status()).isEqualTo("EXPIRED");
        assertThat(persisted.submissionToken()).isNull();
        assertThat(persisted.submissionHash()).isNull();
        assertThat(persisted.answerCorrect()).isNull();
        assertThat(persisted.pointsEarned()).isNull();
        assertThat(persisted.selected()).isFalse();
        assertThat(masteryRepository.findByUserIdAndConceptId(testUser.getId(), testConcept.getId())).isEmpty();
        assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(testUser.getId(), testConcept.getId())).isEmpty();
    }

    @Test
    void testMasteryFailureRollsBackAuthoritativeCompletion() {
        assertActiveGrounding();
        doThrow(new IllegalStateException("Injected mastery persistence failure"))
                .when(masteryUpdateEngine).applyEvidence(any());

        assertThrows(IllegalStateException.class,
                () -> service.submit(answerCommand(UUID.randomUUID().toString(), List.of(1))));

        PersistedSubmissionState persisted = readPersistedSubmissionState();
        assertThat(persisted.status()).isEqualTo("IN_PROGRESS");
        assertThat(persisted.submissionToken()).isNull();
        assertThat(persisted.submissionHash()).isNull();
        assertThat(persisted.completedAt()).isNull();
        assertThat(persisted.answerCorrect()).isNull();
        assertThat(persisted.pointsEarned()).isNull();
        assertThat(persisted.selected()).isFalse();
        assertThat(masteryRepository.findByUserIdAndConceptId(testUser.getId(), testConcept.getId())).isEmpty();
        assertThat(eventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(testUser.getId(), testConcept.getId())).isEmpty();
    }

    @Test
    void testSubmissionGradesAttemptSnapshotsAfterMasterQuestionMutation() {
        assertActiveGrounding();
        transactionTemplate.execute(status -> {
            QuizQuestionOption masterOption = masterOptionRepository
                    .findByQuestionIdOrderByOptionOrderAsc(testQuestion.getId()).get(0);
            masterOption.setIsCorrect(false);
            masterOptionRepository.save(masterOption);
            questionConceptRepository.findByQuestionId(testQuestion.getId()).forEach(questionConceptRepository::delete);
            return null;
        });

        QuizAttemptResultResponse response = service.submit(
                answerCommand(UUID.randomUUID().toString(), List.of(1)));

        assertThat(response.score()).isEqualTo(100.0);
        assertThat(response.perQuestionResults().get(0).isCorrect()).isTrue();
        assertOneCorrectFixtureEvidence(readFixtureEvidenceState(), readPersistedSubmissionState().submissionToken());
    }

    @Test
    void testShortAnswerGradesAttemptAcceptedAnswerSnapshotAfterMasterMutation() {
        assertActiveGrounding();
        transactionTemplate.execute(status -> {
            testQuestion.setQuestionType(QuizQuestionType.SHORT_ANSWER);
            testQuestion.setQuestion("Mutated master question");
            questionRepository.save(testQuestion);

            testAnswer.setQuestionTypeSnapshot(QuizQuestionType.SHORT_ANSWER);
            attemptAnswerRepository.save(testAnswer);

            QuizAttemptAnswerAcceptedAnswer answerSnapshot = new QuizAttemptAnswerAcceptedAnswer();
            answerSnapshot.setAttemptAnswer(testAnswer);
            answerSnapshot.setAcceptedOrder(1);
            answerSnapshot.setAcceptedTextSnapshot("Snapshot answer");
            attemptAcceptedAnswerRepository.save(answerSnapshot);

            QuizQuestionAcceptedAnswer masterAnswer = new QuizQuestionAcceptedAnswer();
            masterAnswer.setQuestion(testQuestion);
            masterAnswer.setAcceptedOrder(1);
            masterAnswer.setAcceptedText("Snapshot answer");
            masterAnswer = questionAcceptedAnswerRepository.save(masterAnswer);
            masterAnswer.setAcceptedText("Mutated master answer");
            questionAcceptedAnswerRepository.save(masterAnswer);
            questionConceptRepository.findByQuestionId(testQuestion.getId()).forEach(questionConceptRepository::delete);
            return null;
        });

        QuizAttemptSubmitCommand command = new QuizAttemptSubmitCommand(
                testUser.getId(), testAttempt.getId(), UUID.randomUUID().toString(), List.of(
                new QuizAttemptSubmitAnswerPayload(1, null, "Snapshot answer")));
        QuizAttemptResultResponse response = service.submit(command);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.score()).isEqualTo(100.0);
        assertThat(response.perQuestionResults().get(0).isCorrect()).isTrue();
    }

    private void assertActiveGrounding() {
        assertThat(conceptSourceRepository.existsActiveGrounding(
                testConcept.getId(), testNotebook.getId(), testUser.getId())).isTrue();
    }

    private QuizAttemptSubmitCommand answerCommand(String token, List<Integer> optionOrders) {
        return new QuizAttemptSubmitCommand(testUser.getId(), testAttempt.getId(), token, List.of(
                new QuizAttemptSubmitAnswerPayload(1, optionOrders, null)));
    }

    private void configureExamDeadline(LocalDateTime startedAt) {
        transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(testAttempt.getId()).orElseThrow();
            attempt.setModeSnapshot(QuizMode.EXAM);
            attempt.setTimeLimitSecondsSnapshot(60);
            attempt.setStartedAt(startedAt);
            attemptRepository.save(attempt);
            return null;
        });
    }

    private PersistedSubmissionState readPersistedSubmissionState() {
        return transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(testAttempt.getId()).orElseThrow();
            QuizAttemptAnswer answer = attemptAnswerRepository.findById(testAnswer.getId()).orElseThrow();
            QuizAttemptAnswerOption option = attemptOptionRepository
                    .findByAttemptAnswerIdOrderByOptionOrderAsc(testAnswer.getId()).get(0);
            return new PersistedSubmissionState(
                    attempt.getStatus(), attempt.getScore(), attempt.getTotalQuestions(), attempt.getCorrectAnswers(),
                    attempt.getCompletedAt(), attempt.getSubmissionToken(), attempt.getSubmissionHash(),
                    attempt.getMasteryApplied(), answer.getIsCorrect(), answer.getPointsEarned(), option.getStudentSelected());
        });
    }

    private void assertOneCorrectFixtureEvidence(FixtureEvidenceState state, String token) {
        assertThat(state.evidenceCount()).isEqualTo(1);
        assertThat(state.eventIds()).hasSize(1);
        assertThat(state.idempotencyKeys()).containsExactly(
                "QUIZ:" + testQuiz.getId() + ":SUB:" + token + ":Q:" + testQuestion.getId());
        assertThat(state.normalizedScores()).containsExactly(1.0);
    }

    private void assertResponseHasAuthoritativeGrading(QuizAttemptResultResponse result) {
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.score()).isEqualTo(100.0);
        assertThat(result.correctAnswers()).isEqualTo(1);
        assertThat(result.perQuestionResults()).hasSize(1);
        assertThat(result.perQuestionResults().stream()
            .filter(QuizAttemptPerQuestionResult::isCorrect)
            .count())
            .isEqualTo((long) result.correctAnswers());
        assertThat(result.perQuestionResults().stream().mapToDouble(QuizAttemptPerQuestionResult::pointsEarned).sum())
                .isEqualTo(1.0);
        assertThat(result.perQuestionResults().get(0).isCorrect()).isTrue();
        assertThat(result.perQuestionResults().get(0).pointsEarned()).isEqualTo(1.0);
    }

    private void assertAttemptMasteryApplied() {
        transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(testAttempt.getId()).orElseThrow();
            assertThat(attempt.getMasteryApplied()).isTrue();
            return null;
        });
    }

    private FixtureEvidenceState readFixtureEvidenceState() {
        return transactionTemplate.execute(status -> {
            StudentConceptMastery mastery = masteryRepository
                    .findByUserIdAndConceptId(testUser.getId(), testConcept.getId()).orElseThrow();
            List<StudentLearningEvent> events = eventRepository
                    .findByUserIdAndConceptIdOrderByOccurredAtAsc(testUser.getId(), testConcept.getId());
            return new FixtureEvidenceState(
                    mastery.getEvidenceCount(),
                    events.stream().map(StudentLearningEvent::getId).toList(),
                    events.stream().map(StudentLearningEvent::getIdempotencyKey).toList(),
                    events.stream().map(StudentLearningEvent::getNormalizedScore).toList());
        });
    }

    private record FixtureEvidenceState(
            Integer evidenceCount,
            List<Long> eventIds,
            List<String> idempotencyKeys,
            List<Double> normalizedScores) {}

    private record PersistedSubmissionState(
            String status,
            Double score,
            Integer totalQuestions,
            Integer correctAnswers,
            LocalDateTime completedAt,
            String submissionToken,
            String submissionHash,
            Boolean masteryApplied,
            Boolean answerCorrect,
            Double pointsEarned,
            Boolean selected) {}

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        AdjustableClock ei16AdjustableClock() {
            return new AdjustableClock(Instant.now(), ZoneId.systemDefault());
        }
    }

    static class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        AdjustableClock(Instant initialInstant, ZoneId zone) {
            this.instant = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        void setInstant(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new AdjustableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
