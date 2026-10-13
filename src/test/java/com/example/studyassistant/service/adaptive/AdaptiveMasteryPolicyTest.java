package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.dto.MasteryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveMasteryPolicyTest {

    private AdaptivePolicyProperties properties;
    private AdaptiveMasteryPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new AdaptivePolicyProperties();
        properties.setMasteredThreshold(0.80);
        properties.setMasteredMinEvidence(2);
        policy = new AdaptiveMasteryPolicy(properties);
    }

    @Test
    void p1_unseenNullZero() {
        assertFalse(policy.isMastered(MasteryState.UNSEEN, null, 0));
    }

    @Test
    void p2_observed10_1() {
        assertFalse(policy.isMastered(MasteryState.OBSERVED, 1.0, 1));
    }

    @Test
    void p3_observed079_10() {
        assertFalse(policy.isMastered(MasteryState.OBSERVED, 0.79, 10));
    }

    @Test
    void p4_observed080_2() {
        assertTrue(policy.isMastered(MasteryState.OBSERVED, 0.80, 2));
    }

    @Test
    void p5_observed10_2() {
        assertTrue(policy.isMastered(MasteryState.OBSERVED, 1.0, 2));
    }

    @Test
    void p6_nonDefaultConfig() {
        properties.setMasteredThreshold(0.90);
        properties.setMasteredMinEvidence(3);
        assertFalse(policy.isMastered(MasteryState.OBSERVED, 0.80, 10));
        assertTrue(policy.isMastered(MasteryState.OBSERVED, 0.90, 3));
    }
    
    @Test
    void p7_certifiedMalformedBehaviorPreserved() {
        // Handled naturally by existing NPE when null estimate is checked for threshold
        // Or boolean false if we rely on null check in isMastered
        assertFalse(policy.isMastered(MasteryState.OBSERVED, null, 2));
    }
}
