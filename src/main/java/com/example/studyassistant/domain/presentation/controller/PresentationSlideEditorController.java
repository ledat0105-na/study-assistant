package com.example.studyassistant.domain.presentation.controller;

import com.example.studyassistant.domain.presentation.PresentationSlideEditorService;
import com.example.studyassistant.domain.presentation.dto.SlideElementCreateRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementDeleteRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementReadDto;
import com.example.studyassistant.domain.presentation.dto.SlideElementUpdateRequest;
import com.example.studyassistant.domain.presentation.dto.SlidePatchRequest;
import com.example.studyassistant.domain.presentation.dto.SlideReadDto;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/presentations")
@RequiredArgsConstructor
public class PresentationSlideEditorController {

    private final TeacherAuthorizationService authService;
    private final PresentationSlideEditorService slideEditorService;

    @GetMapping("/{deckId}/slides")
    public ResponseEntity<List<SlideReadDto>> listSlides(@PathVariable Long deckId, HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        return ResponseEntity.ok(slideEditorService.listSlides(user, deckId));
    }

    @GetMapping("/{deckId}/slides/{slideId}")
    public ResponseEntity<SlideReadDto> getSlide(@PathVariable Long deckId, @PathVariable Long slideId, HttpServletRequest request) {
        User user = authService.verifyTeacherCapability(request);
        return ResponseEntity.ok(slideEditorService.getSlide(user, deckId, slideId));
    }

    @PatchMapping("/{deckId}/slides/{slideId}")
    public ResponseEntity<SlideReadDto> patchSlide(
            @PathVariable Long deckId,
            @PathVariable Long slideId,
            @RequestBody SlidePatchRequest request,
            HttpServletRequest httpRequest) {
        User user = authService.verifyTeacherCapability(httpRequest);
        return ResponseEntity.ok(slideEditorService.patchSlide(user, deckId, slideId, request));
    }

    @PostMapping("/{deckId}/slides/{slideId}/elements")
    public ResponseEntity<SlideElementReadDto> addElement(
            @PathVariable Long deckId,
            @PathVariable Long slideId,
            @RequestBody SlideElementCreateRequest request,
            HttpServletRequest httpRequest) {
        User user = authService.verifyTeacherCapability(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(slideEditorService.addElement(user, deckId, slideId, request));
    }

    @PatchMapping("/{deckId}/slides/{slideId}/elements/{elementId}")
    public ResponseEntity<SlideElementReadDto> updateElement(
            @PathVariable Long deckId,
            @PathVariable Long slideId,
            @PathVariable Long elementId,
            @RequestBody SlideElementUpdateRequest request,
            HttpServletRequest httpRequest) {
        User user = authService.verifyTeacherCapability(httpRequest);
        return ResponseEntity.ok(slideEditorService.updateElement(user, deckId, slideId, elementId, request));
    }

    @DeleteMapping("/{deckId}/slides/{slideId}/elements/{elementId}")
    public ResponseEntity<Void> deleteElement(
            @PathVariable Long deckId,
            @PathVariable Long slideId,
            @PathVariable Long elementId,
            @RequestBody SlideElementDeleteRequest request,
            HttpServletRequest httpRequest) {
        User user = authService.verifyTeacherCapability(httpRequest);
        slideEditorService.deleteElement(user, deckId, slideId, elementId, request);
        return ResponseEntity.noContent().build();
    }
}
