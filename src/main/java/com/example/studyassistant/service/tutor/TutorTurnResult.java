package com.example.studyassistant.service.tutor;

import com.example.studyassistant.retrieval.model.CitationDto;
import java.util.List;

public record TutorTurnResult(
    Long conversationId,
    Long targetConceptId,
    Long focusConceptId,
    TutorMode mode,
    String message,
    boolean hasSufficientEvidence,
    List<CitationDto> citations
) {}
