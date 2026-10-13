package com.example.studyassistant.service;

import com.example.studyassistant.repository.KnowledgeGraphQueryRepository;
import com.example.studyassistant.repository.projection.GraphSeedCandidateProjection;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphSeedConceptServiceTest {

    @Mock
    private KnowledgeGraphQueryRepository queryRepository;

    @InjectMocks
    private GraphSeedConceptService service;

    private final Long notebookId = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxSeedConcepts", 5);
    }

    private GraphSeedCandidateProjection createCandidate(Long conceptId, Long chunkId, Double confidence) {
        return new GraphSeedCandidateProjection() {
            @Override public Long getConceptId() { return conceptId; }
            @Override public String getCanonicalName() { return "Concept " + conceptId; }
            @Override public String getConceptType() { return "TYPE"; }
            @Override public Long getChunkId() { return chunkId; }
            @Override public Double getConceptSourceConfidence() { return confidence; }
        };
    }

    @Test
    void testA_emptyRetrievedChunks_empty() {
        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, Collections.emptyList());
        assertThat(seeds).isEmpty();
        verifyNoInteractions(queryRepository); // O
    }

    @Test
    void testB_retrievedChunksButNoConceptSources_empty() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), any())).thenReturn(Collections.emptyList());

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).isEmpty();
    }

    @Test
    void testC_oneChunkOneConcept_oneSeed() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(createCandidate(1L, 10L, 0.9)));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(1L);
        assertThat(seeds.get(0).getBestRetrievalScore()).isEqualTo(0.8);
        assertThat(seeds.get(0).getBestSourceConfidence()).isEqualTo(0.9);
        assertThat(seeds.get(0).getSupportingChunkCount()).isEqualTo(1);
    }

    @Test
    void testD_oneChunkMultipleConcepts_deterministicRanking() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(2L, 10L, 0.5),
                        createCandidate(1L, 10L, 0.9) // Higher confidence
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(2);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(1L); // Confidence 0.9 > 0.5
        assertThat(seeds.get(1).getConceptId()).isEqualTo(2L);
    }

    @Test
    void testE_sameConceptSupportedByMultipleChunks_oneSeed() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(10L).rrfScore(0.7).build(),
                RetrievedChunk.builder().chunkId(11L).rrfScore(0.9).build()
        );
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L, 11L))))
                .thenReturn(List.of(
                        createCandidate(1L, 10L, 0.8),
                        createCandidate(1L, 11L, 0.5)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(1);
        
        GraphSeedConceptDto seed = seeds.get(0);
        assertThat(seed.getBestRetrievalScore()).isEqualTo(0.9); // max RRF
        assertThat(seed.getBestSourceConfidence()).isEqualTo(0.8); // max conf
        assertThat(seed.getSupportingChunkCount()).isEqualTo(2); // F
        
        verify(queryRepository, times(1)).findSeedCandidatesByChunkIds(any(), any()); // N
    }

    @Test
    void testG_bestRetrievalScoreRankChosenCorrectly() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(10L).denseRank(3).build(),
                RetrievedChunk.builder().chunkId(11L).denseRank(1).build()
        );
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L, 11L))))
                .thenReturn(List.of(
                        createCandidate(1L, 10L, 0.8),
                        createCandidate(1L, 11L, 0.8)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getBestRetrievalRank()).isEqualTo(1);
    }

    @Test
    void testH_confidenceTieBreakWorks() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(2L, 10L, 0.9), // Highest confidence
                        createCandidate(1L, 10L, 0.8)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(2L);
    }

    @Test
    void testI_nullConfidenceSortsLastOnTie() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(1L, 10L, null),
                        createCandidate(2L, 10L, 0.1)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(2L); // 0.1 > null
        assertThat(seeds.get(1).getConceptId()).isEqualTo(1L);
    }

    @Test
    void testJ_conceptIdFinalTieBreakDeterministic() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(2L, 10L, 0.8),
                        createCandidate(1L, 10L, 0.8)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(1L);
        assertThat(seeds.get(1).getConceptId()).isEqualTo(2L);
    }

    @Test
    void testK_maxSeedConceptsEnforced() {
        ReflectionTestUtils.setField(service, "maxSeedConcepts", 2);
        
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(1L, 10L, 0.8),
                        createCandidate(2L, 10L, 0.8),
                        createCandidate(3L, 10L, 0.8)
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(2);
    }

    @Test
    void testL_duplicateRetrievedChunkIdDoesNotInflateSupportCount() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build(),
                RetrievedChunk.builder().chunkId(10L).rrfScore(0.8).build()
        );
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(createCandidate(1L, 10L, 0.8)));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getSupportingChunkCount()).isEqualTo(1);
    }

    @Test
    void testM_repositoryCandidateOutsideTrustedChunkSetIgnored() {
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(10L).build());
        when(queryRepository.findSeedCandidatesByChunkIds(eq(notebookId), eq(Set.of(10L))))
                .thenReturn(List.of(
                        createCandidate(1L, 10L, 0.8),
                        createCandidate(2L, 999L, 0.8) // Untrusted chunk ID
                ));

        List<GraphSeedConceptDto> seeds = service.getSeedConcepts(notebookId, chunks);
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(1L);
    }
}
