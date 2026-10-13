package com.example.studyassistant.retrieval.model;

import java.util.List;
import java.util.Map;

public record GroundedContext(
    boolean hasSufficientEvidence,
    String sourceData,
    Map<String, CitationDto> sourceMap,
    List<RetrievedChunk> chunks
) {}
