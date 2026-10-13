package com.example.studyassistant.service;

import org.springframework.stereotype.Component;

@Component
public class MasteryHeuristicCalculator {

    private static final double ALPHA = 0.30;

    public double calculateNewMastery(Double oldMastery, double normalizedScore) {
        if (!Double.isFinite(normalizedScore) || normalizedScore < 0.0 || normalizedScore > 1.0) {
            throw new IllegalArgumentException("Invalid normalized score");
        }

        double newScore;
        if (oldMastery == null) {
            newScore = normalizedScore;
        } else {
            if (!Double.isFinite(oldMastery) || oldMastery < 0.0 || oldMastery > 1.0) {
                throw new IllegalArgumentException("Corrupted existing mastery state");
            }
            newScore = oldMastery * (1.0 - ALPHA) + normalizedScore * ALPHA;
        }

        // Defensive bound against floating point noise (e.g. 1.0000000000000002)
        if (newScore > 1.0) return 1.0;
        if (newScore < 0.0) return 0.0;
        return newScore;
    }
}
