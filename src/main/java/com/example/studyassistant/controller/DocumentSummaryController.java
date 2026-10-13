package com.example.studyassistant.controller;

import com.example.studyassistant.domain.summary.DocumentSummaryNotFoundException;
import com.example.studyassistant.domain.summary.DocumentSummaryProviderException;
import com.example.studyassistant.domain.summary.DocumentSummaryService;
import com.example.studyassistant.domain.summary.DocumentSummaryUnavailableException;
import com.example.studyassistant.domain.summary.StaleDocumentSummaryException;
import com.example.studyassistant.domain.summary.SummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/documents/{docId}/summary")
@RequiredArgsConstructor
@Slf4j
public class DocumentSummaryController {

    public record SummaryCacheMiss(Long docId, boolean cached, String summary) {}

    private final DocumentSummaryService summaryService;

    @GetMapping
    public ResponseEntity<?> getSummary(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        try {
            Optional<SummaryResponse> cached = summaryService.getCached(userId, docId);
            return cached.<ResponseEntity<?>>map(data -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data))
                    .orElseGet(() -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                            .body(new SummaryCacheMiss(docId, false, null)));
        } catch (DocumentSummaryNotFoundException e) {
            return notFound();
        } catch (DocumentSummaryUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateSummary(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa đăng nhập"));
        }
        try {
            return ResponseEntity.ok(summaryService.generate(userId, docId));
        } catch (DocumentSummaryNotFoundException e) {
            return notFound();
        } catch (DocumentSummaryUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (StaleDocumentSummaryException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Document changed during summary generation; retry"));
        } catch (DocumentSummaryProviderException e) {
            log.error("Summary generation failed for document {}", docId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("code", "SUMMARY_PROVIDER_FAILURE", "error", "AI chưa tạo được bản tóm tắt. Vui lòng thử lại sau."));
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return null;
        }
        Object value = session.getAttribute("userId");
        return value instanceof Long id ? id : null;
    }

    private ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "DOCUMENT_NOT_FOUND", "error", "Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
    }
}
