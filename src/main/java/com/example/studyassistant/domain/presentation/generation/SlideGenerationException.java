package com.example.studyassistant.domain.presentation.generation;

/**
 * Domain exceptions for T1.5 slide generation.
 */
public class SlideGenerationException {

    public static class SlideSchemaViolationException extends RuntimeException {
        public SlideSchemaViolationException(String message) { super(message); }
    }

    public static class MalformedLlmSlideResponseException extends RuntimeException {
        public MalformedLlmSlideResponseException(String message) { super(message); }
        public MalformedLlmSlideResponseException(String message, Throwable cause) { super(message, cause); }
    }

    public static class UnknownSourceRefException extends RuntimeException {
        public UnknownSourceRefException(String message) { super(message); }
    }

    public static class StaleOutlineException extends RuntimeException {
        public StaleOutlineException(String message) { super(message); }
    }

    public static class StaleSourceException extends RuntimeException {
        public StaleSourceException(String message) { super(message); }
    }

    public static class InsufficientEvidenceException extends RuntimeException {
        public InsufficientEvidenceException(String message) { super(message); }
    }

    public static class SlideJobFailedFastException extends RuntimeException {
        private final String errorCode;
        public SlideJobFailedFastException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        public String getErrorCode() { return errorCode; }
    }
}
