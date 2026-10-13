package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroundedQuizGenerationServiceTest {

    @org.mockito.Spy private QuizGenerationProperties props = new QuizGenerationProperties();
    @Mock private QuizScopeValidator scopeValidator;
    @Mock private QuizActiveChunkSelector chunkSelector;
    @Mock private LlmProvider llmProvider;
    @Mock private ObjectMapper objectMapper;
    @Mock private UserRepository userRepository;
    @Mock private NotebookRepository notebookRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizQuestionOptionRepository quizQuestionOptionRepository;
    @Mock private QuizQuestionAcceptedAnswerRepository quizQuestionAcceptedAnswerRepository;
    @Mock private QuizQuestionSourceRepository quizQuestionSourceRepository;
    @Mock private QuizQuestionConceptRepository quizQuestionConceptRepository;
    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @InjectMocks
    private GroundedQuizGenerationService service;

    private User testUser;
    private Notebook testNotebook;
    private Document testDoc;
    private DocumentChunk testChunk;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setId(1L);

        testNotebook = new Notebook();
        testNotebook.setId(1L);
        testNotebook.setUser(testUser);

        testDoc = new Document();
        testDoc.setId(10L);
        testDoc.setNotebook(testNotebook);

        testChunk = new DocumentChunk();
        testChunk.setId(100L);
        testChunk.setContent("test content");
        testChunk.setDocument(testDoc);
    }

    @Test
    void testScopeValidationCalled() {
        GroundedQuizGenerationCommand cmd = new GroundedQuizGenerationCommand(
            1L, QuizScopeType.DOCUMENT, 1L, 10L, null, null, null, null, null, null, null, null
        );
        
        when(chunkSelector.selectEvidence(cmd)).thenReturn(List.of(testChunk));
        when(llmProvider.generate(any(), any(), anyDouble())).thenReturn("{}");
        
        try {
            service.generate(cmd);
        } catch (Exception e) {} // ignore parsing error

        verify(scopeValidator).validateScope(cmd);
    }

    @Test
    void testNoEvidenceThrowsException() {
        GroundedQuizGenerationCommand cmd = new GroundedQuizGenerationCommand(
            1L, QuizScopeType.DOCUMENT, 1L, 10L, null, null, null, null, null, null, null, null
        );
        when(chunkSelector.selectEvidence(cmd)).thenReturn(Collections.emptyList());
        
        Exception ex = assertThrows(IllegalArgumentException.class, () -> service.generate(cmd));
        assertTrue(ex.getMessage().contains("No authorized active evidence"));
    }

    @Test
    void standaloneGenerationPersistsQuizWithDocumentAndNoNotebook() {
        generateStandalone(false);
    }

    @Test
    void persistenceFailureNeverPublishesQuiz() {
        generateStandalone(true);
    }

    private void generateStandalone(boolean failPersistence) {
        testDoc.setNotebook(null); testDoc.setUser(testUser);
        testDoc.setStatus("READY"); testDoc.setActiveIndexVersion("v1");
        testChunk.setIndexVersion("v1");
        GroundedQuizGenerationCommand cmd = new GroundedQuizGenerationCommand(
                1L, QuizScopeType.DOCUMENT, null, 10L, null, null, QuizMode.PRACTICE,
                1, List.of(QuizQuestionType.MCQ_SINGLE), null, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        when(chunkSelector.selectEvidence(cmd)).thenReturn(List.of(testChunk));
        when(llmProvider.generate(any(), any(), anyDouble())).thenReturn("""
                {"questions":[{"type":"MCQ_SINGLE","questionText":"Question?","points":1,
                "explanation":"Grounded","sources":["S1"],"options":[
                {"text":"Yes","isCorrect":true},{"text":"No","isCorrect":false}],"acceptedAnswers":[]}]}
                """);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(documentRepository.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testDoc));
        when(documentRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(testDoc));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
        });
        when(quizQuestionRepository.save(any())).thenAnswer(invocation -> {
            QuizQuestion question = invocation.getArgument(0); question.setId(30L); return question;
        });
        java.util.List<QuizStatus> statesAtSave = new java.util.ArrayList<>();
        when(quizRepository.save(any())).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            statesAtSave.add(quiz.getStatus());
            assertNull(quiz.getNotebook()); assertSame(testDoc, quiz.getDocument());
            quiz.setId(20L); return quiz;
        });
        if (failPersistence) {
            when(quizQuestionOptionRepository.save(any())).thenThrow(new IllegalStateException("Simulated persistence failure"));
            assertThrows(IllegalStateException.class, () -> service.generate(cmd));
            assertEquals(List.of(QuizStatus.DRAFT), statesAtSave);
            verify(quizRepository, times(1)).save(any());
            verifyNoInteractions(quizQuestionSourceRepository);
            return;
        }
        GroundedQuizGenerationResult result = service.generate(cmd);
        assertEquals(1, result.generatedCount());
        assertEquals(20L, result.quizId());
        assertEquals(30L, result.questions().get(0).questionId());
        assertEquals(List.of(QuizStatus.DRAFT, QuizStatus.PUBLISHED), statesAtSave);
        org.mockito.InOrder publication = inOrder(quizRepository, quizQuestionRepository, quizQuestionOptionRepository, quizQuestionSourceRepository);
        publication.verify(quizRepository).save(any());
        publication.verify(quizQuestionRepository).save(any());
        publication.verify(quizQuestionOptionRepository, times(2)).save(any());
        publication.verify(quizQuestionSourceRepository).save(any());
        publication.verify(quizRepository).save(argThat(q -> q.getStatus() == QuizStatus.PUBLISHED));
        verify(scopeValidator, times(2)).validateScope(cmd);
        verifyNoInteractions(notebookRepository);
        verify(quizQuestionOptionRepository, times(2)).save(any());
        verify(quizQuestionSourceRepository).save(any());
    }

    @Test
    void testShortAnswerNormalization() {
        String input = "  Hello \u2168   world  "; // Roman numeral 9
        String normalized = QuizContentHasher.normalizeShortAnswer(input);
        assertEquals("hello ix world", normalized);
    }

    @Test
    void testContentHashDeterminism() {
        String h1 = QuizContentHasher.hash("MCQ_SINGLE|q1|1.0|opt1:true|opt2:false");
        String h2 = QuizContentHasher.hash("MCQ_SINGLE|q1|1.0|opt1:true|opt2:false");
        String h3 = QuizContentHasher.hash("MCQ_SINGLE|q1|1.0|opt2:false|opt1:true");

        assertEquals(h1, h2);
        assertNotEquals(h1, h3); // Order matters
    }
}
