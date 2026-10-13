package com.example.studyassistant.service;

public class KnowledgeGraphGenerationException extends RuntimeException {
    private final GraphFailureCode code;
    public KnowledgeGraphGenerationException() { this(GraphFailureCode.AI_PROVIDER_ERROR); }
    public KnowledgeGraphGenerationException(GraphFailureCode code) { super(code.message); this.code = code; }
    public GraphFailureCode getCode() { return code; }
}
