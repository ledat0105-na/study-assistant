package com.example.studyassistant.domain.flashcard.generation.exception;

public class StaleGenerationSnapshotException extends RuntimeException {
    public StaleGenerationSnapshotException(String message) { super(message); }
}
