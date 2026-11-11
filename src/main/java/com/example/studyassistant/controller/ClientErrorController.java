package com.example.studyassistant.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Limited diagnostic metadata only; client messages may contain private information. */
@RestController
@lombok.RequiredArgsConstructor
public class ClientErrorController {
    private final com.example.studyassistant.service.IncidentMonitor incidents;
    private static final Logger log = LoggerFactory.getLogger(ClientErrorController.class);
    private static final String KEY = ClientErrorController.class.getName();
    public record ClientError(String kind, String source, int line, int column) {}

    @PostMapping("/api/client-errors")
    public ResponseEntity<Void> report(@RequestBody ClientError error, HttpServletRequest request) {
        if (error == null || !("SCRIPT_ERROR".equals(error.kind()) || "UNHANDLED_REJECTION".equals(error.kind()))
                || error.source() == null || !error.source().matches("[a-zA-Z0-9/_.-]{0,200}")
                || error.line() < 0 || error.column() < 0) {
            return ResponseEntity.badRequest().build();
        }
        HttpSession session = request.getSession();
        synchronized (session) {
            long now = System.currentTimeMillis();
            long[] budget = (long[]) session.getAttribute(KEY);
            if (budget == null || now - budget[0] >= 60000) {
                budget = new long[]{now, 0}; session.setAttribute(KEY, budget);
            }
            if (budget[1] >= 5) return ResponseEntity.status(429).build();
            budget[1]++;
        }
        if (session.getAttribute("userId") instanceof Long) incidents.browserError();
        log.error("Browser {} source={} line={} column={}", error.kind(), error.source(), error.line(), error.column());
        return ResponseEntity.noContent().build();
    }
}
