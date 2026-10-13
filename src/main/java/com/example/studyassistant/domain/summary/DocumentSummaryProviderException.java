package com.example.studyassistant.domain.summary;

public class DocumentSummaryProviderException extends RuntimeException {
    public DocumentSummaryProviderException(String message) {
        super(message);
    }

    public DocumentSummaryProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
