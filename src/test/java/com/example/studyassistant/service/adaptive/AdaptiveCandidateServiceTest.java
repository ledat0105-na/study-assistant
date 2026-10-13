package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.adaptive.AdaptiveCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdaptiveCandidateServiceTest {

    @Mock
    private AdaptiveCandidateRepository repository;

    @Mock
    private NotebookRepository notebookRepository;

    @InjectMocks
    private AdaptiveCandidateService service;

    private User owner;
    private Notebook notebook;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);

        notebook = new Notebook();
        notebook.setId(100L);
        notebook.setUser(owner);
    }

    @Test
    void testForeignNotebookIsMasked() {
        // User 99 tries to access User 10's notebook
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(99L, 100L);

        assertThat(facts).isEmpty();
        verify(repository, never()).findCandidateFacts(anyLong(), anyLong());
    }

    @Test
    void testMissingNotebookIsMasked() {
        when(notebookRepository.findById(999L)).thenReturn(Optional.empty());

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 999L);

        assertThat(facts).isEmpty();
        verify(repository, never()).findCandidateFacts(anyLong(), anyLong());
    }

    @Test
    void testEmptyOwnedNotebook() {
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));
        when(repository.findCandidateFacts(100L, 10L)).thenReturn(Collections.emptyList());
        when(repository.findActivePrerequisiteEdges(100L)).thenReturn(Collections.emptyList());
        when(repository.findActiveSourceOrderFacts(100L)).thenReturn(Collections.emptyList());

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 100L);

        assertThat(facts).isEmpty();
    }

    @Test
    void testGroundedUnseenConcept() {
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));

        // Returns one UNSEEN candidate: mastery fields are null
        when(repository.findCandidateFacts(100L, 10L)).thenReturn(Collections.singletonList(
                new Object[]{1L, "Concept A", null, null, null}
        ));
        
        when(repository.findActivePrerequisiteEdges(100L)).thenReturn(Collections.emptyList());
        
        when(repository.findActiveSourceOrderFacts(100L)).thenReturn(Collections.singletonList(
                new Object[]{1L, 20L, 5}
        ));

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 100L);

        assertThat(facts).hasSize(1);
        AdaptiveCandidateFactDto dto = facts.get(0);
        assertThat(dto.getConceptId()).isEqualTo(1L);
        assertThat(dto.getConceptName()).isEqualTo("Concept A");
        assertThat(dto.getMasteryState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(dto.getMasteryEstimate()).isNull();
        assertThat(dto.getEvidenceCount()).isEqualTo(0);
        assertThat(dto.getFirstDocumentId()).isEqualTo(20L);
        assertThat(dto.getFirstChunkIndex()).isEqualTo(5);
        assertThat(dto.getPrerequisiteConceptIds()).isEmpty();
    }

    @Test
    void testGroundedObservedConcept() {
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));

        LocalDateTime now = LocalDateTime.now();
        when(repository.findCandidateFacts(100L, 10L)).thenReturn(Collections.singletonList(
                new Object[]{2L, "Concept B", 0.75, 3, now}
        ));
        
        when(repository.findActivePrerequisiteEdges(100L)).thenReturn(Collections.emptyList());
        when(repository.findActiveSourceOrderFacts(100L)).thenReturn(Collections.emptyList());

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 100L);

        assertThat(facts).hasSize(1);
        AdaptiveCandidateFactDto dto = facts.get(0);
        assertThat(dto.getConceptId()).isEqualTo(2L);
        assertThat(dto.getMasteryState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(dto.getMasteryEstimate()).isEqualTo(0.75);
        assertThat(dto.getEvidenceCount()).isEqualTo(3);
        assertThat(dto.getLastEvidenceAt()).isEqualTo(now);
    }

    @Test
    void testPrerequisiteDirectionAndDeduplication() {
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));

        when(repository.findCandidateFacts(100L, 10L)).thenReturn(java.util.Arrays.asList(
                new Object[]{1L, "PreReq", null, null, null},
                new Object[]{2L, "Dependent", null, null, null}
        ));
        
        // Edge: A is prerequisite_of B. target = 2, source = 1
        when(repository.findActivePrerequisiteEdges(100L)).thenReturn(java.util.Arrays.asList(
                new Object[]{2L, 1L},
                new Object[]{2L, 1L} // Duplicate provenance should be deduplicated by the service's Set
        ));
        
        when(repository.findActiveSourceOrderFacts(100L)).thenReturn(Collections.emptyList());

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 100L);

        assertThat(facts).hasSize(2);
        
        // Results are sorted by conceptId
        AdaptiveCandidateFactDto preReq = facts.get(0);
        assertThat(preReq.getConceptId()).isEqualTo(1L);
        assertThat(preReq.getPrerequisiteConceptIds()).isEmpty(); // A does not depend on B

        AdaptiveCandidateFactDto dependent = facts.get(1);
        assertThat(dependent.getConceptId()).isEqualTo(2L);
        assertThat(dependent.getPrerequisiteConceptIds()).containsExactly(1L); // B depends on A, deduplicated
    }

    @Test
    void testDeterministicSourceLocatorAndOrdering() {
        when(notebookRepository.findById(100L)).thenReturn(Optional.of(notebook));

        // Unordered concepts
        when(repository.findCandidateFacts(100L, 10L)).thenReturn(java.util.Arrays.asList(
                new Object[]{3L, "Concept C", null, null, null},
                new Object[]{1L, "Concept A", null, null, null}
        ));
        
        when(repository.findActivePrerequisiteEdges(100L)).thenReturn(Collections.emptyList());
        
        // Multiple source facts for Concept 1
        when(repository.findActiveSourceOrderFacts(100L)).thenReturn(java.util.Arrays.asList(
                new Object[]{1L, 50L, 2}, // Doc 50, Chunk 2
                new Object[]{1L, 20L, 8}, // Doc 20, Chunk 8 (Should win because 20 < 50)
                new Object[]{1L, 20L, 9}, // Doc 20, Chunk 9 (Loses to 8)
                new Object[]{3L, 30L, 1}
        ));

        List<AdaptiveCandidateFactDto> facts = service.getCandidateFacts(10L, 100L);

        // Deterministic ordering by Concept ID
        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).getConceptId()).isEqualTo(1L);
        assertThat(facts.get(1).getConceptId()).isEqualTo(3L);

        // Deterministic source locator for Concept 1 (min doc, then min chunk)
        assertThat(facts.get(0).getFirstDocumentId()).isEqualTo(20L);
        assertThat(facts.get(0).getFirstChunkIndex()).isEqualTo(8);

        assertThat(facts.get(1).getFirstDocumentId()).isEqualTo(30L);
        assertThat(facts.get(1).getFirstChunkIndex()).isEqualTo(1);
    }
}
