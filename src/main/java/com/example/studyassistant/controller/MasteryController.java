package com.example.studyassistant.controller;

import com.example.studyassistant.dto.ConceptMasteryDetailDto;
import com.example.studyassistant.dto.ConceptMasteryItemDto;
import com.example.studyassistant.dto.MasteryPageDto;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.service.StudentMasteryReadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notebooks/{notebookId}")
@RequiredArgsConstructor
public class MasteryController {

    private final StudentMasteryReadService masteryReadService;
    private final NotebookRepository notebookRepository;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @GetMapping("/mastery")
    public ResponseEntity<?> getNotebookMastery(
            @PathVariable Long notebookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(notebookId, userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MasteryPageDto<ConceptMasteryItemDto> response = masteryReadService.getNotebookMastery(userId, notebookId, PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/concepts/{conceptId}/mastery")
    public ResponseEntity<?> getConceptMastery(
            @PathVariable Long notebookId,
            @PathVariable Long conceptId,
            HttpServletRequest request) {
        
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ConceptMasteryDetailDto response = masteryReadService.getConceptMastery(userId, notebookId, conceptId);
            return ResponseEntity.ok(response);
        } catch (MasteryConceptNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
