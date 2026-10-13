package com.example.studyassistant.domain.presentation.generation;

/**
 * Custom exceptions for outline generation domain.
 * All extend RuntimeException. None expose raw provider errors.
 */
public class OutlineGenerationException {

    /** Deck is archived or not found for this owner. */
    public static class DeckNotAccessibleException extends RuntimeException {
        public DeckNotAccessibleException(String message) { super(message); }
    }

    /** Supplied documentId does not belong to owner's notebook. */
    public static class ForeignDocumentException extends RuntimeException {
        public ForeignDocumentException(String message) { super(message); }
    }

    /** Document not in READY state or has no active index version. */
    public static class DocumentNotIndexedException extends RuntimeException {
        public DocumentNotIndexedException(String message) { super(message); }
    }

    /** Retrieval returned insufficient evidence to proceed with generation. */
    public static class InsufficientEvidenceException extends RuntimeException {
        public InsufficientEvidenceException(String message) { super(message); }
    }

    /** LLM returned malformed JSON that could not be parsed. */
    public static class MalformedLlmResponseException extends RuntimeException {
        public MalformedLlmResponseException(String message) { super(message); }
        public MalformedLlmResponseException(String message, Throwable cause) { super(message, cause); }
    }

    /** LLM cited a source ref not in the server-supplied allowlist. Fail closed. */
    public static class UnknownSourceRefException extends RuntimeException {
        public UnknownSourceRefException(String message) { super(message); }
    }

    /** Outline JSON failed schema validation (bounds, blank fields, etc.). */
    public static class OutlineSchemaViolationException extends RuntimeException {
        public OutlineSchemaViolationException(String message) { super(message); }
    }

    /** Quota exhausted for outline generation. */
    public static class OutlineQuotaExceededException extends RuntimeException {
        public OutlineQuotaExceededException(String message) { super(message); }
    }

    /** LLM provider call failed (network, timeout, 4xx/5xx). */
    public static class LlmProviderException extends RuntimeException {
        public LlmProviderException(String message, Throwable cause) { super(message, cause); }
    }
}
