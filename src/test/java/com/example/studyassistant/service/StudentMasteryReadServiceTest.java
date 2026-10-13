package com.example.studyassistant.service;

import com.example.studyassistant.dto.ConceptMasteryDetailDto;
import com.example.studyassistant.dto.ConceptMasteryItemDto;
import com.example.studyassistant.dto.MasteryPageDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.StudentMasteryReadRepository;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryDetailProjection;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentMasteryReadServiceTest {

    @Mock
    private StudentMasteryReadRepository repository;

    @InjectMocks
    private StudentMasteryReadService service;

    private GroundedConceptMasteryProjection unseenProjection;
    private GroundedConceptMasteryProjection observedProjection;
    private GroundedConceptMasteryDetailProjection unseenDetailProjection;
    private GroundedConceptMasteryDetailProjection observedDetailProjection;

    @BeforeEach
    void setUp() {
        unseenProjection = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 1L; }
            @Override public String getCanonicalName() { return "Unseen Concept"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return null; }
            @Override public Double getMasteryEstimate() { return null; }
            @Override public Integer getEvidenceCount() { return null; }
            @Override public LocalDateTime getLastEvidenceAt() { return null; }
        };

        observedProjection = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 2L; }
            @Override public String getCanonicalName() { return "Observed Concept"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return 100L; }
            @Override public Double getMasteryEstimate() { return 0.75; }
            @Override public Integer getEvidenceCount() { return 5; }
            @Override public LocalDateTime getLastEvidenceAt() { return LocalDateTime.of(2026, 1, 1, 10, 0); }
        };

        unseenDetailProjection = new GroundedConceptMasteryDetailProjection() {
            @Override public Long getConceptId() { return 1L; }
            @Override public String getCanonicalName() { return "Unseen Concept"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public String getDescription() { return "Desc unseen"; }
            @Override public Long getMasteryId() { return null; }
            @Override public Double getMasteryEstimate() { return null; }
            @Override public Integer getEvidenceCount() { return null; }
            @Override public LocalDateTime getLastEvidenceAt() { return null; }
        };

        observedDetailProjection = new GroundedConceptMasteryDetailProjection() {
            @Override public Long getConceptId() { return 2L; }
            @Override public String getCanonicalName() { return "Observed Concept"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public String getDescription() { return "Desc observed"; }
            @Override public Long getMasteryId() { return 100L; }
            @Override public Double getMasteryEstimate() { return 0.75; }
            @Override public Integer getEvidenceCount() { return 5; }
            @Override public LocalDateTime getLastEvidenceAt() { return LocalDateTime.of(2026, 1, 1, 10, 0); }
        };
    }

    @Test
    void testCaseA_ListProjectionMasteryIdNull() {
        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(unseenProjection)));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));

        assertThat(result.getItems()).hasSize(1);
        ConceptMasteryItemDto dto = result.getItems().get(0);
        assertThat(dto.getState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(dto.getMasteryEstimate()).isNull();
        assertThat(dto.getEvidenceCount()).isEqualTo(0);
        assertThat(dto.getLastEvidenceAt()).isNull();
    }

    @Test
    void testCaseB_ListProjectionMasteryIdPresent() {
        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(observedProjection)));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));

        assertThat(result.getItems()).hasSize(1);
        ConceptMasteryItemDto dto = result.getItems().get(0);
        assertThat(dto.getState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(dto.getMasteryEstimate()).isEqualTo(0.75);
        assertThat(dto.getEvidenceCount()).isEqualTo(5);
        assertThat(dto.getLastEvidenceAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void testCaseC_ObservedZeroEvidence() {
        GroundedConceptMasteryProjection zeroEvidence = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 3L; }
            @Override public String getCanonicalName() { return "Zero"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return 101L; }
            @Override public Double getMasteryEstimate() { return 0.0; }
            @Override public Integer getEvidenceCount() { return 0; }
            @Override public LocalDateTime getLastEvidenceAt() { return null; }
        };

        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(zeroEvidence)));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));
        ConceptMasteryItemDto dto = result.getItems().get(0);
        assertThat(dto.getState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(dto.getMasteryEstimate()).isEqualTo(0.0);
        assertThat(dto.getEvidenceCount()).isEqualTo(0);
    }

    @Test
    void testCaseD_MultipleProjectionsOrderPreserved() {
        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(unseenProjection, observedProjection)));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getConceptId()).isEqualTo(1L);
        assertThat(result.getItems().get(1).getConceptId()).isEqualTo(2L);
    }

    @Test
    void testCaseE_EmptyRepositoryList() {
        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(Collections.emptyList()));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void testCaseF_DetailUnseen() {
        when(repository.findGroundedConceptMasteryDetail(1L, 10L, 1L))
                .thenReturn(Optional.of(unseenDetailProjection));

        ConceptMasteryDetailDto result = service.getConceptMastery(1L, 10L, 1L);

        assertThat(result.getState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(result.getMasteryEstimate()).isNull();
        assertThat(result.getEvidenceCount()).isEqualTo(0);
        assertThat(result.getLastEvidenceAt()).isNull();
    }

    @Test
    void testCaseG_DetailObserved() {
        when(repository.findGroundedConceptMasteryDetail(1L, 10L, 2L))
                .thenReturn(Optional.of(observedDetailProjection));

        ConceptMasteryDetailDto result = service.getConceptMastery(1L, 10L, 2L);

        assertThat(result.getState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(result.getMasteryEstimate()).isEqualTo(0.75);
        assertThat(result.getEvidenceCount()).isEqualTo(5);
    }

    @Test
    void testCaseH_DetailRepositoryEmpty() {
        when(repository.findGroundedConceptMasteryDetail(1L, 10L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConceptMastery(1L, 10L, 99L))
                .isInstanceOf(MasteryConceptNotFoundException.class)
                .hasMessage("Concept mastery not found or not accessible");
    }

    @Test
    void testCaseI_ObservedMasteryEstimateNull() {
        GroundedConceptMasteryProjection badProjection = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 1L; }
            @Override public String getCanonicalName() { return "Bad"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return 100L; }
            @Override public Double getMasteryEstimate() { return null; }
            @Override public Integer getEvidenceCount() { return 5; }
            @Override public LocalDateTime getLastEvidenceAt() { return null; }
        };

        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(badProjection)));

        assertThatThrownBy(() -> service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("masteryEstimate or evidenceCount is null");
    }

    @Test
    void testCaseJ_ObservedEvidenceCountNull() {
        GroundedConceptMasteryProjection badProjection = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 1L; }
            @Override public String getCanonicalName() { return "Bad"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return 100L; }
            @Override public Double getMasteryEstimate() { return 0.5; }
            @Override public Integer getEvidenceCount() { return null; }
            @Override public LocalDateTime getLastEvidenceAt() { return null; }
        };

        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(badProjection)));

        assertThatThrownBy(() -> service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCaseK_UnseenProjectionWithUnexpectedValues() {
        GroundedConceptMasteryProjection weirdProjection = new GroundedConceptMasteryProjection() {
            @Override public Long getConceptId() { return 1L; }
            @Override public String getCanonicalName() { return "Weird"; }
            @Override public String getConceptType() { return "CONCEPT"; }
            @Override public Long getMasteryId() { return null; }
            @Override public Double getMasteryEstimate() { return 0.99; } // should be ignored
            @Override public Integer getEvidenceCount() { return 10; } // should be ignored
            @Override public LocalDateTime getLastEvidenceAt() { return LocalDateTime.now(); } // should be ignored
        };

        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(weirdProjection)));

        MasteryPageDto<ConceptMasteryItemDto> result = service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));

        assertThat(result.getItems()).hasSize(1);
        ConceptMasteryItemDto dto = result.getItems().get(0);
        assertThat(dto.getState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(dto.getMasteryEstimate()).isNull();
        assertThat(dto.getEvidenceCount()).isEqualTo(0);
        assertThat(dto.getLastEvidenceAt()).isNull();
    }

    @Test
    void testCaseL_RepositoryInvocationCount() {
        when(repository.findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(Collections.emptyList()));
        service.getNotebookMastery(1L, 10L, PageRequest.of(0, 50));
        verify(repository, times(1)).findGroundedConceptMasteriesByNotebookId(eq(1L), eq(10L), any(Pageable.class));
        
        when(repository.findGroundedConceptMasteryDetail(1L, 10L, 2L))
                .thenReturn(Optional.of(observedDetailProjection));
        service.getConceptMastery(1L, 10L, 2L);
        verify(repository, times(1)).findGroundedConceptMasteryDetail(1L, 10L, 2L);
    }
}
