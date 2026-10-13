package com.example.studyassistant.domain.summary;

public class DocumentSummaryUnavailableException extends RuntimeException {
    public DocumentSummaryUnavailableException(String message) {
        super(message);
    }
}
