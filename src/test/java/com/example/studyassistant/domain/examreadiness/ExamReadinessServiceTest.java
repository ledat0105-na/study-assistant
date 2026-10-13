package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.QuizAttemptAnswerConceptRepository;
import com.example.studyassistant.repository.QuizAttemptRepository;
import com.example.studyassistant.repository.StudentConceptMasteryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamReadinessServiceTest {
    @Mock private NotebookRepository notebookRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private StudentConceptMasteryRepository masteryRepository;
    @Mock private QuizAttemptAnswerConceptRepository answerConceptRepository;

    @Test
    void appliesFixedThirtyDayWindowAndBulkConceptQueries() {
        Long userId = 11L;
        Long notebookId = 12L;
        LocalDateTime windowEnd = LocalDateTime.of(2026, 9, 28, 12, 0);
        Clock clock = Clock.fixed(windowEnd.toInstant(ZoneId.systemDefault().getRules().getOffset(windowEnd)),
                ZoneId.systemDefault());
        Concept first = concept(1L, notebookId, userId);
        Concept second = concept(2L, notebookId, userId);

        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(notebookId, userId)).thenReturn(true);
        when(attemptRepository.findEligibleReadinessExamAttempts(
                eq(userId), eq(notebookId), eq("COMPLETED"), eq(com.example.studyassistant.entity.QuizMode.EXAM),
                eq("QUIZ_SCORING_V1"), eq(PageRequest.of(0, 3)))).thenReturn(List.of());
        when(conceptRepository.findActivelyGroundedByNotebookAndUser(notebookId, userId))
                .thenReturn(List.of(first, second));
        when(masteryRepository.findByUserIdAndConceptIdIn(userId, List.of(1L, 2L))).thenReturn(List.of());
        when(answerConceptRepository.aggregateRecentEvidence(
                List.of(1L, 2L), notebookId, userId, windowEnd.minusDays(30), windowEnd)).thenReturn(List.of());

        ExamReadinessResponse response = new ExamReadinessService(notebookRepository, attemptRepository,
                conceptRepository, masteryRepository, answerConceptRepository, clock)
                .getNotebookReadiness(userId, notebookId);

        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(response.readinessScore()).isNull();
        verify(masteryRepository).findByUserIdAndConceptIdIn(userId, List.of(1L, 2L));
        verify(answerConceptRepository).aggregateRecentEvidence(
                List.of(1L, 2L), notebookId, userId, windowEnd.minusDays(30), windowEnd);
    }

    @Test
    void foreignNotebookIsMaskedAsMissing() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(12L, 11L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(java.util.NoSuchElementException.class,
                () -> new ExamReadinessService(notebookRepository, attemptRepository, conceptRepository,
                        masteryRepository, answerConceptRepository, Clock.systemUTC())
                        .getNotebookReadiness(11L, 12L));
    }

    private Concept concept(Long id, Long notebookId, Long userId) {
        User user = new User();
        user.setId(userId);
        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        notebook.setUser(user);
        Concept concept = new Concept();
        concept.setId(id);
        concept.setNotebook(notebook);
        concept.setCanonicalName("Concept " + id);
        return concept;
    }
}
