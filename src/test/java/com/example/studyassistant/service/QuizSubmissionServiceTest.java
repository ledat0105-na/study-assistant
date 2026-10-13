package com.example.studyassistant.service;

import com.example.studyassistant.dto.QuizSubmissionRequest;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Quiz;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizQuestion;
import com.example.studyassistant.entity.QuizResult;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.QuizAttemptRepository;
import com.example.studyassistant.repository.QuizQuestionRepository;
import com.example.studyassistant.repository.QuizRepository;
import com.example.studyassistant.repository.QuizResultRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuizSubmissionServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizResultRepository quizResultRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private QuizMasteryEvidenceAdapter quizMasteryEvidenceAdapter;

    @InjectMocks
    private QuizSubmissionService service;

    private User user;
    private Quiz quiz;
    private QuizQuestion q1, q2;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        Document doc = new Document();
        doc.setUser(user);

        quiz = new Quiz();
        quiz.setId(100L);
        quiz.setDocument(doc);

        q1 = new QuizQuestion();
        q1.setId(10L);
        q1.setCorrectAnswer("A");

        q2 = new QuizQuestion();
        q2.setId(11L);
        q2.setCorrectAnswer("B");

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void testA_NewSubmissionSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1, q2));
        when(quizResultRepository.findByUserIdAndQuizIdAndSubmissionToken(1L, 100L, "11111111-2222-3333-4444-555555555555"))
                .thenReturn(Optional.empty());
        when(quizResultRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("11111111-2222-3333-4444-555555555555");
        req.setAnswers(Map.of(10L, "A", 11L, "c"));

        QuizResult res = service.submitQuiz(1L, 100L, req);

        assertNotNull(res);
        assertEquals(1, res.getCorrectAnswers());
        assertEquals(2, res.getTotalQuestions());
        assertEquals("11111111-2222-3333-4444-555555555555", res.getSubmissionToken());
        assertNotNull(res.getSubmissionHash());

        verify(quizResultRepository, times(1)).saveAndFlush(any(QuizResult.class));
        verify(quizAttemptRepository, times(1)).saveAndFlush(any(QuizAttempt.class));
    }

    @Test
    void testB_SameTokenSamePayload_Replay() throws Exception {
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        lenient().when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1, q2));

        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("11111111-2222-3333-4444-555555555555");
        req.setAnswers(Map.of(10L, "A"));

        // We compute the hash manually to mock the existing
        java.lang.reflect.Method m = QuizSubmissionService.class.getDeclaredMethod("computeHash", List.class, Map.class);
        m.setAccessible(true);
        String hash = (String) m.invoke(service, List.of(q1, q2), req.getAnswers());
        
        QuizResult existing = new QuizResult();
        existing.setId(999L);
        existing.setSubmissionHash(hash);
        
        when(quizResultRepository.findByUserIdAndQuizIdAndSubmissionToken(1L, 100L, "11111111-2222-3333-4444-555555555555"))
                .thenReturn(Optional.of(existing));

        QuizResult res = service.submitQuiz(1L, 100L, req);

        assertEquals(999L, res.getId());
        verify(quizResultRepository, never()).saveAndFlush(any());
        verify(quizAttemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testC_SameTokenDifferentPayload_Conflict() throws Exception {
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        lenient().when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1, q2));

        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("11111111-2222-3333-4444-555555555555");
        req.setAnswers(Map.of(10L, "A"));
        
        QuizResult existing = new QuizResult();
        existing.setId(999L);
        existing.setSubmissionHash("different_hash");
        
        when(quizResultRepository.findByUserIdAndQuizIdAndSubmissionToken(1L, 100L, "11111111-2222-3333-4444-555555555555"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            service.submitQuiz(1L, 100L, req);
        });
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void testD_MissingToken_400() {
        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setAnswers(Map.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.submitQuiz(1L, 100L, req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void testE_MalformedToken_400() {
        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("bad-token");
        req.setAnswers(Map.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.submitQuiz(1L, 100L, req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void testH_ForeignQuestionID_400() {
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        lenient().when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1, q2));

        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("11111111-2222-3333-4444-555555555555");
        req.setAnswers(Map.of(999L, "A")); // 999 not in [10, 11]
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.submitQuiz(1L, 100L, req));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Unknown question ID"));
    }

    @Test
    void testN_UnrelatedConstraintFailure_Propagates() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizId(100L)).thenReturn(List.of(q1, q2));
        
        when(quizResultRepository.findByUserIdAndQuizIdAndSubmissionToken(1L, 100L, "11111111-2222-3333-4444-555555555555"))
                .thenReturn(Optional.empty()); // Precheck empty, race empty

        when(quizResultRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Other fail"));

        QuizSubmissionRequest req = new QuizSubmissionRequest();
        req.setSubmissionToken("11111111-2222-3333-4444-555555555555");
        req.setAnswers(Map.of());

        assertThrows(DataIntegrityViolationException.class, () -> service.submitQuiz(1L, 100L, req));
    }
}
