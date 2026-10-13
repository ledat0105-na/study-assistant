package com.example.studyassistant.domain.presentation.controller;

import com.example.studyassistant.domain.presentation.export.PptxExportArtifact;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportService;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/teacher/presentations")
@RequiredArgsConstructor
public class PresentationPptxExportController {

    private static final MediaType PPTX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final TeacherAuthorizationService authorizationService;
    private final PresentationPptxExportService exportService;

    @GetMapping("/{deckId}/export/pptx")
    public ResponseEntity<InputStreamResource> exportPptx(
            @PathVariable Long deckId,
            HttpServletRequest request) {
        User owner = authorizationService.verifyTeacherCapability(request);
        PptxExportArtifact artifact = exportService.export(owner, deckId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(PPTX_MEDIA_TYPE);
        headers.setContentLength(artifact.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(artifact.filename(), StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        try {
            InputStream input = artifact.openStream();
            InputStream managedInput = new FilterInputStream(input) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        artifact.close();
                    }
                }
            };
            return new ResponseEntity<>(new InputStreamResource(managedInput), headers, HttpStatus.OK);
        } catch (IOException ex) {
            try {
                artifact.close();
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Could not open completed presentation export", ex);
        }
    }
}