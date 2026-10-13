package com.example.studyassistant.service.tutor;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.service.adaptive.AdaptiveMasteryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TutorPolicyServiceTest {

    private TutorPolicyService tutorPolicyService;
    private AdaptivePolicyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AdaptivePolicyProperties();
        properties.setMasteredThreshold(0.80);
        properties.setMasteredMinEvidence(2);
        
        AdaptiveMasteryPolicy masteryPolicy = new AdaptiveMasteryPolicy(properties);
        tutorPolicyService = new TutorPolicyService(masteryPolicy);
    }

    @Test
    void t1_targetNullAuto_Explain() {
        TutorPolicyContext ctx = new TutorPolicyContext(null, null, null, null, 0, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.focusConceptId()).isNull();
        assertThat(dec.mode()).isEqualTo(TutorMode.EXPLAIN);
    }

    @Test
    void t2_targetUnseenAuto_Explain() {
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.UNSEEN, 0.0, 0, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.focusConceptId()).isEqualTo(10L);
        assertThat(dec.mode()).isEqualTo(TutorMode.EXPLAIN);
    }

    @Test
    void t3_targetObservedLowAuto_AskQuestion() {
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.20, 5, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.ASK_QUESTION);
    }

    @Test
    void t4_targetObservedOneShotAuto_AskQuestion() {
        // One-shot is not mastered (1.0 / 1 < min 2)
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 1.0, 1, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.ASK_QUESTION);
    }

    @Test
    void t5_targetObservedMasteredAuto_Explain() {
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.80, 2, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.EXPLAIN);
    }

    @Test
    void t6_explicitAskQuestionOverridesAuto() {
        // Target mastered, normally EXPLAIN, but explicitly ASK_QUESTION
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.80, 2, null, TutorRequestedMode.ASK_QUESTION);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.ASK_QUESTION);
    }

    @Test
    void t7_explicitGiveHint() {
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.UNSEEN, 0.0, 0, null, TutorRequestedMode.GIVE_HINT);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.GIVE_HINT);
    }

    @Test
    void t8_explicitExplain() {
        // Weak target normally ASK_QUESTION, overridden to EXPLAIN
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.20, 5, null, TutorRequestedMode.EXPLAIN);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.mode()).isEqualTo(TutorMode.EXPLAIN);
    }

    @Test
    void t9_singleUnmetPrerequisite() {
        TutorPrerequisiteState p1 = new TutorPrerequisiteState(20L, "P", MasteryState.UNSEEN, 0.0, 0);
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.5, 5, Collections.singletonList(p1), TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.targetConceptId()).isEqualTo(10L);
        assertThat(dec.focusConceptId()).isEqualTo(20L);
    }

    @Test
    void t10_masteredPrerequisiteFocusesTarget() {
        TutorPrerequisiteState p1 = new TutorPrerequisiteState(20L, "P", MasteryState.OBSERVED, 0.9, 3);
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.5, 5, Collections.singletonList(p1), TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        assertThat(dec.focusConceptId()).isEqualTo(10L);
    }

    @Test
    void t11_multipleUnmetPrerequisitesTieBreak() {
        TutorPrerequisiteState p1 = new TutorPrerequisiteState(50L, "P1", MasteryState.UNSEEN, 0.0, 0);
        TutorPrerequisiteState p2 = new TutorPrerequisiteState(30L, "P2", MasteryState.UNSEEN, 0.0, 0);
        TutorPrerequisiteState p3 = new TutorPrerequisiteState(40L, "P3", MasteryState.UNSEEN, 0.0, 0);
        
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.5, 5, Arrays.asList(p1, p2, p3), TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        
        // Smallest ID should be chosen -> 30L
        assertThat(dec.focusConceptId()).isEqualTo(30L);
    }

    @Test
    void t12_targetPreservedWhenFocusDiffers() {
        TutorPrerequisiteState p1 = new TutorPrerequisiteState(20L, "P", MasteryState.UNSEEN, 0.0, 0);
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.5, 5, Collections.singletonList(p1), TutorRequestedMode.AUTO);
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        
        assertThat(dec.targetConceptId()).isEqualTo(10L);
        assertThat(dec.focusConceptId()).isEqualTo(20L);
        // Mode logic driven by target
        assertThat(dec.mode()).isEqualTo(TutorMode.ASK_QUESTION);
    }

    @Test
    void t13_respectsNonDefaultAdaptivePolicy() {
        properties.setMasteredThreshold(0.95);
        // Prereq is 0.90 / 5. With 0.80 it would be mastered, but with 0.95 it's not.
        TutorPrerequisiteState p1 = new TutorPrerequisiteState(20L, "P", MasteryState.OBSERVED, 0.90, 5);
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.99, 5, Collections.singletonList(p1), TutorRequestedMode.AUTO);
        
        TutorPolicyDecision dec = tutorPolicyService.decide(ctx);
        // Because threshold is 0.95, prereq is unmet -> focus is prereq
        assertThat(dec.focusConceptId()).isEqualTo(20L);
        
        properties.setMasteredThreshold(0.80);
        // Now prereq is mastered -> focus is target
        TutorPolicyDecision dec2 = tutorPolicyService.decide(ctx);
        assertThat(dec2.focusConceptId()).isEqualTo(10L);
    }
    
    @Test
    void t14_sameInputSameDecision() {
        TutorPolicyContext ctx = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.5, 5, null, TutorRequestedMode.AUTO);
        TutorPolicyDecision d1 = tutorPolicyService.decide(ctx);
        TutorPolicyDecision d2 = tutorPolicyService.decide(ctx);
        assertThat(d1).isEqualTo(d2);
    }
    
    @Test
    void testMasteredBoundaryRegression() {
        // OBSERVED .80 / evidenceCount 2 -> mastered
        TutorPolicyContext ctx1 = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.80, 2, null, TutorRequestedMode.AUTO);
        assertThat(tutorPolicyService.decide(ctx1).mode()).isEqualTo(TutorMode.EXPLAIN);

        // OBSERVED 1.0 / evidenceCount 1 -> NOT mastered
        TutorPolicyContext ctx2 = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 1.0, 1, null, TutorRequestedMode.AUTO);
        assertThat(tutorPolicyService.decide(ctx2).mode()).isEqualTo(TutorMode.ASK_QUESTION);

        // OBSERVED .79 / high evidence -> NOT mastered
        TutorPolicyContext ctx3 = new TutorPolicyContext(10L, "T", MasteryState.OBSERVED, 0.79, 10, null, TutorRequestedMode.AUTO);
        assertThat(tutorPolicyService.decide(ctx3).mode()).isEqualTo(TutorMode.ASK_QUESTION);
    }
}