package com.example.studyassistant.controller;

import com.example.studyassistant.exception.GraphTooLargeException;
import com.example.studyassistant.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DocumentMindMapController {
    private final KnowledgeGraphQueryService knowledgeGraphQueryService;
    private final DocumentKnowledgeGraphGenerationService generationService;

    @PostMapping("/{docId}/mind-map/generate")
    public ResponseEntity<?> generate(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) return error(GraphFailureCode.UNAUTHENTICATED);
        return respond(docId, () -> generationService.generate(userId, docId));
    }
    @GetMapping("/{docId}/mind-map")
    public ResponseEntity<?> getDocumentMindMap(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) return error(GraphFailureCode.UNAUTHENTICATED);
        return respond(docId, () -> knowledgeGraphQueryService.getGroundedKnowledgeGraphForDocument(docId, userId));
    }
    private ResponseEntity<?> respond(Long docId, Supplier<?> action) {
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(action.get());
        } catch (KnowledgeGraphGenerationException e) {
            log.error("Mind-map generation failed for doc {}: {}", docId, e.getCode(), e);
            return error(e.getCode());
        } catch (KnowledgeGraphStateException e) {
            return error(e.getCode());
        } catch (GraphTooLargeException e) {
            return error(GraphFailureCode.GRAPH_TOO_LARGE);
        } catch (IllegalArgumentException e) {
            return error(GraphFailureCode.NOT_FOUND);
        } catch (IllegalStateException e) {
            return error(GraphFailureCode.STALE_INDEX);
        } catch (RuntimeException e) {
            log.error("Mind-map local failure for doc {}", docId, e);
            return error(GraphFailureCode.GRAPH_PERSISTENCE_ERROR);
        }
    }
    private ResponseEntity<?> error(GraphFailureCode code) {
        return ResponseEntity.status(code.httpStatus).header("Cache-Control", "no-store")
                .body(Map.of("code", code.name(), "error", code.message));
    }
    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") instanceof Long id ? id : null;
    }
}
