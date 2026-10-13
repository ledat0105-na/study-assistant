package com.example.studyassistant.domain.examreadiness;

public record WeakConceptDto(
        Long conceptId,
        String conceptName,
        double accuracy,
        long evidenceCount,
        double masteryLevel) {}