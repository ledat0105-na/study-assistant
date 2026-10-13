package com.example.studyassistant.service.tutor;

import java.util.List;
import com.example.studyassistant.dto.MasteryState;

public record TutorPolicyContext(
    Long targetConceptId,
    String targetConceptName,
    MasteryState targetMasteryState,
    Double targetMasteryEstimate,
    int targetEvidenceCount,
    List<TutorPrerequisiteState> prerequisites,
    TutorRequestedMode requestedMode
) {}
