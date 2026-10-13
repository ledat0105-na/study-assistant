package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.domain.quiz.attempt.QuizAttemptResponse;
import com.example.studyassistant.domain.quiz.generation.GroundedQuizGenerationCommand;
import com.example.studyassistant.domain.quiz.generation.GroundedQuizGenerationResult;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptResultResponse;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptSubmitAnswerPayload;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptSubmitCommand;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@Import(Phase7ExamIntelligenceE2EMySQLIntegrationTest.TestClockConfiguration.class)
class Phase7ExamIntelligenceE2EMySQLIntegrationTest {
    private static final String GENERATION_RESPONSE = """
            {"questions":[
              {"type":"MCQ_SINGLE","questionText":"Grounded first question","points":2.0,"explanation":"First rationale","sources":["S1"],"options":[{"text":"Correct one","isCorrect":true},{"text":"Wrong one","isCorrect":false}]},
              {"type":"MCQ_SINGLE","questionText":"Grounded second question","points":1.0,"explanation":"Second rationale","sources":["S1"],"options":[{"text":"Correct two","isCorrect":true},{"text":"Wrong two","isCorrect":false}]},
              {"type":"SHORT_ANSWER","questionText":"Grounded short question","points":1.0,"explanation":"Short rationale","sources":["S1"],"acceptedAnswers":["Canonical answer"]}
            ]}
            """;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AdjustableClock testClock;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizQuestionOptionRepository questionOptionRepository;
    @Autowired private QuizQuestionAcceptedAnswerRepository questionAcceptedRepository;
    @Autowired private QuizQuestionSourceRepository questionSourceRepository;
    @Autowired private QuizQuestionConceptRepository questionConceptRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository answerRepository;
    @Autowired private QuizAttemptAnswerOptionRepository answerOptionRepository;
    @Autowired private QuizAttemptAnswerAcceptedAnswerRepository answerAcceptedRepository;
    @Autowired private QuizAttemptAnswerConceptRepository answerConceptRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private StudentLearningEventRepository learningEventRepository;
    @Autowired private QuizResultRepository quizResultRepository;

    @MockitoBean private LlmProvider llmProvider;

    private MockMvc mockMvc;
    private User owner;
    private User foreignUser;
    private Notebook notebook;
    private Document document;
    private DocumentChunk activeChunk;
    private DocumentChunk staleChunk;
    private Concept conceptA;
    private Concept conceptB;
    private Concept staleConcept;
    private Concept orphanConcept;
    private Quiz quiz;
    private final List<QuizQuestion> questions = new ArrayList<>();
        private MockHttpSession ownerSession;
        private MockHttpSession foreignSession;
    private final AtomicBoolean providerCalledInsideTransaction = new AtomicBoolean(true);
    private final AtomicReference<String> capturedPrompt = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
                objectMapper.registerModule(new JavaTimeModule());
        testClock.setInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        ownerSession = new MockHttpSession();
        foreignSession = new MockHttpSession();
        transactionTemplate.execute(status -> {
            owner = saveUser("phase7-owner");
            foreignUser = saveUser("phase7-foreign");
            notebook = new Notebook();
            notebook.setTitle("Phase 7 E2E notebook");
            notebook.setUser(owner);
            notebook = notebookRepository.save(notebook);

            Document savedDocument = new Document();
            savedDocument.setUser(owner);
            savedDocument.setNotebook(notebook);
            savedDocument.setFileName("phase7-e2e.txt");
            savedDocument.setFilePath("fixture-only/phase7-e2e.txt");
            savedDocument.setStatus("READY");
            savedDocument.setActiveIndexVersion("V2");
            savedDocument.setExtractedText("RAW EXTRACTED TEXT MUST NEVER ENTER GENERATION PROMPT");
            document = documentRepository.save(savedDocument);

            activeChunk = saveChunk(document, 1, "V2", "ACTIVE V2 SOURCE CONTENT");
            staleChunk = saveChunk(document, 2, "V1", "STALE V1 SOURCE CONTENT MUST NOT BE USED");

            conceptA = saveConcept("Active concept A");
            conceptB = saveConcept("Active concept B");
            staleConcept = saveConcept("Stale-only concept");
            orphanConcept = saveConcept("Orphan concept");
            saveConceptSource(conceptA, activeChunk);
            saveConceptSource(conceptA, staleChunk);
            saveConceptSource(conceptB, activeChunk);
            saveConceptSource(staleConcept, staleChunk);

            return null;
        });
        ownerSession.setAttribute("userId", owner.getId());
        foreignSession.setAttribute("userId", foreignUser.getId());

        doAnswer(invocation -> {
            providerCalledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            capturedPrompt.set(invocation.getArgument(0));
            return GENERATION_RESPONSE;
        }).when(llmProvider).generate(anyString(), anyString(), anyDouble());
    }

    @AfterEach
    void tearDown() {
        if (owner == null) return;
        transactionTemplate.execute(status -> {
            for (Concept concept : fixtureConcepts()) {
                if (concept == null) continue;
                learningEventRepository.findByUserIdAndConceptIdOrderByOccurredAtAsc(owner.getId(), concept.getId())
                        .forEach(learningEventRepository::delete);
                masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId())
                        .ifPresent(masteryRepository::delete);
            }
            List<Quiz> fixtureQuizzes = quiz != null
                    ? List.of(quiz)
                    : document == null ? List.of() : quizRepository.findByDocumentId(document.getId());
            for (Quiz fixtureQuiz : fixtureQuizzes) {
                List<QuizAttempt> fixtureAttempts = attemptRepository
                        .findByUserIdAndQuizIdOrderByStartedAtDesc(owner.getId(), fixtureQuiz.getId());
                for (QuizAttempt fixtureAttempt : fixtureAttempts) {
                    List<QuizAttemptAnswer> fixtureAnswers = answerRepository
                            .findByAttemptIdOrderByQuestionOrderAsc(fixtureAttempt.getId());
                    for (QuizAttemptAnswer answer : fixtureAnswers) {
                        answerConceptRepository.findByAttemptAnswerId(answer.getId())
                                .forEach(answerConceptRepository::delete);
                        answerOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(answer.getId())
                                .forEach(answerOptionRepository::delete);
                        answerAcceptedRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(answer.getId())
                                .forEach(answerAcceptedRepository::delete);
                        answerRepository.delete(answer);
                    }
                    attemptRepository.delete(fixtureAttempt);
                }

                quizResultRepository.findByQuizId(fixtureQuiz.getId()).forEach(quizResultRepository::delete);
                for (QuizQuestion question : questionRepository.findByQuizId(fixtureQuiz.getId())) {
                    questionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(question.getId())
                            .forEach(questionOptionRepository::delete);
                    questionAcceptedRepository.findByQuestionIdOrderByAcceptedOrderAsc(question.getId())
                            .forEach(questionAcceptedRepository::delete);
                    questionSourceRepository.findByQuestionIdOrderBySourceOrderAsc(question.getId())
                            .forEach(questionSourceRepository::delete);
                    questionConceptRepository.findByQuestionId(question.getId())
                            .forEach(questionConceptRepository::delete);
                    questionRepository.delete(question);
                }
                                quizRepository.delete(fixtureQuiz);
            }
            for (Concept concept : fixtureConcepts()) {
                if (concept == null) continue;
                conceptSourceRepository.findByConceptId(concept.getId()).forEach(conceptSourceRepository::delete);
                conceptRepository.delete(concept);
            }
            if (activeChunk != null) chunkRepository.delete(activeChunk);
            if (staleChunk != null) chunkRepository.delete(staleChunk);
            if (document != null) documentRepository.delete(document);
            if (notebook != null) notebookRepository.delete(notebook);
            if (owner != null) userRepository.delete(owner);
            if (foreignUser != null) userRepository.delete(foreignUser);
            return null;
        });
    }

    @Test
    void groundedQuizToReadinessLifecycleIsDeterministicAndReplaySafe() throws Exception {
        long quizCountBeforeLegacyGet = quizRepository.findByDocumentId(document.getId()).size();
        mockMvc.perform(get("/api/quizzes/document/{docId}", document.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(0))
                .andExpect(jsonPath("$.questions").isEmpty());
        assertThat(quizRepository.findByDocumentId(document.getId()).size()).isEqualTo(quizCountBeforeLegacyGet);
        verify(llmProvider, times(0)).generate(anyString(), anyString(), anyDouble());

        GroundedQuizGenerationCommand generationCommand = new GroundedQuizGenerationCommand(
                foreignUser.getId(), QuizScopeType.DOCUMENT, notebook.getId(), document.getId(), null, null,
                QuizMode.EXAM, 3, List.of(QuizQuestionType.MCQ_SINGLE, QuizQuestionType.SHORT_ANSWER),
                "Phase 7 generated exam", 1800, 60.0);
        MvcResult generationResult = mockMvc.perform(post("/api/quizzes/generate")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(generationCommand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(3))
                .andReturn();
        GroundedQuizGenerationResult generated = objectMapper.readValue(
                generationResult.getResponse().getContentAsByteArray(), GroundedQuizGenerationResult.class);
        quiz = quizRepository.findById(generated.quizId()).orElseThrow();
        questions.addAll(questionRepository.findByQuizId(quiz.getId()));

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
        assertThat(quiz.getNotebook().getId()).isEqualTo(notebook.getId());
        assertThat(generated.questions()).allSatisfy(result ->
                assertThat(result.sourceChunkIds()).containsExactly(activeChunk.getId()));
        assertThat(questionSourceRepository.findByQuestionIdOrderBySourceOrderAsc(questions.get(0).getId()))
                .extracting(source -> source.getDocumentChunk().getId()).containsExactly(activeChunk.getId());
        assertThat(chunkRepository.findById(staleChunk.getId())).isPresent();
        assertThat(conceptSourceRepository.existsActiveGrounding(conceptA.getId(), notebook.getId(), owner.getId())).isTrue();
        assertThat(conceptSourceRepository.existsActiveGrounding(conceptB.getId(), notebook.getId(), owner.getId())).isTrue();
        assertThat(conceptRepository.findActivelyGroundedByNotebookAndUser(notebook.getId(), owner.getId()))
                .extracting(Concept::getId).containsExactlyInAnyOrder(conceptA.getId(), conceptB.getId());
        assertThat(providerCalledInsideTransaction.get()).isFalse();
        assertThat(capturedPrompt.get()).contains("ACTIVE V2 SOURCE CONTENT")
                .doesNotContain("STALE V1 SOURCE CONTENT", "RAW EXTRACTED TEXT");
        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());

        transactionTemplate.execute(status -> {
            Quiz managedQuiz = quizRepository.findById(quiz.getId()).orElseThrow();
            managedQuiz.setStatus(QuizStatus.PUBLISHED);
            quizRepository.save(managedQuiz);
            for (QuizQuestion question : questions) {
                for (Concept concept : List.of(conceptA, conceptB)) {
                    QuizQuestionConcept mapping = new QuizQuestionConcept();
                    mapping.setQuestion(question);
                    mapping.setConcept(concept);
                    questionConceptRepository.save(mapping);
                }
            }
            return null;
        });

        seedHistoricalAttempt(80.0, LocalDateTime.now(testClock).minusDays(2));
        seedHistoricalAttempt(60.0, LocalDateTime.now(testClock).minusDays(3));

        mockMvc.perform(get("/api/quizzes/{quizId}/questions", quiz.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$[0].explanation").doesNotExist());

        mockMvc.perform(post("/api/quizzes/{quizId}/start-attempt", quiz.getId()).session(foreignSession))
                .andExpect(status().isBadRequest());
        MvcResult startResult = mockMvc.perform(post("/api/quizzes/{quizId}/start-attempt", quiz.getId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EXAM"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(1800))
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.questions[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist())
                .andReturn();
        QuizAttemptResponse started = objectMapper.readValue(
                startResult.getResponse().getContentAsByteArray(), QuizAttemptResponse.class);
        assertThat(started.questions()).hasSize(3);
        QuizAttempt attempt = attemptRepository.findById(started.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(attempt.getUser().getId()).isEqualTo(owner.getId());
        assertThat(attempt.getStartedAt()).isNotNull();
        assertThat(attempt.getModeSnapshot()).isEqualTo(QuizMode.EXAM);
        assertThat(attempt.getTimeLimitSecondsSnapshot()).isEqualTo(1800);
        assertThat(attempt.getPassThresholdSnapshot()).isEqualTo(60.0);
        assertThat(attempt.getScoringVersion()).isEqualTo("QUIZ_SCORING_V1");
        assertThat(attempt.getMasteryApplied()).isFalse();

        List<QuizAttemptAnswer> snapshots = answerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId());
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots).extracting(QuizAttemptAnswer::getQuestionTextSnapshot)
                .containsExactly("Grounded first question", "Grounded second question", "Grounded short question");
        assertThat(snapshots).extracting(QuizAttemptAnswer::getPointsPossible).containsExactly(2.0, 1.0, 1.0);
        for (QuizAttemptAnswer snapshot : snapshots) {
            assertThat(answerConceptRepository.findByAttemptAnswerId(snapshot.getId()))
                    .extracting(mapping -> mapping.getConcept().getId())
                    .containsExactlyInAnyOrder(conceptA.getId(), conceptB.getId());
        }
        List<QuizAttemptAnswerOption> optionSnapshots = answerOptionRepository
                .findByAttemptAnswerIdOrderByOptionOrderAsc(snapshots.get(0).getId());
        assertThat(optionSnapshots).extracting(QuizAttemptAnswerOption::getIsCorrectSnapshot).containsExactly(true, false);
        assertThat(answerAcceptedRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(snapshots.get(2).getId()))
                .extracting(QuizAttemptAnswerAcceptedAnswer::getAcceptedTextSnapshot).containsExactly("canonical answer");

        transactionTemplate.execute(status -> {
            for (QuizQuestion question : questions) {
                question.setQuestion("MUTATED MASTER QUESTION " + question.getQuestionOrder());
                questionRepository.save(question);
                for (QuizQuestionOption option : questionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(question.getId())) {
                    option.setIsCorrect(!option.getIsCorrect());
                    option.setOptionText("MUTATED MASTER OPTION");
                    questionOptionRepository.save(option);
                }
                for (QuizQuestionAcceptedAnswer accepted : questionAcceptedRepository.findByQuestionIdOrderByAcceptedOrderAsc(question.getId())) {
                    accepted.setAcceptedText("mutated master accepted answer");
                    questionAcceptedRepository.save(accepted);
                }
                questionConceptRepository.findByQuestionId(question.getId()).forEach(questionConceptRepository::delete);
            }
            return null;
        });

        int quizResultCountBefore = quizResultRepository.findByQuizId(quiz.getId()).size();
        assertThat(quizResultCountBefore).isZero();
        String token = UUID.randomUUID().toString();
        QuizAttemptSubmitCommand submitCommand = new QuizAttemptSubmitCommand(
                foreignUser.getId(), -999L, token, List.of(
                new QuizAttemptSubmitAnswerPayload(1, List.of(1), null),
                new QuizAttemptSubmitAnswerPayload(2, List.of(2), null),
                new QuizAttemptSubmitAnswerPayload(3, null, "CANONICAL ANSWER")));
        byte[] submitBody = objectMapper.writeValueAsBytes(submitCommand);
        MvcResult submitResult = mockMvc.perform(post("/api/quiz-attempts/{attemptId}/submit", attempt.getId())
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(submitBody))
                .andExpect(status().isOk()).andReturn();
        QuizAttemptResultResponse first = objectMapper.readValue(
                submitResult.getResponse().getContentAsByteArray(), QuizAttemptResultResponse.class);
        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.score()).isEqualTo(75.0);
        assertThat(first.correctAnswers()).isEqualTo(2);
        assertThat(first.perQuestionResults()).extracting(result -> result.pointsEarned())
                .containsExactly(2.0, 0.0, 1.0);
        assertThat(first.perQuestionResults()).extracting(result -> result.isCorrect())
                .containsExactly(true, false, true);
        assertThat(first.completedAt()).isEqualTo(LocalDateTime.now(testClock).truncatedTo(ChronoUnit.SECONDS));

        QuizAttempt persistedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(persistedAttempt.getStatus()).isEqualTo("COMPLETED");
        assertThat(persistedAttempt.getSubmissionToken()).isEqualTo(token);
        assertThat(persistedAttempt.getSubmissionHash()).hasSize(64);
        assertThat(persistedAttempt.getMasteryApplied()).isTrue();
        assertThat(answerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId()))
                .extracting(QuizAttemptAnswer::getIsCorrect).containsExactly(true, false, true);
        assertThat(answerOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(snapshots.get(0).getId()))
                .extracting(QuizAttemptAnswerOption::getStudentSelected).containsExactly(true, false);
        assertThat(answerOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(snapshots.get(1).getId()))
                .extracting(QuizAttemptAnswerOption::getStudentSelected).containsExactly(false, true);
        assertThat(quizResultRepository.findByQuizId(quiz.getId())).hasSize(quizResultCountBefore);

        List<FixtureEvidenceState> beforeReplay = evidenceState();
        assertThat(beforeReplay).hasSize(2);
        for (FixtureEvidenceState evidence : beforeReplay) {
            assertThat(evidence.evidenceCount()).isEqualTo(3);
            assertThat(evidence.events()).hasSize(3);
                        assertThat(evidence.events()).extracting(EventSnapshot::normalizedScore)
                                        .containsExactlyInAnyOrder(1.0, 0.0, 1.0);
        }
        assertThat(snapshots).hasSize(3);

        MvcResult replayResult = mockMvc.perform(post("/api/quiz-attempts/{attemptId}/submit", attempt.getId())
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(submitBody))
                .andExpect(status().isOk()).andReturn();
        QuizAttemptResultResponse replay = objectMapper.readValue(
                replayResult.getResponse().getContentAsByteArray(), QuizAttemptResultResponse.class);
        assertThat(replay).isEqualTo(first);
        assertThat(answerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId())).hasSize(3);
        assertThat(evidenceState()).isEqualTo(beforeReplay);
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getMasteryApplied()).isTrue();
        assertThat(quizResultRepository.findByQuizId(quiz.getId())).hasSize(quizResultCountBefore);

        transactionTemplate.execute(status -> {
            Quiz managedQuiz = quizRepository.findById(quiz.getId()).orElseThrow();
            managedQuiz.setStatus(QuizStatus.ARCHIVED);
            quizRepository.save(managedQuiz);
            return null;
        });

        MvcResult readinessResult = mockMvc.perform(get("/api/notebooks/{notebookId}/exam-readiness", notebook.getId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationVersion").value("READINESS_V1"))
                .andExpect(jsonPath("$.insufficientEvidence").value(false))
                .andReturn();
        ExamReadinessResponse readiness = objectMapper.readValue(
                readinessResult.getResponse().getContentAsByteArray(), ExamReadinessResponse.class);
        List<QuizAttempt> eligible = attemptRepository.findEligibleReadinessExamAttempts(
                owner.getId(), notebook.getId(), "COMPLETED", QuizMode.EXAM, "QUIZ_SCORING_V1",
                org.springframework.data.domain.PageRequest.of(0, 3));
        assertThat(eligible).hasSize(3);
        assertThat(eligible.get(0).getId()).isEqualTo(attempt.getId());
        assertThat(readiness.eligibleExamAttemptCount()).isEqualTo(3);
        assertThat(readiness.eligibleConceptCount()).isEqualTo(2);
        assertThat(readiness.examPerformance()).isCloseTo(
                0.50 * (first.score() / 100.0) + 0.30 * 0.80 + 0.20 * 0.60,
                org.assertj.core.data.Offset.offset(1e-12));

        double expectedMastery = activeMasteryMean();
        assertThat(readiness.masteryComponent()).isCloseTo(expectedMastery,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(readiness.coverageComponent()).isEqualTo(1.0);
        int expectedReadiness = (int) Math.round(100.0 * (0.50 * readiness.examPerformance()
                + 0.35 * expectedMastery + 0.15));
        assertThat(readiness.readinessScore()).isEqualTo(expectedReadiness);
        assertThat(readiness.weakConcepts()).extracting(WeakConceptDto::conceptId)
                .containsExactlyInAnyOrder(conceptA.getId(), conceptB.getId());
        assertThat(readiness.weakConcepts()).extracting(WeakConceptDto::evidenceCount)
                .containsExactly(9L, 9L);
        assertThat(readiness.weakConcepts()).extracting(WeakConceptDto::accuracy)
                .containsOnly(2.0 / 9.0);
        assertThat(readiness.weakConcepts()).extracting(WeakConceptDto::masteryLevel)
                .containsExactlyInAnyOrderElementsOf(activeMasteryLevels());

        mockMvc.perform(get("/api/notebooks/{notebookId}/exam-readiness", notebook.getId())
                        .session(foreignSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/quiz-attempts/{attemptId}/submit", attempt.getId())
                        .session(foreignSession).contentType(MediaType.APPLICATION_JSON).content(submitBody))
                .andExpect(status().isNotFound());

        MvcResult legacyGet = mockMvc.perform(get("/api/quizzes/document/{docId}", document.getId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(quiz.getId()))
                .andReturn();
        JsonNode legacyQuestions = objectMapper.readTree(legacyGet.getResponse().getContentAsByteArray())
                .path("questions");
        assertThat(legacyQuestions.get(0).has("correctAnswer")).isFalse();
        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());
        mockMvc.perform(delete("/api/quizzes/document/{docId}", document.getId()).session(ownerSession))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId()).session(ownerSession))
                .andExpect(status().isMethodNotAllowed());
    }

    private User saveUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "_" + UUID.randomUUID());
        user.setEmail(prefix + "_" + UUID.randomUUID() + "@example.test");
        user.setPassword("test-only");
        return userRepository.save(user);
    }

    private DocumentChunk saveChunk(Document ownerDocument, int index, String version, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(ownerDocument);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setTokenCount(5);
        chunk.setIndexVersion(version);
        return chunkRepository.save(chunk);
    }

    private Concept saveConcept(String name) {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName(name);
        concept.setNormalizedName(name.toLowerCase().replace(' ', '-'));
        return conceptRepository.save(concept);
    }

    private void saveConceptSource(Concept concept, DocumentChunk chunk) {
        ConceptSource source = new ConceptSource();
        source.setConcept(concept);
        source.setDocumentChunk(chunk);
        source.setEvidenceText("Exact fixture grounding evidence.");
        conceptSourceRepository.save(source);
    }

    private void seedHistoricalAttempt(double score, LocalDateTime completedAt) {
        QuizAttempt historical = new QuizAttempt();
        historical.setUser(owner);
        historical.setQuiz(quiz);
        historical.setStatus("COMPLETED");
        historical.setModeSnapshot(QuizMode.EXAM);
        historical.setScoringVersion("QUIZ_SCORING_V1");
        historical.setScore(score);
        historical.setCompletedAt(completedAt);
        historical.setStartedAt(completedAt.minusMinutes(30));
        historical.setTotalQuestions(3);
        historical.setCorrectAnswers(0);
        historical.setMasteryApplied(true);
        historical = attemptRepository.save(historical);

        for (int index = 0; index < questions.size(); index++) {
            QuizAttemptAnswer answer = new QuizAttemptAnswer();
            answer.setAttempt(historical);
            answer.setQuestion(questions.get(index));
            answer.setQuestionOrder(questions.get(index).getQuestionOrder());
            answer.setQuestionTextSnapshot(questions.get(index).getQuestion());
            answer.setQuestionTypeSnapshot(questions.get(index).getQuestionType());
            answer.setPointsPossible(questions.get(index).getPoints());
            answer.setPointsEarned(0.0);
            answer.setIsCorrect(false);
            answer.setGradedAt(completedAt);
            answer = answerRepository.save(answer);
            for (Concept concept : List.of(conceptA, conceptB)) {
                QuizAttemptAnswerConcept mapping = new QuizAttemptAnswerConcept();
                mapping.setAttemptAnswer(answer);
                mapping.setConcept(concept);
                answerConceptRepository.save(mapping);
            }
        }
    }

    private List<FixtureEvidenceState> evidenceState() {
        List<FixtureEvidenceState> result = new ArrayList<>();
        for (Concept concept : List.of(conceptA, conceptB)) {
            StudentConceptMastery mastery = masteryRepository
                    .findByUserIdAndConceptId(owner.getId(), concept.getId()).orElseThrow();
            List<EventSnapshot> events = learningEventRepository
                    .findByUserIdAndConceptIdOrderByOccurredAtAsc(owner.getId(), concept.getId()).stream()
                    .map(event -> new EventSnapshot(event.getId(), event.getIdempotencyKey(), event.getNormalizedScore()))
                    .sorted(Comparator.comparing(EventSnapshot::id))
                    .toList();
            result.add(new FixtureEvidenceState(mastery.getEvidenceCount(), mastery.getMasteryEstimate(), events));
        }
        return List.copyOf(result);
    }

    private double activeMasteryMean() {
        return activeMasteryLevels().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private List<Double> activeMasteryLevels() {
        return List.of(conceptA, conceptB).stream()
                .map(concept -> masteryRepository.findByUserIdAndConceptId(owner.getId(), concept.getId())
                        .orElseThrow().getMasteryEstimate())
                .toList();
    }

        private List<Concept> fixtureConcepts() {
                return java.util.stream.Stream.of(conceptA, conceptB, staleConcept, orphanConcept)
                                .filter(java.util.Objects::nonNull)
                                .toList();
        }

    private record EventSnapshot(Long id, String idempotencyKey, Double normalizedScore) {}
    private record FixtureEvidenceState(Integer evidenceCount, Double masteryEstimate, List<EventSnapshot> events) {}

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        AdjustableClock phase7E2EClock() {
            return new AdjustableClock(Instant.now(), ZoneId.systemDefault());
        }
    }

    static class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        AdjustableClock(Instant initial, ZoneId zone) {
            this.instant = new AtomicReference<>(initial);
            this.zone = zone;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId newZone) { return new AdjustableClock(instant(), newZone); }
        @Override public Instant instant() { return instant.get(); }
        void setInstant(Instant value) { instant.set(value); }
    }
}
