package com.example.studyassistant.domain.presentation.controller;

import com.example.studyassistant.domain.presentation.export.PresentationExportStateInvalidException;
import com.example.studyassistant.domain.presentation.export.PptxRenderingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PresentationPptxExportController.class)
public class PresentationPptxExportExceptionHandler {

    @ExceptionHandler(PresentationExportStateInvalidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidExportState() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "EXPORT_STATE_INVALID", "error", "Presentation export state is invalid"));
    }

    @ExceptionHandler(PptxRenderingException.class)
    public ResponseEntity<Map<String, String>> handleRenderingFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "EXPORT_FAILED", "error", "Presentation export failed"));
    }
}