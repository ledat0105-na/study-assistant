package com.example.studyassistant.service.tutor;

import com.example.studyassistant.dto.MasteryState;

public record TutorPrerequisiteState(
    Long conceptId,
    String conceptName,
    MasteryState masteryState,
    Double masteryEstimate,
    int evidenceCount
) {}
