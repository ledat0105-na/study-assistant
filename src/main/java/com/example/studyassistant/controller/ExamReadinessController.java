package com.example.studyassistant.controller;

import com.example.studyassistant.domain.examreadiness.ExamReadinessResponse;
import com.example.studyassistant.domain.examreadiness.ExamReadinessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/notebooks/{notebookId}")
public class ExamReadinessController {
    private final ExamReadinessService readinessService;

    public ExamReadinessController(ExamReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/exam-readiness")
    public ResponseEntity<?> getReadiness(@PathVariable Long notebookId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = session != null ? (Long) session.getAttribute("userId") : null;
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            ExamReadinessResponse response = readinessService.getNotebookReadiness(userId, notebookId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}