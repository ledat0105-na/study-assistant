package com.example.studyassistant.service.tutor;

import com.example.studyassistant.service.adaptive.AdaptiveMasteryPolicy;
import com.example.studyassistant.dto.MasteryState;
import org.springframework.stereotype.Service;

@Service
public class TutorPolicyService {

    private final AdaptiveMasteryPolicy masteryPolicy;

    public TutorPolicyService(AdaptiveMasteryPolicy masteryPolicy) {
        this.masteryPolicy = masteryPolicy;
    }

    public TutorPolicyDecision decide(TutorPolicyContext context) {
        Long focusConceptId = context.targetConceptId();
        String focusConceptName = context.targetConceptName();

        // 10. FOCUS SELECTION
        // 11. MULTIPLE UNMET PREREQUISITES - Tie-break: smallest conceptId
        if (context.prerequisites() != null && !context.prerequisites().isEmpty()) {
            TutorPrerequisiteState chosenPrereq = null;
            for (TutorPrerequisiteState prereq : context.prerequisites()) {
                boolean isMastered = masteryPolicy.isMastered(
                    prereq.masteryState(),
                    prereq.masteryEstimate(),
                    prereq.evidenceCount()
                );
                
                if (!isMastered) {
                    if (chosenPrereq == null || prereq.conceptId() < chosenPrereq.conceptId()) {
                        chosenPrereq = prereq;
                    }
                }
            }
            if (chosenPrereq != null) {
                focusConceptId = chosenPrereq.conceptId();
                focusConceptName = chosenPrereq.conceptName();
            }
        }

        // 14. NULL REQUESTED MODE -> AUTO
        TutorRequestedMode reqMode = context.requestedMode() == null ? TutorRequestedMode.AUTO : context.requestedMode();
        TutorMode finalMode;

        // 13. EXPLICIT REQUEST HAS PRIORITY
        if (reqMode == TutorRequestedMode.ASK_QUESTION) {
            finalMode = TutorMode.ASK_QUESTION;
        } else if (reqMode == TutorRequestedMode.GIVE_HINT) {
            finalMode = TutorMode.GIVE_HINT;
        } else if (reqMode == TutorRequestedMode.EXPLAIN) {
            finalMode = TutorMode.EXPLAIN;
        } else {
            // 15. AUTO POLICY
            if (context.targetConceptId() == null) {
                finalMode = TutorMode.EXPLAIN;
            } else if (context.targetMasteryState() == null || context.targetMasteryState() == MasteryState.UNSEEN) {
                finalMode = TutorMode.EXPLAIN;
            } else { // OBSERVED
                boolean isTargetMastered = masteryPolicy.isMastered(
                    context.targetMasteryState(),
                    context.targetMasteryEstimate(),
                    context.targetEvidenceCount()
                );
                finalMode = isTargetMastered ? TutorMode.EXPLAIN : TutorMode.ASK_QUESTION;
            }
        }

        return new TutorPolicyDecision(
            context.targetConceptId(),
            context.targetConceptName(),
            focusConceptId,
            focusConceptName,
            finalMode
        );
    }
}
