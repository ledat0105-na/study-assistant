package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@Import(ExamReadinessMySQLIntegrationTest.TestClockConfiguration.class)
class ExamReadinessMySQLIntegrationTest {
    @Autowired private ExamReadinessService readinessService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AdjustableClock testClock;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizQuestionOptionRepository questionOptionRepository;
    @Autowired private QuizQuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;
    @Autowired private QuizQuestionConceptRepository questionConceptRepository;
    @Autowired private QuizQuestionSourceRepository questionSourceRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository answerRepository;
    @Autowired private QuizAttemptAnswerOptionRepository answerOptionRepository;
    @Autowired private QuizAttemptAnswerAcceptedAnswerRepository answerAcceptedAnswerRepository;
    @Autowired private QuizAttemptAnswerConceptRepository answerConceptRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private StudentLearningEventRepository learningEventRepository;

    private User user;
    private User otherUser;
    private Notebook notebook;
    private Notebook otherNotebook;
    private Document activeDocument;
    private Document staleDocument;
    private DocumentChunk activeChunk;
    private DocumentChunk staleChunk;
    private Quiz archivedQuiz;
    private final List<Concept> activeConcepts = new ArrayList<>();
    private Concept staleConcept;
    private Concept orphanConcept;
    private final List<QuizAttempt> fixtureAttempts = new ArrayList<>();
    private final List<QuizQuestion> fixtureQuestions = new ArrayList<>();
    private final List<QuizAttemptAnswer> fixtureAnswers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClock.setInstant(Instant.parse("2026-09-28T12:00:00Z"));
        transactionTemplate.execute(status -> {
            user = new User();
            user.setUsername("readiness_" + System.nanoTime());
            user.setPassword("test");
            user.setEmail("readiness_" + System.nanoTime() + "@example.test");
            user = userRepository.save(user);

            otherUser = new User();
            otherUser.setUsername("readiness_other_" + System.nanoTime());
            otherUser.setPassword("test");
            otherUser.setEmail("readiness_other_" + System.nanoTime() + "@example.test");
            otherUser = userRepository.save(otherUser);

            notebook = new Notebook();
            notebook.setTitle("Readiness fixture");
            notebook.setUser(user);
            notebook = notebookRepository.save(notebook);

            otherNotebook = new Notebook();
            otherNotebook.setTitle("Other readiness fixture");
            otherNotebook.setUser(otherUser);
            otherNotebook = notebookRepository.save(otherNotebook);

            activeDocument = saveDocument(user, notebook, "READY", "active-v1");
            activeChunk = saveChunk(activeDocument, "active-v1", 1);
            staleDocument = saveDocument(user, notebook, "READY", "active-v2");
            staleChunk = saveChunk(staleDocument, "stale-v1", 2);

            for (int index = 1; index <= 3; index++) {
                Concept concept = saveConcept(notebook, "Active concept " + index);
                activeConcepts.add(concept);
                saveConceptSource(concept, activeChunk);
            }
            staleConcept = saveConcept(notebook, "Stale concept");
            saveConceptSource(staleConcept, staleChunk);
            orphanConcept = saveConcept(notebook, "Orphan concept");

            archivedQuiz = new Quiz();
            archivedQuiz.setNotebook(notebook);
            archivedQuiz.setTitle("Archived historical exam");
            archivedQuiz.setMode(QuizMode.EXAM);
            archivedQuiz.setStatus(QuizStatus.ARCHIVED);
            archivedQuiz.setDeletedAt(LocalDateTime.now(testClock).minusDays(1));
            archivedQuiz = quizRepository.save(archivedQuiz);

            LocalDateTime base = LocalDateTime.now(testClock);
            QuizAttempt newest80 = createAttempt(QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", 80.0,
                    base.minusDays(1), base.minusDays(1), List.of(true, false, false), true);
            createAttempt(QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", 70.0,
                    base.minusDays(2), base.minusDays(2), List.of(false, true, false));
            createAttempt(QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", 60.0,
                    base.minusDays(3), base.minusDays(3), List.of(false, true, false));
            createAttempt(QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", 99.0,
                    base.minusDays(4), base.minusDays(31), List.of(false, false, false));
            createAttempt(QuizMode.PRACTICE, "COMPLETED", "QUIZ_SCORING_V1", 100.0,
                    base.minusHours(5), base.minusHours(5), List.of(false, false, false));
            createAttempt(QuizMode.EXAM, "EXPIRED", "QUIZ_SCORING_V1", 100.0,
                    base.minusHours(4), base.minusHours(4), List.of(true, true, true));
            createAttempt(QuizMode.EXAM, "IN_PROGRESS", "QUIZ_SCORING_V1", 100.0,
                    base.minusHours(3), base.minusHours(3), List.of(true, true, true));
            createAttempt(QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V0", 100.0,
                    base.minusHours(2), base.minusDays(31), List.of(true, true, true));
            createAttempt(QuizMode.PRACTICE, "COMPLETED", "QUIZ_SCORING_V1", null,
                    base.minusDays(2), base.minusDays(30), List.of(true, true, false));
            createAttempt(QuizMode.PRACTICE, "COMPLETED", "QUIZ_SCORING_V1", null,
                    base.minusDays(2), base.plusSeconds(1), List.of(false, false, false));

            StudentConceptMastery firstMastery = new StudentConceptMastery(user, activeConcepts.get(0), 0.8, 2);
            masteryRepository.save(firstMastery);
            StudentConceptMastery secondMastery = new StudentConceptMastery(user, activeConcepts.get(1), 0.6, 1);
            masteryRepository.save(secondMastery);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (user == null) return;
        transactionTemplate.execute(status -> {
            for (Concept concept : activeConcepts) {
                learningEventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(user.getId(), concept.getId())
                        .forEach(learningEventRepository::delete);
                masteryRepository.findByUserIdAndConceptId(user.getId(), concept.getId())
                        .ifPresent(masteryRepository::delete);
            }
            fixtureAnswers.forEach(answer -> {
                answerConceptRepository.findByAttemptAnswerId(answer.getId()).forEach(answerConceptRepository::delete);
                answerOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(answer.getId()).forEach(answerOptionRepository::delete);
                answerAcceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(answer.getId())
                        .forEach(answerAcceptedAnswerRepository::delete);
                answerRepository.delete(answer);
            });
            fixtureAttempts.forEach(attemptRepository::delete);
            for (QuizQuestion question : fixtureQuestions) {
                questionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(question.getId()).forEach(questionOptionRepository::delete);
                questionAcceptedAnswerRepository.findByQuestionIdOrderByAcceptedOrderAsc(question.getId())
                        .forEach(questionAcceptedAnswerRepository::delete);
                questionConceptRepository.findByQuestionId(question.getId()).forEach(questionConceptRepository::delete);
                questionSourceRepository.findByQuestionIdOrderBySourceOrderAsc(question.getId()).forEach(questionSourceRepository::delete);
                questionRepository.delete(question);
            }
            if (archivedQuiz != null) quizRepository.delete(archivedQuiz);
            for (Concept concept : activeConcepts) {
                conceptSourceRepository.findByConceptId(concept.getId()).forEach(conceptSourceRepository::delete);
                conceptRepository.delete(concept);
            }
            if (staleConcept != null) {
                conceptSourceRepository.findByConceptId(staleConcept.getId()).forEach(conceptSourceRepository::delete);
                conceptRepository.delete(staleConcept);
            }
            if (orphanConcept != null) conceptRepository.delete(orphanConcept);
            if (activeChunk != null) documentChunkRepository.delete(activeChunk);
            if (staleChunk != null) documentChunkRepository.delete(staleChunk);
            if (activeDocument != null) documentRepository.delete(activeDocument);
            if (staleDocument != null) documentRepository.delete(staleDocument);
            if (notebook != null) notebookRepository.delete(notebook);
            if (otherNotebook != null) notebookRepository.delete(otherNotebook);
            if (user != null) userRepository.delete(user);
            if (otherUser != null) userRepository.delete(otherUser);
            return null;
        });
    }

    @Test
    void activeGroundingTopThreeReadinessAndWeakEvidenceAreAuthoritative() {
        assertThat(conceptRepository.findActivelyGroundedByNotebookAndUser(notebook.getId(), user.getId()))
                .extracting(Concept::getId)
                .containsExactlyInAnyOrderElementsOf(activeConcepts.stream().map(Concept::getId).toList());

        ExamReadinessResponse response = readinessService.getNotebookReadiness(user.getId(), notebook.getId());

        assertThat(response.calculationVersion()).isEqualTo("READINESS_V1");
        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.eligibleExamAttemptCount()).isEqualTo(3);
        assertThat(response.eligibleConceptCount()).isEqualTo(3);
        assertThat(response.examPerformance()).isCloseTo(0.73, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(response.masteryComponent()).isCloseTo(1.4 / 3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(response.coverageComponent()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(response.coveredConceptCount()).isEqualTo(2);
        assertThat(response.readinessScore()).isEqualTo(63);
        assertThat(response.weakConcepts()).extracting(WeakConceptDto::conceptId)
                .containsExactly(activeConcepts.get(2).getId(), activeConcepts.get(0).getId());
        assertThat(response.weakConcepts()).extracting(WeakConceptDto::evidenceCount)
            .containsExactly(6L, 5L);

        assertThrows(java.util.NoSuchElementException.class,
            () -> readinessService.getNotebookReadiness(otherUser.getId(), notebook.getId()));
    }

    private Document saveDocument(User owner, Notebook targetNotebook, String status, String activeVersion) {
        Document document = new Document();
        document.setUser(owner);
        document.setNotebook(targetNotebook);
        document.setFileName("readiness-fixture.txt");
        document.setFilePath("test-fixtures/readiness-fixture.txt");
        document.setStatus(status);
        document.setActiveIndexVersion(activeVersion);
        return documentRepository.save(document);
    }

    private DocumentChunk saveChunk(Document document, String indexVersion, int chunkIndex) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent("Readiness grounding fixture text.");
        chunk.setTokenCount(4);
        chunk.setIndexVersion(indexVersion);
        return documentChunkRepository.save(chunk);
    }

    private Concept saveConcept(Notebook targetNotebook, String name) {
        Concept concept = new Concept();
        concept.setNotebook(targetNotebook);
        concept.setCanonicalName(name);
        concept.setNormalizedName(name.toLowerCase());
        return conceptRepository.save(concept);
    }

    private void saveConceptSource(Concept concept, DocumentChunk chunk) {
        ConceptSource source = new ConceptSource();
        source.setConcept(concept);
        source.setDocumentChunk(chunk);
        source.setEvidenceText("Readiness evidence.");
        conceptSourceRepository.save(source);
    }

    private QuizAttempt createAttempt(QuizMode mode, String status, String scoringVersion, Double score,
                                      LocalDateTime completedAt, LocalDateTime gradedAt, List<Boolean> outcomes) {
        return createAttempt(mode, status, scoringVersion, score, completedAt, gradedAt, outcomes, false);
    }

    private QuizAttempt createAttempt(QuizMode mode, String status, String scoringVersion, Double score,
                                      LocalDateTime completedAt, LocalDateTime gradedAt, List<Boolean> outcomes,
                                      boolean includeMultiConceptAnswer) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(user);
        attempt.setQuiz(archivedQuiz);
        attempt.setStatus(status);
        attempt.setModeSnapshot(mode);
        attempt.setScoringVersion(scoringVersion);
        attempt.setScore(score);
        attempt.setCompletedAt(completedAt);
        attempt.setStartedAt(completedAt == null ? LocalDateTime.now(testClock).minusHours(1) : completedAt.minusMinutes(30));
        attempt = attemptRepository.save(attempt);
        fixtureAttempts.add(attempt);

        for (int index = 0; index < activeConcepts.size(); index++) {
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(archivedQuiz);
        question.setQuestionOrder(fixtureQuestions.size() + 1);
        question.setQuestionType(QuizQuestionType.MCQ_SINGLE);
        question.setPoints(1.0);
        question.setQuestion("Snapshot question");
        question = questionRepository.save(question);
        fixtureQuestions.add(question);

        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.setAttempt(attempt);
        answer.setQuestion(question);
        answer.setQuestionOrder(question.getQuestionOrder());
        answer.setQuestionTextSnapshot("Attempt snapshot question");
        answer.setQuestionTypeSnapshot(QuizQuestionType.MCQ_SINGLE);
        answer.setPointsPossible(1.0);
        answer.setPointsEarned(Boolean.TRUE.equals(outcomes.get(index)) ? 1.0 : 0.0);
        answer.setIsCorrect(outcomes.get(index));
        answer.setGradedAt(gradedAt);
        answer = answerRepository.save(answer);
        fixtureAnswers.add(answer);

            QuizAttemptAnswerConcept mapping = new QuizAttemptAnswerConcept();
            mapping.setAttemptAnswer(answer);
            mapping.setConcept(activeConcepts.get(index));
            answerConceptRepository.save(mapping);
            if (includeMultiConceptAnswer && index == 1) {
                QuizAttemptAnswerConcept additionalMapping = new QuizAttemptAnswerConcept();
                additionalMapping.setAttemptAnswer(answer);
                additionalMapping.setConcept(activeConcepts.get(2));
                answerConceptRepository.save(additionalMapping);
            }
        }
        return attempt;
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        AdjustableClock readinessTestClock() {
            return new AdjustableClock(Instant.parse("2026-09-28T12:00:00Z"), ZoneId.systemDefault());
        }
    }

    static class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        AdjustableClock(Instant instant, ZoneId zone) {
            this.instant = new AtomicReference<>(instant);
            this.zone = zone;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId newZone) { return new AdjustableClock(instant(), newZone); }
        @Override public Instant instant() { return instant.get(); }
        void setInstant(Instant value) { instant.set(value); }
    }
}
