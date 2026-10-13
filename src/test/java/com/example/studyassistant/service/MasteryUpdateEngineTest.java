package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MasteryUpdateEngineTest {

    @Mock private ConceptRepository conceptRepository;
    @Mock private ConceptSourceRepository conceptSourceRepository;
    @Mock private StudentLearningEventRepository eventRepository;
    @Mock private StudentConceptMasteryRepository masteryRepository;

    private MasteryHeuristicCalculator calculator = new MasteryHeuristicCalculator();
    private MasteryUpdateEngine engine;

    private User user;
    private Notebook notebook;
    private Concept concept;

    @BeforeEach
    void setUp() {
        engine = new MasteryUpdateEngine(conceptRepository, conceptSourceRepository, eventRepository, masteryRepository, calculator);
        
        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(2L);
        notebook.setUser(user);

        concept = new Concept();
        concept.setId(3L);
        concept.setNotebook(notebook);
    }

    private MasteryEvidenceCommand createCommand(double score) {
        return new MasteryEvidenceCommand(1L, 2L, 3L, "QUIZ", score, "APP", "src1", "key1", LocalDateTime.now());
    }

    private void mockValidGrounding() {
        when(conceptRepository.findByIdWithPessimisticWrite(3L)).thenReturn(Optional.of(concept));
        when(conceptSourceRepository.existsActiveGrounding(3L, 2L, 1L)).thenReturn(true);
    }

    @Test
    void testA_ColdStart_Score1() {
        mockValidGrounding();
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key1", 3L)).thenReturn(Optional.empty());
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.empty());

        MasteryUpdateResult result = engine.applyEvidence(createCommand(1.0));

        assertThat(result.applied()).isTrue();
        assertThat(result.evidenceCount()).isEqualTo(1);
        assertThat(result.masteryEstimate()).isEqualTo(1.0);
        verify(eventRepository).save(any(StudentLearningEvent.class));
        verify(masteryRepository).save(any(StudentConceptMastery.class));
    }

    @Test
    void testB_ColdStart_Score0() {
        mockValidGrounding();
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key1", 3L)).thenReturn(Optional.empty());
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.empty());

        MasteryUpdateResult result = engine.applyEvidence(createCommand(0.0));

        assertThat(result.applied()).isTrue();
        assertThat(result.evidenceCount()).isEqualTo(1);
        assertThat(result.masteryEstimate()).isEqualTo(0.0);
    }

    @Test
    void testC_Existing1_Score0() {
        mockValidGrounding();
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key1", 3L)).thenReturn(Optional.empty());
        StudentConceptMastery existing = new StudentConceptMastery(user, concept, 1.0, 1);
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.of(existing));

        MasteryUpdateResult result = engine.applyEvidence(createCommand(0.0));

        assertThat(result.applied()).isTrue();
        assertThat(result.evidenceCount()).isEqualTo(2);
        assertThat(result.masteryEstimate()).isEqualTo(0.70);
    }

    @Test
    void testD_Existing0_Score1() {
        mockValidGrounding();
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key1", 3L)).thenReturn(Optional.empty());
        StudentConceptMastery existing = new StudentConceptMastery(user, concept, 0.0, 1);
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.of(existing));

        MasteryUpdateResult result = engine.applyEvidence(createCommand(1.0));

        assertThat(result.applied()).isTrue();
        assertThat(result.evidenceCount()).isEqualTo(2);
        assertThat(result.masteryEstimate()).isEqualTo(0.30);
    }

    @Test
    void testE_SecondAcceptedEvidence() {
        mockValidGrounding();
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key2", 3L)).thenReturn(Optional.empty());
        StudentConceptMastery existing = new StudentConceptMastery(user, concept, 0.5, 5);
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.of(existing));

        MasteryEvidenceCommand cmd = new MasteryEvidenceCommand(1L, 2L, 3L, "QUIZ", 1.0, "APP", "src1", "key2", LocalDateTime.now());
        MasteryUpdateResult result = engine.applyEvidence(cmd);

        assertThat(result.applied()).isTrue();
        assertThat(result.evidenceCount()).isEqualTo(6);
    }

    @Test
    void testF_DuplicateIdempotency() {
        mockValidGrounding();
        StudentLearningEvent existingEvent = new StudentLearningEvent(user, concept, "key1", "QUIZ", 1.0, "APP", "src1", LocalDateTime.now());
        when(eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(1L, "key1", 3L)).thenReturn(Optional.of(existingEvent));
        StudentConceptMastery existingMastery = new StudentConceptMastery(user, concept, 0.5, 5);
        when(masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(1L, 3L)).thenReturn(Optional.of(existingMastery));

        MasteryUpdateResult result = engine.applyEvidence(createCommand(1.0));

        assertThat(result.applied()).isFalse();
        assertThat(result.evidenceCount()).isEqualTo(5);
        assertThat(result.masteryEstimate()).isEqualTo(0.5);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void testM_ForeignOwnershipRejected() {
        // Concept belongs to user 99, but command is for user 1
        User foreignUser = new User();
        foreignUser.setId(99L);
        notebook.setUser(foreignUser);

        when(conceptRepository.findByIdWithPessimisticWrite(3L)).thenReturn(Optional.of(concept));

        assertThrows(MasteryConceptNotFoundException.class, () -> engine.applyEvidence(createCommand(1.0)));
        verify(conceptSourceRepository, never()).existsActiveGrounding(anyLong(), anyLong(), anyLong());
    }

    @Test
    void testN_NotebookMismatchRejected() {
        // Concept belongs to notebook 99, but command is for notebook 2
        Notebook foreignNotebook = new Notebook();
        foreignNotebook.setId(99L);
        foreignNotebook.setUser(user);
        concept.setNotebook(foreignNotebook);

        when(conceptRepository.findByIdWithPessimisticWrite(3L)).thenReturn(Optional.of(concept));

        assertThrows(MasteryConceptNotFoundException.class, () -> engine.applyEvidence(createCommand(1.0)));
    }

    @Test
    void testR_ActiveGroundingRejected() {
        when(conceptRepository.findByIdWithPessimisticWrite(3L)).thenReturn(Optional.of(concept));
        when(conceptSourceRepository.existsActiveGrounding(3L, 2L, 1L)).thenReturn(false);

        assertThrows(MasteryConceptNotFoundException.class, () -> engine.applyEvidence(createCommand(1.0)));
    }

    @Test
    void test_CommandValidation() {
        assertThrows(IllegalArgumentException.class, () -> new MasteryEvidenceCommand(null, 2L, 3L, "Q", 1.0, "S", "1", "k", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new MasteryEvidenceCommand(1L, 2L, 3L, "", 1.0, "S", "1", "k", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new MasteryEvidenceCommand(1L, 2L, 3L, "Q", 1.5, "S", "1", "k", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new MasteryEvidenceCommand(1L, 2L, 3L, "Q", -0.5, "S", "1", "k", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new MasteryEvidenceCommand(1L, 2L, 3L, "Q", Double.NaN, "S", "1", "k", LocalDateTime.now()));
    }
}
