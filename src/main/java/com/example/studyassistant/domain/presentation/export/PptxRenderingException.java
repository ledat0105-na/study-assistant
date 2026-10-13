package com.example.studyassistant.domain.presentation.export;

public class PptxRenderingException extends RuntimeException {
    public PptxRenderingException(Throwable cause) {
        super("PPTX rendering failed", cause);
    }
}
