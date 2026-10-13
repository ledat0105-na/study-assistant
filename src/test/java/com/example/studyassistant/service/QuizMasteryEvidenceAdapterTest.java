package com.example.studyassistant.service;

import com.example.studyassistant.dto.GradedQuizQuestion;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.QuizAttemptAnswerConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizMasteryEvidenceAdapterTest {

    @Mock
    private MasteryUpdateEngine masteryUpdateEngine;

    @InjectMocks
    private QuizMasteryEvidenceAdapter adapter;

    private User user;
    private Notebook notebook;
    private Document document;
    private Quiz quiz;
    private Concept concept1;
    private Concept concept2;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(10L);
        notebook.setUser(user);

        document = new Document();
        document.setNotebook(notebook);

        quiz = new Quiz();
        quiz.setId(100L);
        quiz.setDocument(document);

        concept1 = new Concept();
        concept1.setId(500L);
        concept1.setNotebook(notebook);

        concept2 = new Concept();
        concept2.setId(600L);
        concept2.setNotebook(notebook);
    }

    private QuizQuestion makeQuestion(Long id, Concept concept) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setConcept(concept);
        return q;
    }

    @Test
    void applyEvidence_CorrectAnsweredAttributed_Dispatches1() {
        QuizQuestion q1 = makeQuestion(1L, concept1);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, true, true);
        LocalDateTime now = LocalDateTime.now();

        adapter.applyEvidence(1L, quiz, "token-1", now, List.of(gq));

        ArgumentCaptor<MasteryEvidenceCommand> captor = ArgumentCaptor.forClass(MasteryEvidenceCommand.class);
        verify(masteryUpdateEngine, times(1)).applyEvidence(captor.capture());

        MasteryEvidenceCommand cmd = captor.getValue();
        assertEquals(1L, cmd.userId());
        assertEquals(10L, cmd.notebookId());
        assertEquals(500L, cmd.conceptId());
        assertEquals(1.0, cmd.normalizedScore());
        assertEquals("QUIZ_QUESTION", cmd.sourceType());
        assertEquals("1", cmd.sourceId());
        assertEquals("QUIZ:100:SUB:token-1:Q:1", cmd.idempotencyKey());
        assertEquals(now, cmd.occurredAt());
    }

    @Test
    void applyEvidence_IncorrectAnsweredAttributed_Dispatches0() {
        QuizQuestion q1 = makeQuestion(1L, concept1);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, true, false);
        LocalDateTime now = LocalDateTime.now();

        adapter.applyEvidence(1L, quiz, "token-1", now, List.of(gq));

        ArgumentCaptor<MasteryEvidenceCommand> captor = ArgumentCaptor.forClass(MasteryEvidenceCommand.class);
        verify(masteryUpdateEngine, times(1)).applyEvidence(captor.capture());

        assertEquals(0.0, captor.getValue().normalizedScore());
    }

    @Test
    void applyEvidence_ConceptNull_Skips() {
        QuizQuestion q1 = makeQuestion(1L, null);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, true, true);
        adapter.applyEvidence(1L, quiz, "token-1", LocalDateTime.now(), List.of(gq));
        verify(masteryUpdateEngine, never()).applyEvidence(any());
    }

    @Test
    void applyEvidence_MissingAnswer_Skips() {
        QuizQuestion q1 = makeQuestion(1L, concept1);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, false, false);
        adapter.applyEvidence(1L, quiz, "token-1", LocalDateTime.now(), List.of(gq));
        verify(masteryUpdateEngine, never()).applyEvidence(any());
    }

    @Test
    void applyEvidence_MultipleQuestionsSameConcept_SortedByQuestionId() {
        QuizQuestion q2 = makeQuestion(2L, concept1);
        QuizQuestion q1 = makeQuestion(1L, concept1);
        adapter.applyEvidence(1L, quiz, "token", LocalDateTime.now(), List.of(
            new GradedQuizQuestion(q2, true, true),
            new GradedQuizQuestion(q1, true, true)
        ));

        ArgumentCaptor<MasteryEvidenceCommand> captor = ArgumentCaptor.forClass(MasteryEvidenceCommand.class);
        verify(masteryUpdateEngine, times(2)).applyEvidence(captor.capture());
        
        List<MasteryEvidenceCommand> cmds = captor.getAllValues();
        assertEquals("1", cmds.get(0).sourceId());
        assertEquals("2", cmds.get(1).sourceId());
    }

    @Test
    void applyEvidence_MultipleConcepts_SortedByConceptId() {
        QuizQuestion q1 = makeQuestion(1L, concept2);
        QuizQuestion q2 = makeQuestion(2L, concept1);
        adapter.applyEvidence(1L, quiz, "token", LocalDateTime.now(), List.of(
            new GradedQuizQuestion(q1, true, true),
            new GradedQuizQuestion(q2, true, true)
        ));

        ArgumentCaptor<MasteryEvidenceCommand> captor = ArgumentCaptor.forClass(MasteryEvidenceCommand.class);
        verify(masteryUpdateEngine, times(2)).applyEvidence(captor.capture());

        List<MasteryEvidenceCommand> cmds = captor.getAllValues();
        assertEquals(500L, cmds.get(0).conceptId());
        assertEquals(600L, cmds.get(1).conceptId());
    }

    @Test
    void applyEvidence_ConceptNotebookMismatch_ThrowsException() {
        Notebook otherNotebook = new Notebook();
        otherNotebook.setId(99L);
        Concept foreignConcept = new Concept();
        foreignConcept.setId(900L);
        foreignConcept.setNotebook(otherNotebook);

        QuizQuestion q1 = makeQuestion(1L, foreignConcept);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, true, true);

        assertThrows(IllegalStateException.class, () -> 
            adapter.applyEvidence(1L, quiz, "token", LocalDateTime.now(), List.of(gq))
        );
        verify(masteryUpdateEngine, never()).applyEvidence(any());
    }

    @Test
    void applyEvidence_IdempotencyKeyLength_Validation() {
        QuizQuestion q1 = makeQuestion(1L, concept1);
        GradedQuizQuestion gq = new GradedQuizQuestion(q1, true, true);
        
        String tooLongToken = "A".repeat(120);
        assertThrows(IllegalStateException.class, () -> 
            adapter.applyEvidence(1L, quiz, tooLongToken, LocalDateTime.now(), List.of(gq))
        );
    }

    @Test
    void resolveNotebook_R1_DirectDocument() {
        assertEquals(10L, adapter.resolveAuthoritativeNotebookId(quiz));
    }

    @Test
    void resolveNotebook_R2_TopicDocument() {
        Quiz topicQuiz = new Quiz();
        topicQuiz.setId(101L);
        Topic topic = new Topic();
        topic.setDocument(document);
        topicQuiz.setTopic(topic);
        
        assertEquals(10L, adapter.resolveAuthoritativeNotebookId(topicQuiz));
    }

    @Test
    void resolveNotebook_R3_BothPathsSameNotebook() {
        Quiz complexQuiz = new Quiz();
        complexQuiz.setId(102L);
        complexQuiz.setDocument(document);
        Topic topic = new Topic();
        topic.setDocument(document);
        complexQuiz.setTopic(topic);

        assertEquals(10L, adapter.resolveAuthoritativeNotebookId(complexQuiz));
    }

    @Test
    void resolveNotebook_R4_BothPathsDifferentNotebooks() {
        Quiz complexQuiz = new Quiz();
        complexQuiz.setId(102L);
        complexQuiz.setDocument(document); // Notebook 10
        
        Notebook otherNotebook = new Notebook();
        otherNotebook.setId(99L);
        Document otherDoc = new Document();
        otherDoc.setNotebook(otherNotebook);
        Topic topic = new Topic();
        topic.setDocument(otherDoc);
        complexQuiz.setTopic(topic);

        assertThrows(IllegalArgumentException.class, () -> 
            adapter.resolveAuthoritativeNotebookId(complexQuiz)
        );
    }

    @Test
    void resolveNotebook_DirectQuizNotebook() {
        quiz.setNotebook(notebook);
        assertEquals(10L, adapter.resolveAuthoritativeNotebookId(quiz));
    }

    @Test
    void resolveNotebook_DirectAndLegacyConflictFailsClosed() {
        Notebook otherNotebook = new Notebook();
        otherNotebook.setId(99L);
        quiz.setNotebook(otherNotebook);

        assertThrows(IllegalArgumentException.class, () -> adapter.resolveAuthoritativeNotebookId(quiz));
    }

    @Test
    void applySnapshotEvidence_ConceptNotebookMismatchFailsClosed() {
        quiz.setNotebook(notebook);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(200L);
        attempt.setQuiz(quiz);
        attempt.setUser(user);
        attempt.setSubmissionToken("snapshot-token");

        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.setId(300L);
        answer.setAttempt(attempt);
        answer.setQuestion(makeQuestion(1L, concept1));

        Notebook foreignNotebook = new Notebook();
        foreignNotebook.setId(99L);
        Concept foreignConcept = new Concept();
        foreignConcept.setId(900L);
        foreignConcept.setNotebook(foreignNotebook);
        QuizAttemptAnswerConcept snapshot = new QuizAttemptAnswerConcept();
        snapshot.setAttemptAnswer(answer);
        snapshot.setConcept(foreignConcept);

        QuizAttemptAnswerConceptRepository conceptRepository = mock(QuizAttemptAnswerConceptRepository.class);
        when(conceptRepository.findByAttemptAnswerId(300L)).thenReturn(List.of(snapshot));

        assertThrows(IllegalStateException.class, () -> adapter.applySnapshotEvidence(
                attempt, List.of(answer), LocalDateTime.now(), conceptRepository));
        verify(masteryUpdateEngine, never()).applyEvidence(any());
    }

    @Test
    void documentOnlySnapshotWithoutConceptsDoesNotRequireNotebook() {
        Quiz documentQuiz = new Quiz(); documentQuiz.setId(99L);
        documentQuiz.setDocument(new Document());
        QuizAttempt attempt = new QuizAttempt(); attempt.setQuiz(documentQuiz);
        QuizAttemptAnswer answer = new QuizAttemptAnswer(); answer.setId(300L);
        QuizAttemptAnswerConceptRepository repository = mock(QuizAttemptAnswerConceptRepository.class);
        when(repository.findByAttemptAnswerId(300L)).thenReturn(List.of());
        adapter.applySnapshotEvidence(attempt, List.of(answer), LocalDateTime.now(), repository);
        verifyNoInteractions(masteryUpdateEngine);
    }

    @Test
    void resolveNotebook_R5_NoResolvableNotebook() {
        Quiz emptyQuiz = new Quiz();
        emptyQuiz.setId(103L);
        assertThrows(IllegalArgumentException.class, () -> 
            adapter.resolveAuthoritativeNotebookId(emptyQuiz)
        );
    }
}
