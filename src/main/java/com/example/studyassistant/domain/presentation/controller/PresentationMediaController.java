package com.example.studyassistant.domain.presentation.controller;

import com.example.studyassistant.domain.presentation.dto.PresentationMediaAssetDto;
import com.example.studyassistant.domain.presentation.media.PresentationMediaContent;
import com.example.studyassistant.domain.presentation.media.PresentationMediaService;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/teacher/presentations/{deckId}/media")
@RequiredArgsConstructor
public class PresentationMediaController {
    private final TeacherAuthorizationService authorizationService;
    private final PresentationMediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PresentationMediaAssetDto> upload(
            @PathVariable Long deckId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        User user = authorizationService.verifyTeacherCapability(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(user, deckId, file));
    }

    @GetMapping
    public ResponseEntity<List<PresentationMediaAssetDto>> list(
            @PathVariable Long deckId,
            HttpServletRequest request) {
        User user = authorizationService.verifyTeacherCapability(request);
        return ResponseEntity.ok(mediaService.list(user, deckId));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<PresentationMediaAssetDto> get(
            @PathVariable Long deckId,
            @PathVariable Long mediaId,
            HttpServletRequest request) {
        User user = authorizationService.verifyTeacherCapability(request);
        return ResponseEntity.ok(mediaService.get(user, deckId, mediaId));
    }

    @GetMapping("/{mediaId}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long deckId,
            @PathVariable Long mediaId,
            HttpServletRequest request) {
        User user = authorizationService.verifyTeacherCapability(request);
        PresentationMediaContent content = mediaService.openContent(user, deckId, mediaId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.mimeType()));
        headers.setContentLength(content.contentLength());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        return new ResponseEntity<>(new InputStreamResource(content.inputStream()), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long deckId,
            @PathVariable Long mediaId,
            HttpServletRequest request) {
        User user = authorizationService.verifyTeacherCapability(request);
        mediaService.delete(user, deckId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
