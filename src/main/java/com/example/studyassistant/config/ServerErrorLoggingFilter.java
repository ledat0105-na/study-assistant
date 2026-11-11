package com.example.studyassistant.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records 5xx responses even when a controller catches the exception itself. */
@Component
@lombok.RequiredArgsConstructor
public class ServerErrorLoggingFilter extends OncePerRequestFilter {
    private final com.example.studyassistant.service.IncidentMonitor incidents;
    private static final Logger log = LoggerFactory.getLogger(ServerErrorLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failed = true;
            log.error("Request processing failed", ex);
            throw ex;
        } finally {
            if (failed || response.getStatus() >= 500) incidents.serverError();
            if (response.getStatus() >= 500) {
                // Deliberately omit headers, request bodies and query strings (credentials).
                log.error("HTTP {} {}", response.getStatus(), request.getMethod());
            }
        }
    }
}
