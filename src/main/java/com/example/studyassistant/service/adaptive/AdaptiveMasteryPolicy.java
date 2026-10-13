package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.config.AdaptivePolicyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveMasteryPolicy {

    private final AdaptivePolicyProperties properties;

    @Autowired
    public AdaptiveMasteryPolicy(AdaptivePolicyProperties properties) {
        this.properties = properties;
    }

    public boolean isMastered(MasteryState masteryState, Double masteryEstimate, int evidenceCount) {
        return masteryState == MasteryState.OBSERVED
                && masteryEstimate != null
                && masteryEstimate >= properties.getMasteredThreshold()
                && evidenceCount >= properties.getMasteredMinEvidence();
    }
}
