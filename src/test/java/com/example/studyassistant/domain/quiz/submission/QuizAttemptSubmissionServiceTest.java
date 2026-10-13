package com.example.studyassistant.domain.quiz.submission;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.MasteryUpdateEngine;
import com.example.studyassistant.service.QuizMasteryEvidenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
public class QuizAttemptSubmissionServiceTest {

    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAttemptAnswerRepository attemptAnswerRepository;
    @Mock private QuizAttemptAnswerOptionRepository optionRepository;
    @Mock private QuizAttemptAnswerAcceptedAnswerRepository acceptedAnswerRepository;
    @Mock private QuizAttemptAnswerConceptRepository conceptRepository;
    @Mock private QuizMasteryEvidenceAdapter masteryAdapter;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private Clock clock;

    @InjectMocks
    private QuizAttemptSubmissionService service;

    private QuizAttempt attempt;
    private static final Instant RECEIVED_INSTANT = Instant.parse("2026-09-28T04:45:40.839616800Z");

    @BeforeEach
    void setup() {
        lenient().when(clock.instant()).thenReturn(RECEIVED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        User user = new User();
        user.setId(1L);

        Notebook notebook = new Notebook();
        notebook.setId(1L);
        notebook.setUser(user);

        Quiz quiz = new Quiz();
        quiz.setId(1L);
        quiz.setNotebook(notebook);

        attempt = new QuizAttempt();
        attempt.setId(10L);
        attempt.setUser(user);
        attempt.setQuiz(quiz);
        attempt.setStatus("IN_PROGRESS");
        attempt.setMasteryApplied(false);
    }

    @Test
    void testMcqSingleCorrect() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));

        QuizAttemptAnswer ans = new QuizAttemptAnswer();
        ans.setId(100L);
        ans.setQuestionOrder(1);
        ans.setQuestionTypeSnapshot(QuizQuestionType.MCQ_SINGLE);
        ans.setPointsPossible(1.0);
        ans.setExplanationSnapshot("Giải thích từ snapshot");
        
        QuizQuestion q = new QuizQuestion();
        q.setId(99L);
        ans.setQuestion(q);

        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(ans));

        QuizAttemptAnswerOption opt1 = new QuizAttemptAnswerOption();
        opt1.setOptionOrder(1);
        opt1.setIsCorrectSnapshot(true);
        QuizAttemptAnswerOption opt2 = new QuizAttemptAnswerOption();
        opt2.setOptionOrder(2);
        opt2.setIsCorrectSnapshot(false);

        when(optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(100L)).thenReturn(List.of(opt1, opt2));

        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
            new QuizAttemptSubmitAnswerPayload(1, List.of(1), null)
        ));

        QuizAttemptResultResponse resp = service.submit(cmd);

        assertThat(resp.score()).isEqualTo(100.0);
        assertThat(resp.perQuestionResults().get(0).correctOptionOrders()).containsExactly(1);
        assertThat(resp.perQuestionResults().get(0).explanation()).isEqualTo("Giải thích từ snapshot");
        assertThat(resp.completedAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 4, 45, 40));
        assertThat(ans.getPointsEarned()).isEqualTo(1.0);
        assertThat(ans.getIsCorrect()).isTrue();
        assertThat(service.submit(cmd)).isEqualTo(resp);
    }

    @Test
    void exactDeadlineIsAcceptedAtFullReceivedPrecision() {
        prepareDeadlineAttempt();
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        prepareCorrectSnapshotAnswer();

        QuizAttemptResultResponse response = service.submit(validAnswerCommand());

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(attempt.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 4, 45, 40));
    }

    @Test
    void oneNanosecondAfterDeadlineExpires() {
        prepareDeadlineAttempt();
        when(clock.instant()).thenReturn(RECEIVED_INSTANT.plusNanos(1));
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        QuizAttemptSubmitCommand command = validAnswerCommand();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.submit(command));

        assertThat(attempt.getStatus()).isEqualTo("EXPIRED");
        verify(attemptRepository).save(attempt);
        verify(attemptAnswerRepository, never()).save(any());
        verify(masteryAdapter, never()).applySnapshotEvidence(any(), any(), any(), any());
    }

    private void prepareDeadlineAttempt() {
        attempt.setModeSnapshot(QuizMode.EXAM);
        attempt.setTimeLimitSecondsSnapshot(60);
        attempt.setStartedAt(LocalDateTime.of(2026, 9, 28, 4, 44, 40, 839616800));
    }

    private void prepareCorrectSnapshotAnswer() {
        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.setId(100L);
        answer.setQuestionOrder(1);
        answer.setQuestionTypeSnapshot(QuizQuestionType.MCQ_SINGLE);
        answer.setPointsPossible(1.0);
        QuizQuestion question = new QuizQuestion();
        question.setId(99L);
        answer.setQuestion(question);
        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(answer));

        QuizAttemptAnswerOption option = new QuizAttemptAnswerOption();
        option.setOptionOrder(1);
        option.setIsCorrectSnapshot(true);
        when(optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(100L)).thenReturn(List.of(option));
    }

    private QuizAttemptSubmitCommand validAnswerCommand() {
        return new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
                new QuizAttemptSubmitAnswerPayload(1, List.of(1), null)));
    }
    
    @Test
    void testShortAnswerNormalization() {
        // H. SHORT_ANSWER exact normalization
        // I. NFKC normalization
        // J. whitespace collapse
        // K. Locale.ROOT lowercase
        // L. punctuation is NOT stripped
        
        String input = "  Hello \u2168   world.  "; // Roman numeral 9
        String normalized = QuizAttemptSubmissionService.normalizeShortAnswer(input);
        assertThat(normalized).isEqualTo("hello ix world.");
    }

    @Test
    void testUnknownQuestionOrderRejected() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of());

        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
            new QuizAttemptSubmitAnswerPayload(999, List.of(1), null)
        ));

        Exception e = assertThrows(IllegalArgumentException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Unknown questionOrder");
    }

    @Test
    void testDuplicateQuestionOrderRejected() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        
        QuizAttemptAnswer ans = new QuizAttemptAnswer();
        ans.setId(100L); ans.setQuestionOrder(1); ans.setQuestionTypeSnapshot(QuizQuestionType.MCQ_SINGLE);
        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(ans));
        
        QuizAttemptAnswerOption opt1 = new QuizAttemptAnswerOption();
        opt1.setOptionOrder(1);
        QuizAttemptAnswerOption opt2 = new QuizAttemptAnswerOption();
        opt2.setOptionOrder(2);
        when(optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(100L)).thenReturn(List.of(opt1, opt2));

        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
            new QuizAttemptSubmitAnswerPayload(1, List.of(1), null),
            new QuizAttemptSubmitAnswerPayload(1, List.of(2), null)
        ));

        Exception e = assertThrows(IllegalArgumentException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Duplicate questionOrder");
    }

    @Test
    void testUnknownOptionOrderRejected() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        
        QuizAttemptAnswer ans = new QuizAttemptAnswer();
        ans.setId(100L); ans.setQuestionOrder(1); ans.setQuestionTypeSnapshot(QuizQuestionType.MCQ_MULTI);
        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(ans));
        when(optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(100L)).thenReturn(List.of());

        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
            new QuizAttemptSubmitAnswerPayload(1, List.of(999), null)
        ));

        Exception e = assertThrows(IllegalArgumentException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Unknown selected optionOrder");
    }

    @Test
    void testDuplicateOptionOrderRejected() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        
        QuizAttemptAnswer ans = new QuizAttemptAnswer();
        ans.setId(100L); ans.setQuestionOrder(1); ans.setQuestionTypeSnapshot(QuizQuestionType.MCQ_MULTI);
        when(attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(ans));
        
        QuizAttemptAnswerOption opt = new QuizAttemptAnswerOption();
        opt.setOptionOrder(1);
        when(optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(100L)).thenReturn(List.of(opt));

        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of(
            new QuizAttemptSubmitAnswerPayload(1, List.of(1, 1), null)
        ));

        Exception e = assertThrows(IllegalArgumentException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Duplicate selected optionOrder");
    }

    @Test
    void testMalformedTokenRejected() {
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "not-a-uuid", List.of());
        Exception e = assertThrows(IllegalArgumentException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Token is not a valid UUID");
    }

    @Test
    void testTokenOtherAttemptConflict() {
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.existsByUserIdAndSubmissionTokenAndIdNot(1L, "210cc85e-eb31-482f-8703-a26ab56b6b55", 10L)).thenReturn(true);
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of());
        Exception e = assertThrows(IllegalStateException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Conflict: Token already used by another attempt");
        InOrder order = inOrder(attemptRepository);
        order.verify(attemptRepository).findByIdAndUserIdWithPessimisticWrite(10L, 1L);
        order.verify(attemptRepository).existsByUserIdAndSubmissionTokenAndIdNot(1L, cmd.submissionToken(), 10L);
    }

    @Test
    void testUniqueTokenRaceMapsToConflictAfterRollback() {
        QuizAttempt otherAttempt = new QuizAttempt();
        otherAttempt.setId(11L);
        doThrow(new DataIntegrityViolationException("unique token"))
            .when(transactionTemplate).execute(any());
        when(attemptRepository.findByUserIdAndSubmissionToken(1L,
                "210cc85e-eb31-482f-8703-a26ab56b6b55")).thenReturn(Optional.of(otherAttempt));

        IllegalStateException conflict = assertThrows(IllegalStateException.class, () -> service.submit(
                new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of())));

        assertThat(conflict.getMessage()).contains("CONFLICT: Token already used by another attempt");
        verify(attemptRepository).findByUserIdAndSubmissionToken(1L, "210cc85e-eb31-482f-8703-a26ab56b6b55");
    }

    @Test
    void testUnknownStatusFailClosed() {
        attempt.setStatus("WEIRD_STATUS");
        when(attemptRepository.findByIdAndUserIdWithPessimisticWrite(10L, 1L)).thenReturn(Optional.of(attempt));
        QuizAttemptSubmitCommand cmd = new QuizAttemptSubmitCommand(1L, 10L, "210cc85e-eb31-482f-8703-a26ab56b6b55", List.of());
        Exception e = assertThrows(IllegalStateException.class, () -> service.submit(cmd));
        assertThat(e.getMessage()).contains("Corrupt or unknown status");
    }
}
