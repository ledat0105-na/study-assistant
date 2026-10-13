package com.example.studyassistant.controller.tutor;

import com.example.studyassistant.dto.tutor.TutorMessageRequest;
import com.example.studyassistant.dto.tutor.TutorMessageResponse;
import com.example.studyassistant.service.tutor.TutorTurnCommand;
import com.example.studyassistant.service.tutor.TutorTurnResult;
import com.example.studyassistant.service.tutor.TutorTurnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/tutor/conversations")
public class TutorController {

    private final TutorTurnService tutorTurnService;

    public TutorController(TutorTurnService tutorTurnService) {
        this.tutorTurnService = tutorTurnService;
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Long) session.getAttribute("userId");
        }
        return null;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<?> sendTutorMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody TutorMessageRequest requestBody,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        TutorTurnCommand command = new TutorTurnCommand(
                userId,
                conversationId,
                requestBody.getMessage(),
                requestBody.getConceptId(),
                requestBody.getRequestedMode()
        );

        try {
            TutorTurnResult result = tutorTurnService.turn(command);
            
            TutorMessageResponse response = new TutorMessageResponse(
                result.conversationId(),
                result.targetConceptId(),
                result.focusConceptId(),
                result.mode(),
                result.message(),
                result.hasSufficientEvidence(),
                result.citations() != null ? result.citations() : Collections.emptyList()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found or unauthorized")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
            }
            if (e.getMessage() != null && e.getMessage().contains("non-notebook")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Notebookless conversation not supported");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");
        }
    }
}
