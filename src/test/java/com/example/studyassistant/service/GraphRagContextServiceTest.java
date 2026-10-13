package com.example.studyassistant.service;

import com.example.studyassistant.entity.RelationType;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.service.TokenCounter;
import com.example.studyassistant.service.dto.GraphContextDto;
import com.example.studyassistant.service.dto.GraphContextEvidenceDto;
import com.example.studyassistant.service.dto.GraphContextRelationDto;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphRagContextServiceTest {

    @Mock
    private GraphSeedConceptService graphSeedConceptService;

    @Mock
    private GraphContextExpansionService graphContextExpansionService;

    @Mock
    private TokenCounter tokenCounter;

    @InjectMocks
    private GraphRagContextService graphRagContextService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(graphRagContextService, "enabled", true);
        ReflectionTestUtils.setField(graphRagContextService, "maxContextTokens", 1000);
        // Default token counter behavior
        lenient().when(tokenCounter.count(any())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text != null ? text.length() : 0; // Simple mock length counting
        });
    }

    @Test
    void testA_FeatureFlagOff() {
        ReflectionTestUtils.setField(graphRagContextService, "enabled", false);
        String result = graphRagContextService.buildContext(1L, List.of(new RetrievedChunk()));
        assertThat(result).isEmpty();
        verifyNoInteractions(graphSeedConceptService, graphContextExpansionService, tokenCounter);
    }

    @Test
    void testB_EmptyPrimaryChunks() {
        String result = graphRagContextService.buildContext(1L, Collections.emptyList());
        assertThat(result).isEmpty();
        verifyNoInteractions(graphSeedConceptService, graphContextExpansionService, tokenCounter);
    }

    @Test
    void testC_EmptySeeds() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        when(graphSeedConceptService.getSeedConcepts(eq(1L), eq(chunks))).thenReturn(Collections.emptyList());
        
        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).isEmpty();
        verifyNoInteractions(graphContextExpansionService);
    }

    @Test
    void testD_EmptyGraph() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);
        when(graphContextExpansionService.expandContext(1L, seeds)).thenReturn(GraphContextDto.builder().relations(Collections.emptyList()).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).isEmpty();
    }

    @Test
    void testE_GraphOnlyEvidence_Excluded() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .relationId(100L)
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(RelationType.PREREQUISITE_OF)
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(99L).evidenceText("Not primary").build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).isEmpty(); // No eligible evidence
    }

    @Test
    void testF_AlignedEvidence_Serialized() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .relationId(100L)
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(RelationType.IS_A)
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Primary evidence!").build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).contains("=== KNOWLEDGE GRAPH CONTEXT ===");
        assertThat(result).contains("A IS_A B");
        assertThat(result).contains("Primary evidence!");
        assertThat(result).contains("=== END KNOWLEDGE GRAPH CONTEXT ===");
    }

    @Test
    void testG_MixedEvidence_OnlyAlignedSerialized() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .relationId(100L)
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(RelationType.PART_OF)
                .evidence(List.of(
                        GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Aligned evidence").build(),
                        GraphContextEvidenceDto.builder().chunkId(99L).evidenceText("Graph-only evidence").build()
                ))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).contains("Aligned evidence");
        assertThat(result).doesNotContain("Graph-only evidence");
    }

    @Test
    void testP_UnsupportedRelationType_Excluded() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .relationId(100L)
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(null) // Unsupported
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Ev").build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).isEmpty();
    }

    @Test
    void testS_GraphBudgetEnforced() {
        ReflectionTestUtils.setField(graphRagContextService, "maxContextTokens", 300); 
        // 300 fits wrapper (170) + one relation (110), but not two relations (110*2 = 220)
        
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel1 = GraphContextRelationDto.builder()
                .sourceCanonicalName("A").targetCanonicalName("B").relationType(RelationType.IS_A)
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Evidence1".repeat(10)).build()))
                .build();
                
        GraphContextRelationDto rel2 = GraphContextRelationDto.builder()
                .sourceCanonicalName("C").targetCanonicalName("D").relationType(RelationType.IS_A)
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Evidence2".repeat(10)).build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel1, rel2)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).contains("Evidence1");
        assertThat(result).doesNotContain("Evidence2"); // Budget hit before rel2 could be added
    }

    @Test
    void testW_OperationalAllowedFailure() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenThrow(new RuntimeException("DB Timeout"));
        
        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).isEmpty(); // Caught and returned empty safely
    }

    @Test
    void testJ_IdsNotSerialized() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .relationId(999L)
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(RelationType.IS_A)
                .evidence(List.of(GraphContextEvidenceDto.builder().relationSourceId(888L).chunkId(10L).documentId(777L).evidenceText("Text").build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).doesNotContain("999");
        assertThat(result).doesNotContain("888");
        assertThat(result).doesNotContain("777");
    }

    @Test
    void testM_DuplicatePrimaryChunkIdsHarmless() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(10L).build(),
                RetrievedChunk.builder().chunkId(10L).build() // Duplicate
        );
        List<GraphSeedConceptDto> seeds = List.of(GraphSeedConceptDto.builder().conceptId(1L).build());
        when(graphSeedConceptService.getSeedConcepts(1L, chunks)).thenReturn(seeds);

        GraphContextRelationDto rel = GraphContextRelationDto.builder()
                .sourceCanonicalName("A")
                .targetCanonicalName("B")
                .relationType(RelationType.IS_A)
                .evidence(List.of(GraphContextEvidenceDto.builder().chunkId(10L).evidenceText("Text").build()))
                .build();
        
        when(graphContextExpansionService.expandContext(1L, seeds))
                .thenReturn(GraphContextDto.builder().relations(List.of(rel)).build());

        String result = graphRagContextService.buildContext(1L, chunks);
        assertThat(result).contains("Text");
        // Should only appear once per relation evidence
        assertThat(result.split("Text").length).isEqualTo(2); // One occurrence + wrapper split
    }
}
