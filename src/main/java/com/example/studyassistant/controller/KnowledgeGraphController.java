package com.example.studyassistant.controller;

import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.service.KnowledgeGraphQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notebooks/{notebookId}")
@CrossOrigin(origins = "*")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphQueryService knowledgeGraphQueryService;

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @GetMapping("/knowledge-graph")
    public ResponseEntity<?> getKnowledgeGraph(@PathVariable Long notebookId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chua dang nh?p"));
        }
        try {
            return ResponseEntity.ok(knowledgeGraphQueryService.getGroundedKnowledgeGraph(notebookId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (GraphTooLargeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/concepts/{conceptId}")
    public ResponseEntity<?> getConceptDetail(@PathVariable Long notebookId, @PathVariable Long conceptId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chua dang nh?p"));
        }
        try {
            return ResponseEntity.ok(knowledgeGraphQueryService.getConceptDetail(notebookId, conceptId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
