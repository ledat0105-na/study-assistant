package com.example.studyassistant.service;

public class KnowledgeGraphStateException extends IllegalStateException {
    private final GraphFailureCode code;
    public KnowledgeGraphStateException(GraphFailureCode code) { super(code.message); this.code = code; }
    public GraphFailureCode getCode() { return code; }
}
