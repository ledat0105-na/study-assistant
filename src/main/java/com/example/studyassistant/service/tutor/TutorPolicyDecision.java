package com.example.studyassistant.service.tutor;

public record TutorPolicyDecision(
    Long targetConceptId,
    String targetConceptName,
    Long focusConceptId,
    String focusConceptName,
    TutorMode mode
) {}
