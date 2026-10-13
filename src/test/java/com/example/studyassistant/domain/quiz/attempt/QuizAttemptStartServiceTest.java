package com.example.studyassistant.domain.quiz.attempt;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuizAttemptStartServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizQuestionOptionRepository optionRepository;
    @Mock private QuizQuestionAcceptedAnswerRepository acceptedAnswerRepository;
    @Mock private QuizQuestionConceptRepository questionConceptRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAttemptAnswerRepository attemptAnswerRepository;
    @Mock private QuizAttemptAnswerOptionRepository attemptOptionRepository;
    @Mock private QuizAttemptAnswerAcceptedAnswerRepository attemptAcceptedAnswerRepository;
    @Mock private QuizAttemptAnswerConceptRepository attemptConceptRepository;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private QuizAttemptStartService service;

    private User user;
    private Notebook notebook;
    private Quiz quiz;

    @BeforeEach
    void setup() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(1L);
        notebook.setUser(user);

        quiz = new Quiz();
        quiz.setId(100L);
        quiz.setNotebook(notebook);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setMode(QuizMode.PRACTICE);
    }

    @Test
    void testStartDraftRejected() {
        quiz.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        
        Exception ex = assertThrows(IllegalArgumentException.class, () -> service.startAttempt(new QuizAttemptStartCommand(1L, 100L)));
        assertThat(ex.getMessage()).contains("not PUBLISHED");
    }

    @Test
    void testForeignOwnerRejected() {
        User other = new User();
        other.setId(2L);
        notebook.setUser(other);
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        
        Exception ex = assertThrows(IllegalArgumentException.class, () -> service.startAttempt(new QuizAttemptStartCommand(1L, 100L)));
        assertThat(ex.getMessage()).contains("not found or unauthorized");
    }

    @Test
    void documentQuizWithoutNotebookUsesDocumentOwner() {
        Document document = new Document(); document.setUser(user);
        quiz.setDocument(document); quiz.setNotebook(null);
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        QuizQuestion question = new QuizQuestion(); question.setId(42L);
        question.setQuestionOrder(1); question.setQuestionType(QuizQuestionType.MCQ_SINGLE);
        question.setPoints(1.0); question.setQuestion("Document question");
        when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(question));
        when(attemptRepository.save(any())).thenAnswer(inv -> {
            QuizAttempt saved = inv.getArgument(0); saved.setId(500L); return saved;
        });
        when(attemptAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.startAttempt(new QuizAttemptStartCommand(1L, 100L)).attemptId()).isEqualTo(500L);
        verify(attemptRepository).save(argThat(a -> a.getUser() == user));
    }

    @Test
    void foreignDocumentRejectedEvenWhenNotebookBelongsToCaller() {
        User other = new User(); other.setId(2L);
        Document document = new Document(); document.setUser(other); quiz.setDocument(document);
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        assertThrows(IllegalArgumentException.class, () -> service.startAttempt(new QuizAttemptStartCommand(1L, 100L)));
        verifyNoInteractions(attemptRepository);
    }

    @Test
    void testEmptyQuizRejected() {
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizId(100L)).thenReturn(Collections.emptyList());
        
        Exception ex = assertThrows(IllegalArgumentException.class, () -> service.startAttempt(new QuizAttemptStartCommand(1L, 100L)));
        assertThat(ex.getMessage()).contains("empty quiz");
    }

    @Test
    void testStartSuccess() {
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        
        QuizQuestion q1 = new QuizQuestion();
        q1.setId(10L);
        q1.setQuestionOrder(1);
        q1.setQuestionType(QuizQuestionType.MCQ_SINGLE);
        q1.setPoints(1.0);
        q1.setQuestion("Test Q");
        when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(500L);
        attempt.setQuiz(quiz);
        attempt.setModeSnapshot(QuizMode.PRACTICE);
        
        when(attemptRepository.save(any())).thenReturn(attempt);
        when(attemptAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizAttemptResponse resp = service.startAttempt(new QuizAttemptStartCommand(1L, 100L));
        
        assertThat(resp).isNotNull();
        assertThat(resp.attemptId()).isEqualTo(500L);
        verify(attemptRepository).save(any(QuizAttempt.class));
        verify(attemptAnswerRepository).save(any(QuizAttemptAnswer.class));
    }
}
