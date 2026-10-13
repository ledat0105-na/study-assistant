package com.example.studyassistant.domain.flashcard.generation.exception;

public class MalformedLlmResponseException extends RuntimeException {
    public MalformedLlmResponseException(String message) { super(message); }
    public MalformedLlmResponseException(String message, Throwable cause) { super(message, cause); }
}
