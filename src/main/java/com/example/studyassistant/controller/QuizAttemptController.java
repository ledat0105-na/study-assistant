package com.example.studyassistant.controller;

import com.example.studyassistant.domain.quiz.submission.QuizAttemptResultResponse;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptSubmitCommand;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptSubmitRequest;
import com.example.studyassistant.domain.quiz.submission.QuizAttemptSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/quiz-attempts")
@CrossOrigin(origins = "*")
public class QuizAttemptController {

    private final QuizAttemptSubmissionService submissionService;

    public QuizAttemptController(QuizAttemptSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<?> submitQuizAttempt(
            @PathVariable Long attemptId,
            @RequestBody QuizAttemptSubmitRequest command,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = session != null ? (Long) session.getAttribute("userId") : null;
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        QuizAttemptSubmitCommand safeCommand = new QuizAttemptSubmitCommand(
                userId, attemptId, command.submissionToken(), command.answers());
        try {
            QuizAttemptResultResponse response = submissionService.submit(safeCommand);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
                if (e.getMessage().contains("Conflict") || e.getMessage().contains("CONFLICT")
                    || e.getMessage().contains("EXPIRED")
                    || e.getMessage().contains("Corrupt") || e.getMessage().contains("status")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }
            throw e;
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
