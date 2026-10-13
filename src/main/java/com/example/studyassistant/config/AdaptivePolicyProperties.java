package com.example.studyassistant.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "studyassistant.adaptive.policy.v1")
@Validated
public class AdaptivePolicyProperties {

    @jakarta.validation.constraints.DecimalMin("0.0")
    @jakarta.validation.constraints.DecimalMax("1.0")
    private double masteredThreshold = 0.80;

    @Min(1)
    private int masteredMinEvidence = 2;

    @Min(1)
    private int defaultLimit = 5;

    @Min(1)
    private int maxLimit = 20;

    public double getMasteredThreshold() {
        return masteredThreshold;
    }

    public void setMasteredThreshold(double masteredThreshold) {
        this.masteredThreshold = masteredThreshold;
    }

    public int getMasteredMinEvidence() {
        return masteredMinEvidence;
    }

    public void setMasteredMinEvidence(int masteredMinEvidence) {
        this.masteredMinEvidence = masteredMinEvidence;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    @jakarta.validation.constraints.AssertTrue(message = "maxLimit must be greater than or equal to defaultLimit")
    public boolean isLimitRangeValid() {
        return maxLimit >= defaultLimit;
    }
}
