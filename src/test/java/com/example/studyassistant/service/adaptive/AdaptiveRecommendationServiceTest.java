package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.adaptive.AdaptiveReasonCode;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveRecommendationServiceTest {

    @Mock
    private AdaptiveCandidateService candidateService;

    private AdaptivePolicyProperties properties;
    private AdaptiveRecommendationService service;

    @BeforeEach
    void setUp() {
        properties = new AdaptivePolicyProperties();
        properties.setMasteredThreshold(0.80);
        properties.setMasteredMinEvidence(2);
        properties.setDefaultLimit(5);
        properties.setMaxLimit(20);

        service = new AdaptiveRecommendationService(candidateService, properties, new AdaptiveMasteryPolicy(properties));
    }

    private AdaptiveCandidateFactDto createFact(Long id, MasteryState state, Double estimate, int count, Long docId, Integer chunkIdx, List<Long> prereqs) {
        return new AdaptiveCandidateFactDto(id, "Concept " + id, state, estimate, count, null, docId, chunkIdx, prereqs);
    }

    // --- MASTERED PREDICATE TESTS ---

    @Test
    void testMastered_P1_Unseen() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.UNSEEN, null, 0, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.UNSEEN_CONCEPT);
    }

    @Test
    void testMastered_P2_BelowThreshold() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.799, 2, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD);
    }

    @Test
    void testMastered_P3_BelowEvidence() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.800, 1, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY);
    }

    @Test
    void testMastered_P4_Mastered() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.800, 2, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void testMastered_P5_AboveMastered() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.801, 2, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void testMastered_P6_OneShot() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 1.0, 1, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY);
    }

    @Test
    void testMastered_ConfigOverride() {
        properties.setMasteredThreshold(0.90);
        properties.setMasteredMinEvidence(3);
        
        // 0.85 / 2 -> Below both thresholds under new config
        AdaptiveCandidateFactDto notMastered = createFact(1L, MasteryState.OBSERVED, 0.85, 2, null, null, null);
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(notMastered));
        
        List<AdaptiveRecommendationDto> results1 = service.getRecommendations(1L, 1L, 10);
        assertThat(results1).hasSize(1);
        assertThat(results1.get(0).getReasonCodes()).containsExactlyInAnyOrder(
                AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD,
                AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY
        );

        // 0.90 / 3 -> Mastered under new config
        AdaptiveCandidateFactDto mastered = createFact(2L, MasteryState.OBSERVED, 0.90, 3, null, null, null);
        when(candidateService.getCandidateFacts(2L, 2L)).thenReturn(List.of(mastered));
        
        List<AdaptiveRecommendationDto> results2 = service.getRecommendations(2L, 2L, 10);
        assertThat(results2).isEmpty();
    }

    @Test
    void testLimit_ConfigOverride() {
        properties.setDefaultLimit(2);
        properties.setMaxLimit(3);
        
        List<AdaptiveCandidateFactDto> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(createFact((long) i, MasteryState.OBSERVED, 0.5, 5, null, null, null));
        }
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(many);
        
        assertThat(service.getRecommendations(1L, 1L, null)).hasSize(2);
        assertThat(service.getRecommendations(1L, 1L, 0)).hasSize(2);
        assertThat(service.getRecommendations(1L, 1L, 99)).hasSize(3);
    }

    // --- REASON TESTS ---

    @Test
    void testReasons_R2_BelowBoth() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.50, 1, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(
                AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD,
                AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY
        );
    }

    @Test
    void testReasons_R4_BelowThresholdOnly() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.50, 5, null, null, null)));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD);
    }

    // --- PREREQUISITE TESTS ---

    @Test
    void testPrereq_G2_UnseenPrereq_BlocksDependent() {
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.UNSEEN, null, 0, null, null, null);
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.50, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(1L); // Only A is eligible
    }

    @Test
    void testPrereq_G3_OneShotPrereq_BlocksDependent() {
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 1.0, 1, null, null, null);
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.50, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(1L); // Only A is eligible
    }

    @Test
    void testPrereq_G4_MasteredPrereq_UnblocksDependent() {
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.80, 2, null, null, null);
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.50, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(2L); // A is mastered, B is eligible
    }

    @Test
    void testPrereq_G6_MultiplePrereqs_OneFails_Blocks() {
        AdaptiveCandidateFactDto p1 = createFact(1L, MasteryState.OBSERVED, 0.80, 2, null, null, null);
        AdaptiveCandidateFactDto p2 = createFact(2L, MasteryState.OBSERVED, 0.70, 5, null, null, null);
        AdaptiveCandidateFactDto dep = createFact(3L, MasteryState.UNSEEN, null, 0, null, null, List.of(1L, 2L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(p1, p2, dep));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(2L); // Only p2 is eligible
    }

    // --- SCC TESTS ---

    @Test
    void testSCC_C1_MutualCycle_NoExternal() {
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(2L));
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(2);
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void testSCC_C2_SelfLoop() {
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(1L);
    }

    @Test
    void testSCC_C3_ExternalMastered_UnblocksCycle() {
        AdaptiveCandidateFactDto x = createFact(3L, MasteryState.OBSERVED, 0.8, 2, null, null, null);
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(2L, 3L));
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(x, a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(2); // A and B are eligible
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void testSCC_C4_ExternalFails_BlocksCycle() {
        AdaptiveCandidateFactDto x = createFact(3L, MasteryState.OBSERVED, 0.5, 5, null, null, null);
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(2L, 3L));
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.5, 5, null, null, List.of(1L));
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(x, a, b));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).hasSize(2); // X and B are eligible (B's internal edge is ignored, A is blocked by X)
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(3L, 2L);
    }

    // --- RANKING TESTS ---

    @Test
    void testRanking_O1_O2_O3_O4_O5() {
        // A: OBSERVED 0.20 count 5 (Top priority)
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.OBSERVED, 0.20, 5, 1L, 1, null);
        
        // B: OBSERVED 0.80 count 1 (Lower priority because 0.80 > 0.20)
        AdaptiveCandidateFactDto b = createFact(2L, MasteryState.OBSERVED, 0.80, 1, 1L, 1, null);
        
        // C: OBSERVED 0.20 count 1 (Lower priority than A because count 1 < 5)
        AdaptiveCandidateFactDto c = createFact(3L, MasteryState.OBSERVED, 0.20, 1, 1L, 1, null);
        
        // D: UNSEEN (After all observed)
        AdaptiveCandidateFactDto d = createFact(4L, MasteryState.UNSEEN, null, 0, 1L, 1, null);
        
        // E: OBSERVED 0.20 count 5 (Tie with A, break with DocId)
        AdaptiveCandidateFactDto e = createFact(5L, MasteryState.OBSERVED, 0.20, 5, 2L, 1, null);
        
        List<AdaptiveCandidateFactDto> shuffled = Arrays.asList(d, c, e, b, a);
        Collections.shuffle(shuffled);
        
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(shuffled);
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        
        // Expected order: A (1L), E (5L), C (3L), B (2L), D (4L)
        assertThat(results).extracting("conceptId").containsExactly(1L, 5L, 3L, 2L, 4L);
    }

    // --- LIMIT TESTS ---

    @Test
    void testLimit_L1_L2_L3_L4_L6() {
        List<AdaptiveCandidateFactDto> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(createFact((long) i, MasteryState.OBSERVED, 0.5, 5, null, null, null));
        }
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(many);
        
        assertThat(service.getRecommendations(1L, 1L, null)).hasSize(5); // null -> default
        assertThat(service.getRecommendations(1L, 1L, 0)).hasSize(5); // 0 -> default
        assertThat(service.getRecommendations(1L, 1L, -5)).hasSize(5); // neg -> default
        assertThat(service.getRecommendations(1L, 1L, 1)).hasSize(1); // 1 -> 1
        assertThat(service.getRecommendations(1L, 1L, 20)).hasSize(20); // 20 -> 20
        assertThat(service.getRecommendations(1L, 1L, 21)).hasSize(20); // 21 -> capped at 20
    }

    // --- INVARIANT TESTS ---

    @Test
    void testInvariant_E4_NullEstimateForObserved() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, null, 1, null, null, null)));
        
        assertThatThrownBy(() -> service.getRecommendations(1L, 1L, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void testInvariant_E5_NegativeEvidenceCount() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(createFact(1L, MasteryState.OBSERVED, 0.5, -1, null, null, null)));
        
        assertThatThrownBy(() -> service.getRecommendations(1L, 1L, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void testInvariant_E7_DuplicateConceptId() {
        when(candidateService.getCandidateFacts(1L, 1L))
                .thenReturn(List.of(
                        createFact(1L, MasteryState.UNSEEN, null, 0, null, null, null),
                        createFact(1L, MasteryState.UNSEEN, null, 0, null, null, null)
                ));
        
        assertThatThrownBy(() -> service.getRecommendations(1L, 1L, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate concept ID");
    }

    @Test
    void testInvariant_E6_UnknownPrerequisite() {
        // Concept 1 depends on Concept 99 (which isn't in facts list).
        // It should be treated as NOT MASTERED safely.
        AdaptiveCandidateFactDto a = createFact(1L, MasteryState.UNSEEN, null, 0, null, null, List.of(99L));
        when(candidateService.getCandidateFacts(1L, 1L)).thenReturn(List.of(a));
        
        List<AdaptiveRecommendationDto> results = service.getRecommendations(1L, 1L, 10);
        assertThat(results).isEmpty(); // Blocked by unknown prereq
    }
}
