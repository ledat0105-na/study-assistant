package com.example.studyassistant.domain.examreadiness;

import java.util.List;

public record ExamReadinessResponse(
        Long notebookId,
        String calculationVersion,
        boolean insufficientEvidence,
        Integer readinessScore,
        double examPerformance,
        double masteryComponent,
        double coverageComponent,
        int eligibleExamAttemptCount,
        int eligibleConceptCount,
        int coveredConceptCount,
        int weakConceptCount,
        List<WeakConceptDto> weakConcepts) {}