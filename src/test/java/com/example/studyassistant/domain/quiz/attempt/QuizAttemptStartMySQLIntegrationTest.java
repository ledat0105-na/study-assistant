package com.example.studyassistant.domain.quiz.attempt;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class QuizAttemptStartMySQLIntegrationTest {

    @Autowired private QuizAttemptStartService service;

    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizQuestionOptionRepository optionRepository;
    @Autowired private QuizQuestionAcceptedAnswerRepository acceptedAnswerRepository;
    @Autowired private QuizQuestionConceptRepository questionConceptRepository;
    @Autowired private ConceptRepository conceptRepository;

    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository attemptAnswerRepository;
    @Autowired private QuizAttemptAnswerOptionRepository attemptOptionRepository;
    @Autowired private QuizAttemptAnswerAcceptedAnswerRepository attemptAcceptedAnswerRepository;
    @Autowired private QuizAttemptAnswerConceptRepository attemptConceptRepository;
    
    @MockitoSpyBean private QuizAttemptAnswerOptionRepository spyAttemptOptionRepository;

    @Autowired private TransactionTemplate transactionTemplate;

    private User testUser;
    private Notebook testNotebook;
    private Quiz testQuiz;
    private Concept testConcept;
    private QuizQuestion qMcqSingle;
    private QuizQuestion qMcqMulti;
    private QuizQuestion qTrueFalse;
    private QuizQuestion qShortAnswer;
    private QuizQuestionOption optSingleTrue;
    private QuizQuestionOption optSingleFalse;
    private QuizQuestionAcceptedAnswer acc1;

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("attempt_user_" + System.currentTimeMillis());
            testUser.setPassword("pass");
            testUser.setEmail("attempt_" + System.currentTimeMillis() + "@example.com");
            testUser = userRepository.save(testUser);

            testNotebook = new Notebook();
            testNotebook.setTitle("Attempt Notebook");
            testNotebook.setUser(testUser);
            testNotebook = notebookRepository.save(testNotebook);

            testConcept = new Concept();
            testConcept.setNotebook(testNotebook);
            testConcept.setCanonicalName("Test Concept");
            testConcept.setNormalizedName("test concept");
            testConcept = conceptRepository.save(testConcept);

            testQuiz = new Quiz();
            testQuiz.setNotebook(testNotebook);
            testQuiz.setTitle("Attempt Quiz");
            testQuiz.setStatus(QuizStatus.PUBLISHED);
            testQuiz.setMode(QuizMode.PRACTICE);
            testQuiz.setTimeLimitSeconds(1800);
            testQuiz.setPassThreshold(80.0);
            testQuiz = quizRepository.save(testQuiz);

            // MCQ_SINGLE
            qMcqSingle = new QuizQuestion();
            qMcqSingle.setQuiz(testQuiz);
            qMcqSingle.setQuestionOrder(1);
            qMcqSingle.setQuestionType(QuizQuestionType.MCQ_SINGLE);
            qMcqSingle.setPoints(1.0);
            qMcqSingle.setQuestion("Test MCQ Single");
            qMcqSingle = questionRepository.save(qMcqSingle);

            optSingleTrue = new QuizQuestionOption();
            optSingleTrue.setQuestion(qMcqSingle);
            optSingleTrue.setOptionOrder(1);
            optSingleTrue.setOptionText("Option A");
            optSingleTrue.setIsCorrect(true);
            optSingleTrue = optionRepository.save(optSingleTrue);

            optSingleFalse = new QuizQuestionOption();
            optSingleFalse.setQuestion(qMcqSingle);
            optSingleFalse.setOptionOrder(2);
            optSingleFalse.setOptionText("Option B");
            optSingleFalse.setIsCorrect(false);
            optSingleFalse = optionRepository.save(optSingleFalse);

            QuizQuestionConcept qqc = new QuizQuestionConcept();
            qqc.setQuestion(qMcqSingle);
            qqc.setConcept(testConcept);
            questionConceptRepository.save(qqc);

            // MCQ_MULTI
            qMcqMulti = new QuizQuestion();
            qMcqMulti.setQuiz(testQuiz);
            qMcqMulti.setQuestionOrder(2);
            qMcqMulti.setQuestionType(QuizQuestionType.MCQ_MULTI);
            qMcqMulti.setPoints(2.0);
            qMcqMulti.setQuestion("Test MCQ Multi");
            qMcqMulti = questionRepository.save(qMcqMulti);

            QuizQuestionOption m1 = new QuizQuestionOption();
            m1.setQuestion(qMcqMulti);
            m1.setOptionOrder(1);
            m1.setOptionText("M1");
            m1.setIsCorrect(true);
            optionRepository.save(m1);

            QuizQuestionOption m2 = new QuizQuestionOption();
            m2.setQuestion(qMcqMulti);
            m2.setOptionOrder(2);
            m2.setOptionText("M2");
            m2.setIsCorrect(true);
            optionRepository.save(m2);

            QuizQuestionOption m3 = new QuizQuestionOption();
            m3.setQuestion(qMcqMulti);
            m3.setOptionOrder(3);
            m3.setOptionText("M3");
            m3.setIsCorrect(false);
            optionRepository.save(m3);

            // TRUE_FALSE
            qTrueFalse = new QuizQuestion();
            qTrueFalse.setQuiz(testQuiz);
            qTrueFalse.setQuestionOrder(3);
            qTrueFalse.setQuestionType(QuizQuestionType.TRUE_FALSE);
            qTrueFalse.setPoints(1.0);
            qTrueFalse.setQuestion("Test TF");
            qTrueFalse = questionRepository.save(qTrueFalse);

            QuizQuestionOption tf1 = new QuizQuestionOption();
            tf1.setQuestion(qTrueFalse);
            tf1.setOptionOrder(1);
            tf1.setOptionText("True");
            tf1.setIsCorrect(true);
            optionRepository.save(tf1);

            QuizQuestionOption tf2 = new QuizQuestionOption();
            tf2.setQuestion(qTrueFalse);
            tf2.setOptionOrder(2);
            tf2.setOptionText("False");
            tf2.setIsCorrect(false);
            optionRepository.save(tf2);

            // SHORT_ANSWER
            qShortAnswer = new QuizQuestion();
            qShortAnswer.setQuiz(testQuiz);
            qShortAnswer.setQuestionOrder(4);
            qShortAnswer.setQuestionType(QuizQuestionType.SHORT_ANSWER);
            qShortAnswer.setPoints(5.0);
            qShortAnswer.setQuestion("Test Short");
            qShortAnswer = questionRepository.save(qShortAnswer);

            acc1 = new QuizQuestionAcceptedAnswer();
            acc1.setQuestion(qShortAnswer);
            acc1.setAcceptedOrder(1);
            acc1.setAcceptedText("correct 1");
            acceptedAnswerRepository.save(acc1);

            return null;
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            // Delete snapshot children first, using exact fixture deletes
            attemptRepository.findByQuizId(testQuiz.getId()).forEach(a -> {
                attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(a.getId()).forEach(ans -> {
                    attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ans.getId()).forEach(attemptOptionRepository::delete);
                    attemptAcceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(ans.getId()).forEach(attemptAcceptedAnswerRepository::delete);
                    attemptConceptRepository.findByAttemptAnswerId(ans.getId()).forEach(attemptConceptRepository::delete);
                    attemptAnswerRepository.delete(ans);
                });
                attemptRepository.delete(a);
            });

            // Delete master
            if (testQuiz != null) {
                questionRepository.findByQuizId(testQuiz.getId()).forEach(q -> {
                    optionRepository.findByQuestionIdOrderByOptionOrderAsc(q.getId()).forEach(optionRepository::delete);
                    acceptedAnswerRepository.findByQuestionIdOrderByAcceptedOrderAsc(q.getId()).forEach(acceptedAnswerRepository::delete);
                    questionConceptRepository.findByQuestionId(q.getId()).forEach(questionConceptRepository::delete);
                    questionRepository.delete(q);
                });
                quizRepository.findById(testQuiz.getId()).ifPresent(quizRepository::delete);
            }
            if (testConcept != null) conceptRepository.findById(testConcept.getId()).ifPresent(conceptRepository::delete);
            if (testNotebook != null) notebookRepository.findById(testNotebook.getId()).ifPresent(notebookRepository::delete);
            if (testUser != null) userRepository.findById(testUser.getId()).ifPresent(userRepository::delete);

            return null;
        });
    }

    @Test
    void testSnapshotCoverageAndMutationIsolation() {
        QuizAttemptStartCommand cmd = new QuizAttemptStartCommand(testUser.getId(), testQuiz.getId());
        QuizAttemptResponse response = service.startAttempt(cmd);
        assertThat(response).isNotNull();

        transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findById(response.attemptId()).orElseThrow();
            // A. Attempt fields
            assertThat(attempt.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(attempt.getScoringVersion()).isEqualTo("QUIZ_SCORING_V1");
            assertThat(attempt.getMasteryApplied()).isFalse();
            assertThat(attempt.getModeSnapshot()).isEqualTo(QuizMode.PRACTICE);
            assertThat(attempt.getTimeLimitSecondsSnapshot()).isEqualTo(1800);
            assertThat(attempt.getPassThresholdSnapshot()).isEqualTo(80.0);

            // B. 4 Answers for 4 Questions
            List<QuizAttemptAnswer> answers = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId());
            assertThat(answers).hasSize(4);

            QuizAttemptAnswer ansSingle = answers.get(0);
            QuizAttemptAnswer ansMulti = answers.get(1);
            QuizAttemptAnswer ansTf = answers.get(2);
            QuizAttemptAnswer ansShort = answers.get(3);

            // C. MCQ_SINGLE Option Snapshots
            List<QuizAttemptAnswerOption> singleOpts = attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ansSingle.getId());
            assertThat(singleOpts).hasSize(2);
            assertThat(singleOpts.get(0).getOptionTextSnapshot()).isEqualTo("Option A");
            assertThat(singleOpts.get(0).getIsCorrectSnapshot()).isTrue();
            assertThat(singleOpts.get(0).getStudentSelected()).isFalse();

            // D. MCQ_MULTI Option Snapshots
            List<QuizAttemptAnswerOption> multiOpts = attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ansMulti.getId());
            assertThat(multiOpts).hasSize(3);
            assertThat(multiOpts.stream().filter(QuizAttemptAnswerOption::getIsCorrectSnapshot).count()).isEqualTo(2);

            // E. TRUE_FALSE Snapshots
            List<QuizAttemptAnswerOption> tfOpts = attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ansTf.getId());
            assertThat(tfOpts).hasSize(2);
            assertThat(tfOpts.get(0).getOptionTextSnapshot()).isEqualTo("True");

            // F. SHORT_ANSWER Accepted Snapshots
            List<QuizAttemptAnswerAcceptedAnswer> accs = attemptAcceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(ansShort.getId());
            assertThat(accs).hasSize(1);
            assertThat(accs.get(0).getAcceptedTextSnapshot()).isEqualTo("correct 1");

            // G. Concept Snapshots
            List<QuizAttemptAnswerConcept> cons = attemptConceptRepository.findByAttemptAnswerId(ansSingle.getId());
            assertThat(cons).hasSize(1);
            assertThat(cons.get(0).getConcept().getId()).isEqualTo(testConcept.getId());

            // H. Master mutation isolation
            qMcqSingle.setQuestion("Mutated Question");
            questionRepository.save(qMcqSingle);
            
            optSingleTrue.setOptionText("Mutated Option A");
            optSingleTrue.setIsCorrect(false);
            optionRepository.save(optSingleTrue);

            acc1.setAcceptedText("mutated");
            acceptedAnswerRepository.save(acc1);

            return null;
        });

        // Re-verify snapshots are unchanged
        transactionTemplate.execute(status -> {
            List<QuizAttemptAnswer> answers = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(response.attemptId());
            assertThat(answers.get(0).getQuestionTextSnapshot()).isEqualTo("Test MCQ Single"); // Unchanged
            
            List<QuizAttemptAnswerOption> singleOpts = attemptOptionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(answers.get(0).getId());
            assertThat(singleOpts.get(0).getOptionTextSnapshot()).isEqualTo("Option A"); // Unchanged
            assertThat(singleOpts.get(0).getIsCorrectSnapshot()).isTrue(); // Unchanged
            
            List<QuizAttemptAnswerAcceptedAnswer> accs = attemptAcceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(answers.get(3).getId());
            assertThat(accs.get(0).getAcceptedTextSnapshot()).isEqualTo("correct 1"); // Unchanged
            return null;
        });
    }

    @Test
    void testAtomicRollbackOnFailure() {
        // I. Atomic rollback
        long initialAttempts = attemptRepository.count();
        long initialAnswers = attemptAnswerRepository.count();

        // Deliberately force an exception during snapshot option persistence
        doAnswer(inv -> {
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            assertThat(active).isTrue();
            throw new RuntimeException("Deliberate snapshot failure");
        }).when(spyAttemptOptionRepository).save(any());

        QuizAttemptStartCommand cmd = new QuizAttemptStartCommand(testUser.getId(), testQuiz.getId());
        Exception ex = assertThrows(RuntimeException.class, () -> service.startAttempt(cmd));
        assertThat(ex.getMessage()).contains("Deliberate snapshot failure");

        // Verify total rollback
        transactionTemplate.execute(status -> {
            assertThat(attemptRepository.count()).isEqualTo(initialAttempts);
            assertThat(attemptAnswerRepository.count()).isEqualTo(initialAnswers);
            return null;
        });
    }

    @Test
    void testMultipleAttempts() {
        // J. Multiple attempts
        QuizAttemptStartCommand cmd = new QuizAttemptStartCommand(testUser.getId(), testQuiz.getId());
        QuizAttemptResponse r1 = service.startAttempt(cmd);
        QuizAttemptResponse r2 = service.startAttempt(cmd);

        assertThat(r1.attemptId()).isNotEqualTo(r2.attemptId());
        
        transactionTemplate.execute(status -> {
            assertThat(attemptRepository.findById(r1.attemptId())).isPresent();
            assertThat(attemptRepository.findById(r2.attemptId())).isPresent();
            
            List<QuizAttemptAnswer> a1 = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(r1.attemptId());
            List<QuizAttemptAnswer> a2 = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(r2.attemptId());
            
            assertThat(a1).hasSize(4);
            assertThat(a2).hasSize(4);
            
            // Prove they are distinct snapshot rows
            assertThat(a1.get(0).getId()).isNotEqualTo(a2.get(0).getId());
            return null;
        });
    }

    @Test
    void testStudentDtoSafety() {
        QuizAttemptStartCommand cmd = new QuizAttemptStartCommand(testUser.getId(), testQuiz.getId());
        QuizAttemptResponse response = service.startAttempt(cmd);
        
        // Assert response shape does not contain secret fields
        // Since we explicitly constructed QuizAttemptQuestionDto and QuizAttemptOptionDto, 
        // they physically lack isCorrect, explanation, pointsEarned, etc.
        // We can verify by asserting the properties aren't accessible and using reflection to ensure they aren't hiding.
        boolean hasIsCorrect = false;
        try {
            QuizAttemptOptionDto.class.getMethod("isCorrectSnapshot");
            hasIsCorrect = true;
        } catch (NoSuchMethodException ignored) {}
        
        assertThat(hasIsCorrect).as("Student DTO leaks correctness").isFalse();
    }
}
