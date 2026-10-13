package com.example.studyassistant.service;

import com.example.studyassistant.entity.RelationType;
import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.projection.GraphContextEvidenceProjection;
import com.example.studyassistant.repository.projection.GraphContextRelationProjection;
import com.example.studyassistant.service.dto.GraphContextDto;
import com.example.studyassistant.service.dto.GraphContextRelationDto;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphContextExpansionServiceTest {

    @Mock
    private KnowledgeGraphQueryRepository queryRepository;

    @InjectMocks
    private GraphContextExpansionService service;

    private final Long notebookId = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxRelations", 10);
        ReflectionTestUtils.setField(service, "maxEvidencePerRelation", 2);
    }

    private GraphSeedConceptDto seed(Long id) {
        return GraphSeedConceptDto.builder().conceptId(id).build();
    }

    private GraphContextRelationProjection relation(Long relId, Long srcId, Long tgtId, RelationType type, Double conf, Long evCount) {
        return new GraphContextRelationProjection() {
            @Override public Long getRelationId() { return relId; }
            @Override public Long getSourceConceptId() { return srcId; }
            @Override public String getSourceCanonicalName() { return "SRC"; }
            @Override public Long getTargetConceptId() { return tgtId; }
            @Override public String getTargetCanonicalName() { return "TGT"; }
            @Override public RelationType getRelationType() { return type; }
            @Override public Double getRelationConfidence() { return conf; }
            @Override public Long getActiveEvidenceCount() { return evCount; }
        };
    }

    private GraphContextEvidenceProjection evidence(Long relId, Long srcId, Long chunkId, Double conf) {
        return new GraphContextEvidenceProjection() {
            @Override public Long getRelationId() { return relId; }
            @Override public Long getRelationSourceId() { return srcId; }
            @Override public Long getChunkId() { return chunkId; }
            @Override public Long getDocumentId() { return 1L; }
            @Override public String getEvidenceText() { return "TXT"; }
            @Override public Double getConfidence() { return conf; }
            @Override public Integer getPageStart() { return 1; }
            @Override public Integer getPageEnd() { return 1; }
        };
    }

    @Test
    void testA_emptySeeds() {
        GraphContextDto ctx = service.expandContext(notebookId, Collections.emptyList());
        assertThat(ctx.getSeeds()).isEmpty();
        verifyNoInteractions(queryRepository);
    }

    @Test
    void testB_noRelations() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations()).isEmpty();
        verify(queryRepository, never()).findGraphContextEvidence(any(), any());
    }

    @Test
    void testCD_outgoingAndIncomingPreserved() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L), // outgoing
                        relation(11L, 2L, 1L, RelationType.PART_OF, 0.9, 1L) // incoming
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations()).hasSize(2);
    }

    @Test
    void testE_relationTouchingTwoSeedsReturnedOnce() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L, 2L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L),
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L) // duplicate from repo if it happens
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L), seed(2L)));
        assertThat(ctx.getRelations()).hasSize(1);
    }

    @Test
    void testFG_unrelatedAndUnsupportedIgnored() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L),
                        relation(11L, 99L, 100L, RelationType.IS_A, 0.9, 1L), // F
                        relation(12L, 1L, 2L, null, 0.9, 1L) // G
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations()).hasSize(1);
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(10L);
    }

    @Test
    void testH_strongerTypeBeatsRelatedTo() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.RELATED_TO, 0.9, 1L),
                        relation(11L, 1L, 3L, RelationType.IS_A, 0.5, 1L) // weaker conf but better type
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(11L);
    }

    @Test
    void testI_twoSeedBridgeBeatsOneSeedEdge() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L, 2L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 3L, RelationType.IS_A, 0.9, 1L), // one seed
                        relation(11L, 1L, 2L, RelationType.IS_A, 0.9, 1L) // two seeds
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L), seed(2L)));
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(11L);
    }

    @Test
    void testJ_betterSeedRankWins() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L, 2L))))
                .thenReturn(List.of(
                        relation(10L, 2L, 3L, RelationType.IS_A, 0.9, 1L), // connected to seed rank 2
                        relation(11L, 1L, 4L, RelationType.IS_A, 0.9, 1L) // connected to seed rank 1
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L), seed(2L)));
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(11L);
    }

    @Test
    void testKL_confidenceTieBreaksAndNullsLast() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, null, 1L),
                        relation(11L, 1L, 3L, RelationType.IS_A, 0.8, 1L),
                        relation(12L, 1L, 4L, RelationType.IS_A, 0.9, 1L)
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(12L);
        assertThat(ctx.getRelations().get(1).getRelationId()).isEqualTo(11L);
        assertThat(ctx.getRelations().get(2).getRelationId()).isEqualTo(10L);
    }

    @Test
    void testMN_activeEvidenceCountAndIdFinalTieBreak() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L),
                        relation(11L, 1L, 3L, RelationType.IS_A, 0.9, 2L),
                        relation(9L, 1L, 4L, RelationType.IS_A, 0.9, 1L)
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations().get(0).getRelationId()).isEqualTo(11L); // higher count
        assertThat(ctx.getRelations().get(1).getRelationId()).isEqualTo(9L); // same count, lower id
    }

    @Test
    void testO_maxRelationsEnforced() {
        ReflectionTestUtils.setField(service, "maxRelations", 2);
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L),
                        relation(11L, 1L, 3L, RelationType.IS_A, 0.8, 1L),
                        relation(12L, 1L, 4L, RelationType.IS_A, 0.7, 1L)
                ));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations()).hasSize(2);
    }

    @Test
    void testPQR_evidenceGroupedAndLimitedAndOrdered() {
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L)));
        when(queryRepository.findGraphContextEvidence(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        evidence(10L, 101L, 50L, 0.5),
                        evidence(10L, 102L, 51L, 0.9), // highest conf
                        evidence(10L, 103L, 52L, 0.7)
                ));
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations().get(0).getEvidence()).hasSize(2);
        assertThat(ctx.getRelations().get(0).getEvidence().get(0).getRelationSourceId()).isEqualTo(102L);
    }

    @Test
    void testSTU_nullConfLastTieBreaksDuplicateIgnored() {
        ReflectionTestUtils.setField(service, "maxEvidencePerRelation", 10);
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L)));
        when(queryRepository.findGraphContextEvidence(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        evidence(10L, 101L, 50L, null),
                        evidence(10L, 101L, 50L, null), // duplicate source ID
                        evidence(10L, 102L, 49L, null)
                ));
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations().get(0).getEvidence()).hasSize(2);
        assertThat(ctx.getRelations().get(0).getEvidence().get(0).getRelationSourceId()).isEqualTo(102L); // lower chunk id
        assertThat(ctx.getRelations().get(0).getEvidence().get(1).getRelationSourceId()).isEqualTo(101L);
    }

    @Test
    void testV_evidenceForNonSelectedRelationIgnored() {
        ReflectionTestUtils.setField(service, "maxRelations", 1);
        when(queryRepository.findGraphContextRelations(eq(notebookId), eq(Set.of(1L))))
                .thenReturn(List.of(
                        relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L), // selected
                        relation(11L, 1L, 3L, RelationType.IS_A, 0.5, 1L)  // discarded
                ));
        // repo somehow returns evidence for both
        when(queryRepository.findGraphContextEvidence(any(), any()))
                .thenReturn(List.of(
                        evidence(10L, 101L, 50L, 0.9),
                        evidence(11L, 102L, 51L, 0.9)
                ));
        GraphContextDto ctx = service.expandContext(notebookId, List.of(seed(1L)));
        assertThat(ctx.getRelations()).hasSize(1);
        assertThat(ctx.getRelations().get(0).getEvidence()).hasSize(1);
        assertThat(ctx.getRelations().get(0).getEvidence().get(0).getRelationId()).isEqualTo(10L);
    }
    
    @Test
    void testWX_queriesCalledOnce() {
        when(queryRepository.findGraphContextRelations(any(), any()))
                .thenReturn(List.of(relation(10L, 1L, 2L, RelationType.IS_A, 0.9, 1L)));
        when(queryRepository.findGraphContextEvidence(any(), any())).thenReturn(Collections.emptyList());
        service.expandContext(notebookId, List.of(seed(1L), seed(2L), seed(3L)));
        verify(queryRepository, times(1)).findGraphContextRelations(any(), any());
        verify(queryRepository, times(1)).findGraphContextEvidence(any(), any());
    }
}
