package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizBatchGenerationTest {
    final QuizGenerationProperties props = new QuizGenerationProperties();
    final QuizScopeValidator validator = mock(QuizScopeValidator.class);
    final QuizActiveChunkSelector selector = mock(QuizActiveChunkSelector.class);
    final LlmProvider llm = mock(LlmProvider.class);
    final UserRepository users = mock(UserRepository.class);
    final DocumentRepository documents = mock(DocumentRepository.class);
    final QuizRepository quizzes = mock(QuizRepository.class);
    final QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
    final QuizQuestionSourceRepository sources = mock(QuizQuestionSourceRepository.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    GroundedQuizGenerationService service;
    Document document;
    final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void setup() {
        User user = new User(); user.setId(1L);
        document = new Document(); document.setId(10L); document.setUser(user);
        document.setStatus("READY"); document.setActiveIndexVersion("v1");
        service = new GroundedQuizGenerationService(props, validator, selector, llm, new ObjectMapper(), users,
                mock(NotebookRepository.class), documents, mock(TopicRepository.class), mock(ConceptRepository.class),
                quizzes, questions, mock(QuizQuestionOptionRepository.class), mock(QuizQuestionAcceptedAnswerRepository.class),
                sources, mock(QuizQuestionConceptRepository.class), tx);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(documents.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenReturn(Optional.of(document));
        when(documents.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(document));
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        when(quizzes.save(any())).thenAnswer(inv -> { Quiz quiz = inv.getArgument(0); quiz.setId(20L); return quiz; });
        when(questions.save(any())).thenAnswer(inv -> { QuizQuestion q = inv.getArgument(0); q.setId((long) q.getQuestionOrder()); return q; });
    }

    GroundedQuizGenerationCommand command(Integer count) {
        return new GroundedQuizGenerationCommand(1L, QuizScopeType.DOCUMENT, null, 10L, null, null,
                QuizMode.PRACTICE, count, List.of(QuizQuestionType.MCQ_SINGLE), null, null, null);
    }

    void evidence(int count) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DocumentChunk c = new DocumentChunk(); c.setId(100L + i); c.setDocument(document);
            c.setChunkIndex(i); c.setIndexVersion("v1"); c.setContent("Nội dung phần " + i); chunks.add(c);
        }
        when(selector.selectEvidence(any())).thenReturn(chunks);
    }

    String question(String text, String alias) {
        return "{\"type\":\"MCQ_SINGLE\",\"questionText\":\"" + text + "\",\"sources\":[\"" + alias
                + "\"],\"options\":[{\"text\":\"Đúng\",\"isCorrect\":true},{\"text\":\"Sai\",\"isCorrect\":false}]}";
    }

    void validProvider() {
        when(llm.generate(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String prompt = inv.getArgument(0);
            assertTrue(prompt.contains("same language as the SOURCE DATA"));
            assertTrue(prompt.contains("Allowed question types: [MCQ_SINGLE]"));
            String alias = prompt.substring(prompt.indexOf("=== SOURCE DATA ===\n[") + "=== SOURCE DATA ===\n[".length());
            alias = alias.substring(0, alias.indexOf(']'));
            int count = Integer.parseInt(((String) inv.getArgument(1)).split(" ")[1]);
            assertTrue(count <= 5);
            int batch = calls.incrementAndGet();
            List<String> items = new ArrayList<>();
            for (int i = 0; i < count; i++) items.add(question("Câu " + batch + "-" + i, "[" + alias + "]"));
            return "{\"questions\":[" + String.join(",", items) + "]}";
        });
    }

    @Test void longDocumentUsesEightBatchesAndGlobalSourceAliases() {
        evidence(60); validProvider();
        var result = service.generate(command(null));
        assertEquals(40, result.requestedCount()); assertEquals(40, result.generatedCount());
        assertEquals(8, calls.get()); verify(tx, times(1)).execute(any()); verifySingleQuizPublished();
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(llm, times(8)).generate(prompts.capture(), anyString(), anyDouble());
        assertTrue(prompts.getAllValues().get(0).contains("[S1]\n"));
        assertTrue(prompts.getAllValues().get(7).contains("[S60]\n"));
        assertEquals(8, result.questions().stream().map(q -> q.sourceChunkIds().get(0)).distinct().count());
    }

    @Test void shortDocumentCanRequestFiftyQuestionsWithoutOversizedCall() {
        evidence(1); validProvider();
        var result = service.generate(command(100));
        assertEquals(50, result.requestedCount()); assertEquals(50, result.generatedCount());
        assertEquals(10, calls.get());
    }

    @Test void automaticCountHasMinimumAndScalesWithLength() {
        evidence(10); validProvider();
        assertEquals(7, service.generate(command(null)).requestedCount());
        evidence(1);
        assertEquals(5, service.generate(command(0)).requestedCount());
    }

    @Test void failedBatchKeepsOtherBatchAndReportsActualCount() {
        evidence(10);
        when(llm.generate(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("provider unavailable"))
                .thenReturn("{\"questions\":[" + question("Câu hợp lệ", "[S6]") + "]}");
        var result = service.generate(command(10));
        assertEquals(10, result.requestedCount()); assertEquals(1, result.generatedCount());
        assertEquals(List.of(105L), result.questions().get(0).sourceChunkIds());
        verifySingleQuizPublished();
    }

    @Test void allFailedBatchesDoNotPersistQuiz() {
        evidence(10);
        when(llm.generate(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("provider unavailable"));
        assertThrows(RuntimeException.class, () -> service.generate(command(10)));
        verifyNoInteractions(tx, quizzes, questions, sources);
    }

    private void verifySingleQuizPublished() {
        var saved = ArgumentCaptor.forClass(Quiz.class);
        verify(quizzes, times(2)).save(saved.capture());
        assertSame(saved.getAllValues().get(0), saved.getAllValues().get(1));
        assertEquals(QuizStatus.PUBLISHED, saved.getValue().getStatus());
    }

    @Test void invalidQuestionsAreSkippedAndExtraQuestionsAreCapped() {
        evidence(1);
        when(llm.generate(anyString(), anyString(), anyDouble())).thenReturn("{\"questions\":[null,"
                + question("Sai nguồn", "S99") + "," + question("Hợp lệ", "[S1]") + "," + question("Dư", "S1") + "]}");
        var result = service.generate(command(1));
        assertEquals(1, result.generatedCount()); assertEquals("Hợp lệ", result.questions().get(0).questionText());
    }

    @Test void batchCannotCiteAnotherBatchAndDuplicatesAreRemoved() {
        evidence(10);
        when(llm.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"questions\":[" + question("Trùng", "S1") + "]}")
                .thenReturn("{\"questions\":[" + question("Sai nhóm", "S1") + ","
                        + question(" trùng ", "S6") + "," + question("Mới", "S6") + "]}");
        var result = service.generate(command(10));
        assertEquals(2, result.generatedCount());
    }

    @Test void changedIndexDuringGenerationIsRejectedBeforeSaving() {
        evidence(1);
        when(llm.generate(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            document.setActiveIndexVersion("v2");
            return "{\"questions\":[" + question("Câu", "S1") + "]}";
        });
        assertThrows(IllegalArgumentException.class, () -> service.generate(command(1)));
        verifyNoInteractions(quizzes, questions, sources);
    }
}
