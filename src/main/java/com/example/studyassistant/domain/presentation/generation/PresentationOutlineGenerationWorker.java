package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.service.RetrievalService;
import com.example.studyassistant.util.PromptSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T1.4 — Async outline generation worker.
 *
 * MUST be a SEPARATE Spring bean from PresentationOutlineGenerationService
 * so that @Async proxying applies (no same-class self-invocation).
 *
 * Stages (ALL outside open DB transaction except explicit TXs):
 *
 * [short TX]  QUEUED → RUNNING
 * [NO TX]     RetrievalService.search  (not held open)
 * [NO TX]     LlmProvider.generate     (network call, no DB TX)
 * [NO TX]     parse + validate JSON
 * [NO TX]     compute deterministic contentHash
 * [TX2]       PESSIMISTIC_WRITE lock deck → allocate revisionNumber → persist revision → SUCCEEDED
 * [fail TX]   FAILED with safe errorCode/errorSummary
 *
 * Source ref policy (fail closed):
 *   - Server assigns opaque aliases S1..Sn
 *   - LLM may only cite these aliases
 *   - Unknown aliases are dropped; factual slides without valid evidence are omitted
 *   - Duplicate aliases normalized to distinct list
 *
 * No raw chunk DB IDs exposed to LLM.
 * No doc.getExtractedText() usage.
 * No legacy substring context path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationOutlineGenerationWorker {

    private static final int MAX_SECTION_TITLE_LENGTH = 200;
    private static final int MAX_SLIDE_TITLE_LENGTH = 200;
    private static final int MAX_SLIDE_PURPOSE_LENGTH = 500;
    private static final int MAX_KEY_POINT_LENGTH = 500;
    private static final int MAX_KEY_POINTS_PER_SLIDE = 10;
    private static final int MAX_SLIDES_TOTAL = 30;
    private static final double OUTLINE_TEMPERATURE = 0.2;

    private final PresentationGenerationJobRepository jobRepository;
    private final RetrievalService retrievalService;
    private final LlmProvider llmProvider;
    // legacyJackson2ObjectMapper for Jackson 2 compatibility (Boot 4 / Jackson 3 env)
    @Qualifier("legacyJackson2ObjectMapper")
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final PresentationOutlinePersistenceService persistenceService;
    private final PresentationJobFinalizerService finalizerService;
    private final PresentationEvidenceSelector evidenceSelector;

    /**
     * Entry point invoked by @Async proxy.
     * Must not be called directly from the same bean.
     */
    @Async("presentationTaskExecutor")
    public void generateOutlineAsync(
            Long jobId,
            Long deckId,
            Long ownerId,
            Long notebookId,
            String topic,
            String teacherInstruction,
            int targetSlideCount,
            String audience,
            String language,
            List<Long> requestedDocumentIds
    ) {
        log.info("Outline generation async worker started: job={} deck={}", jobId, deckId);

        // ── short TX: QUEUED → RUNNING ──────────────────────────────────────────
        boolean transitioned = transitionToRunning(jobId);
        if (!transitioned) {
            log.warn("Job {} not in QUEUED state, skipping", jobId);
            return;
        }

        try {
            // ── NO TX: retrieval ────────────────────────────────────────────────
            assertNotInTransaction("Retrieval");

            List<RetrievedChunk> chunks = evidenceSelector.select(ownerId, notebookId, requestedDocumentIds);
            if (chunks == null || chunks.isEmpty()) {
                failJob(jobId, "INSUFFICIENT_EVIDENCE", "No content chunks retrieved");
                return;
            }

            // ── Build opaque source map: S1..Sn → RetrievedChunk ───────────────
            // Raw chunk DB IDs are NEVER exposed to the LLM
            Map<String, RetrievedChunk> sourceMap = new LinkedHashMap<>();
            for (int i = 0; i < chunks.size(); i++) {
                sourceMap.put("S" + (i + 1), chunks.get(i));
            }

            // ── NO TX: LLM call ──────────────────────────────────────────────────
            assertNotInTransaction("LLM");
            updateProgress(jobId, 1);

            String systemPrompt = buildSystemPrompt(sourceMap, targetSlideCount, audience, language);
            String userPrompt = buildUserPrompt(topic, teacherInstruction, targetSlideCount, audience, language);

            String rawResponse;
            try {
                rawResponse = llmProvider.generate(systemPrompt, userPrompt, OUTLINE_TEMPERATURE);
            } catch (Exception e) {
                log.error("LLM provider failure for job={}", jobId, e);
                failJob(jobId, "LLM_PROVIDER_ERROR", "Outline generation provider call failed");
                return;
            }
            updateProgress(jobId, 2);

            // ── NO TX: parse + validate ──────────────────────────────────────────
            OutlineJsonSchema outline;
            try {
                outline = parseAndValidateOutline(rawResponse, sourceMap, targetSlideCount, audience, language);
            } catch (OutlineGenerationException.MalformedLlmResponseException e) {
                log.warn("Malformed LLM JSON for job={}: {}", jobId, e.getMessage());
                failJob(jobId, "MALFORMED_LLM_RESPONSE", "Provider returned non-parseable outline JSON");
                return;
            } catch (OutlineGenerationException.UnknownSourceRefException e) {
                log.warn("Unknown source ref for job={}: {}", jobId, e.getMessage());
                failJob(jobId, "UNKNOWN_SOURCE_REF", "Provider cited a source reference not in server allowlist");
                return;
            } catch (OutlineGenerationException.OutlineSchemaViolationException e) {
                log.warn("Schema violation for job={}: {}", jobId, e.getMessage());
                failJob(jobId, "SCHEMA_VIOLATION", "Provider outline failed validation: " + e.getMessage());
                return;
            }

            // ── NO TX: compute content hash ──────────────────────────────────────
            String contentHash = OutlineContentHasher.contentHash(outline);

            // ── NO TX: serialize validated outline to JSON ───────────────────────
            String validatedOutlineJson;
            try {
                validatedOutlineJson = objectMapper.writeValueAsString(outline);
            } catch (Exception e) {
                log.error("Serialization failure for job={}", jobId, e);
                failJob(jobId, "SERIALIZATION_ERROR", "Could not serialize validated outline");
                return;
            }

            // ── NO TX: build scope snapshot ──────────────────────────────────────
            String scopeSnapshot = buildScopeSnapshot(sourceMap, requestedDocumentIds, notebookId);

            // ── TX2: lock deck, allocate revision number, persist ────────────────
            assertNotInTransaction("TX2-prepare");
            try {
                persistenceService.persistRevisionAndCompleteJob(
                        jobId, deckId, ownerId, validatedOutlineJson,
                        contentHash, scopeSnapshot
                );
            } catch (Exception e) {
                log.error("Persistence failure for job={}", jobId, e);
                failJob(jobId, "PERSISTENCE_ERROR", "Failed to persist outline revision");
            }

        } catch (Exception e) {
            log.error("Unexpected failure for job={}", jobId, e);
            failJob(jobId, "UNEXPECTED_ERROR", "Unexpected error during outline generation");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt construction
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSystemPrompt(
            Map<String, RetrievedChunk> sourceMap,
            int targetSlideCount,
            String audience,
            String language) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict educational presentation outline generator.\n");
        sb.append("CRITICAL RULES — violation will cause your response to be rejected:\n");
        sb.append("1. Return EXACTLY ONE JSON object. No markdown. No code fences. No prose.\n");
        sb.append("2. The JSON must match this exact schema:\n");
        sb.append("""
{
  "schemaVersion": 1,
  "title": "<string>",
  "learningObjective": "<string>",
  "audience": "<string>",
  "language": "<string>",
  "sections": [
    {
      "title": "<string>",
      "purpose": "<string>",
      "slides": [
        {
          "title": "<string>",
          "purpose": "<string>",
          "slideType": "CONTENT|TITLE|SECTION|SUMMARY",
          "keyPoints": ["<string>"],
          "sourceRefs": ["S1", "S2"]
        }
      ]
    }
  ]
}
""");
        sb.append("3. Target slide count: approximately ").append(targetSlideCount).append(" slides total.\n");
        sb.append("4. SOURCE REF RULES:\n");
        sb.append("   - CONTENT and SUMMARY slides MUST have at least one sourceRef from the list below.\n");
        sb.append("   - TITLE and SECTION divider slides MAY have empty sourceRefs.\n");
        sb.append("   - You may ONLY use the source IDs listed below (S1, S2, ...).\n");
        sb.append("   - NEVER invent source IDs. Unknown IDs will cause your response to be REJECTED.\n");
        sb.append("5. Do not include HTML, script tags, or executable content.\n");
        sb.append("6. Adapt to audience: ").append(audience != null ? audience : "general audience").append(".\n");
        sb.append("7. Language of content: ").append(language != null ? language : "same as source material").append(".\n");
        sb.append("\n=== AUTHORIZED SOURCES ===\n");

        for (Map.Entry<String, RetrievedChunk> entry : sourceMap.entrySet()) {
            RetrievedChunk chunk = entry.getValue();
            sb.append("[").append(entry.getKey()).append("]\n");
            if (chunk.getDocumentTitle() != null) {
                sb.append("Document: ").append(PromptSanitizer.sanitizeUntrustedPromptText(chunk.getDocumentTitle())).append("\n");
            }
            if (chunk.getSectionTitle() != null) {
                sb.append("Section: ").append(PromptSanitizer.sanitizeUntrustedPromptText(chunk.getSectionTitle())).append("\n");
            }
            if (chunk.getPageStart() != null) {
                sb.append("Pages: ").append(chunk.getPageStart());
                if (chunk.getPageEnd() != null) sb.append("-").append(chunk.getPageEnd());
                sb.append("\n");
            }
            sb.append("Content: ").append(PromptSanitizer.sanitizeUntrustedPromptText(chunk.getContent())).append("\n\n");
        }
        sb.append("=== END SOURCES ===\n");
        return sb.toString();
    }

    private String buildUserPrompt(
            String topic,
            String teacherInstruction,
            int targetSlideCount,
            String audience,
            String language) {

        StringBuilder sb = new StringBuilder();
        sb.append("Generate a presentation outline");
        if (topic != null && !topic.isBlank()) {
            sb.append(" on: ").append(PromptSanitizer.sanitizeUntrustedPromptText(topic.trim()));
        }
        sb.append(".\n");
        sb.append("Target approximately ").append(targetSlideCount).append(" slides.\n");
        if (audience != null && !audience.isBlank()) {
            sb.append("Audience: ").append(PromptSanitizer.sanitizeUntrustedPromptText(audience.trim())).append(".\n");
        }
        if (language != null && !language.isBlank()) {
            sb.append("Output language: ").append(PromptSanitizer.sanitizeUntrustedPromptText(language.trim())).append(".\n");
        }
        if (teacherInstruction != null && !teacherInstruction.isBlank()) {
            sb.append("Teacher instructions: ")
              .append(PromptSanitizer.sanitizeUntrustedPromptText(teacherInstruction.trim())).append(".\n");
        }
        sb.append("Return only valid JSON. No markdown, no code fences.");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse + validate (fail closed on unknown source refs)
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    OutlineJsonSchema parseAndValidateOutline(
            String rawResponse,
            Map<String, RetrievedChunk> sourceMap,
            int targetSlideCount,
            String audience,
            String language) {

        // Strip markdown code fence if present (defensive; prompt says not to include)
        String cleaned = rawResponse == null ? "" : rawResponse.trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end >= 0) cleaned = cleaned.substring(start, end + 1);
        }

        // Parse
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(cleaned,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new OutlineGenerationException.MalformedLlmResponseException(
                    "JSON parse failure: " + e.getMessage(), e);
        }

        // schemaVersion must equal 1
        Object sv = root.get("schemaVersion");
        if (!(sv instanceof Number) || ((Number) sv).intValue() != 1) {
            throw new OutlineGenerationException.OutlineSchemaViolationException("schemaVersion must be 1");
        }

        String title = extractNonBlankString(root, "title", "title");
        String learningObjective = extractNonBlankString(root, "learningObjective", "learningObjective");
        String resolvedAudience = extractStringOrDefault(root, "audience", audience != null ? audience : "general");
        String resolvedLanguage = extractStringOrDefault(root, "language", language != null ? language : "en");

        // sections
        Object sectionsRaw = root.get("sections");
        if (!(sectionsRaw instanceof List) || ((List<?>) sectionsRaw).isEmpty()) {
            throw new OutlineGenerationException.OutlineSchemaViolationException("sections must be non-empty array");
        }

        List<Map<String, Object>> sectionMaps = (List<Map<String, Object>>) sectionsRaw;
        List<OutlineJsonSchema.OutlineSection> sections = new ArrayList<>();
        int totalSlides = 0;

        Set<String> allowedRefs = sourceMap.keySet();

        for (Map<String, Object> sectionMap : sectionMaps) {
            String sectionTitle = extractNonBlankString(sectionMap, "title", "section.title");
            if (sectionTitle.length() > MAX_SECTION_TITLE_LENGTH) {
                sectionTitle = sectionTitle.substring(0, MAX_SECTION_TITLE_LENGTH);
            }
            String sectionPurpose = extractStringOrDefault(sectionMap, "purpose", "");

            Object slidesRaw = sectionMap.get("slides");
            if (!(slidesRaw instanceof List) || ((List<?>) slidesRaw).isEmpty()) {
                throw new OutlineGenerationException.OutlineSchemaViolationException(
                        "Each section must have at least one slide");
            }

            List<Map<String, Object>> slideMaps = (List<Map<String, Object>>) slidesRaw;
            List<OutlineJsonSchema.OutlineSlide> slides = new ArrayList<>();

            for (Map<String, Object> slideMap : slideMaps) {
                String slideTitle = extractNonBlankString(slideMap, "title", "slide.title");
                if (slideTitle.length() > MAX_SLIDE_TITLE_LENGTH) {
                    slideTitle = slideTitle.substring(0, MAX_SLIDE_TITLE_LENGTH);
                }
                String slidePurpose = extractStringOrDefault(slideMap, "purpose", "");
                if (slidePurpose.length() > MAX_SLIDE_PURPOSE_LENGTH) {
                    slidePurpose = slidePurpose.substring(0, MAX_SLIDE_PURPOSE_LENGTH);
                }

                String slideType = extractStringOrDefault(slideMap, "slideType", "CONTENT");
                slideType = normalizeSlideType(slideType);

                // keyPoints
                List<String> keyPoints = new ArrayList<>();
                Object kpsRaw = slideMap.get("keyPoints");
                if (kpsRaw instanceof List) {
                    for (Object kp : (List<?>) kpsRaw) {
                        if (kp instanceof String kpStr && !kpStr.isBlank()) {
                            String truncated = kpStr.length() > MAX_KEY_POINT_LENGTH
                                    ? kpStr.substring(0, MAX_KEY_POINT_LENGTH) : kpStr;
                            keyPoints.add(truncated);
                            if (keyPoints.size() >= MAX_KEY_POINTS_PER_SLIDE) break;
                        }
                    }
                }

                // Normalize aliases and retain only server-issued evidence.
                List<String> sourceRefs = validateSourceRefs(slideMap, allowedRefs, slideType);
                if (("CONTENT".equals(slideType) || "SUMMARY".equals(slideType)) && sourceRefs.isEmpty()) continue;

                slides.add(new OutlineJsonSchema.OutlineSlide(
                        slideTitle, slidePurpose, slideType, keyPoints, sourceRefs));
                totalSlides++;

                if (totalSlides > MAX_SLIDES_TOTAL) {
                    throw new OutlineGenerationException.OutlineSchemaViolationException(
                            "Too many slides: exceeded maximum of " + MAX_SLIDES_TOTAL);
                }
            }
            if (!slides.isEmpty()) sections.add(new OutlineJsonSchema.OutlineSection(sectionTitle, sectionPurpose, slides));
        }

        // target slide count policy: allow ±50% tolerance
        int minSlides = Math.max(1, targetSlideCount / 2);
        int maxSlides = targetSlideCount * 2;
        if (totalSlides < minSlides || totalSlides > maxSlides) {
            throw new OutlineGenerationException.OutlineSchemaViolationException(
                    "Slide count " + totalSlides + " outside acceptable range [" + minSlides + ", " + maxSlides + "]");
        }

        return new OutlineJsonSchema(1, title, learningObjective, resolvedAudience, resolvedLanguage, sections);
    }

    private List<String> validateSourceRefs(Map<String, Object> slideMap, Set<String> allowedRefs, String slideType) {
        Object refsRaw = slideMap.get("sourceRefs");
        List<String> sourceRefs = new ArrayList<>();

        if (refsRaw instanceof List) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object ref : (List<?>) refsRaw) {
                if (ref instanceof String refStr) {
                    String normalized = refStr.trim();
                    if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1).trim();
                    if (!allowedRefs.contains(normalized)) continue;
                    seen.add(normalized); // deduplicate
                }
            }
            sourceRefs.addAll(seen);
        }

        return sourceRefs;
    }

    private String normalizeSlideType(String raw) {
        if (raw == null) return "CONTENT";
        return switch (raw.trim().toUpperCase()) {
            case "TITLE" -> "TITLE";
            case "SECTION" -> "SECTION";
            case "SUMMARY" -> "SUMMARY";
            default -> "CONTENT";
        };
    }

    private String extractNonBlankString(Map<String, Object> map, String key, String fieldName) {
        Object val = map.get(key);
        if (!(val instanceof String str) || str.isBlank()) {
            throw new OutlineGenerationException.OutlineSchemaViolationException(
                    "Field '" + fieldName + "' must be a non-blank string");
        }
        return str.trim();
    }

    private String extractStringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val instanceof String str && !str.isBlank()) return str.trim();
        return defaultVal;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job state transitions
    // ─────────────────────────────────────────────────────────────────────────

    private boolean transitionToRunning(Long jobId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            return jobRepository.findById(jobId).map(job -> {
                if (!PresentationJobStatus.QUEUED.equals(job.getStatus())) {
                    return false;
                }
                job.setStatus(PresentationJobStatus.RUNNING);
                job.setStartedAt(LocalDateTime.now(clock));
                job.setAttemptCount(job.getAttemptCount() + 1);
                jobRepository.save(job);
                return true;
            }).orElse(false);
        }));
    }

    private void updateProgress(Long jobId, int current) {
        transactionTemplate.execute(status -> {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setProgressCurrent(current);
                jobRepository.save(job);
            });
            return null;
        });
    }

    private void failJob(Long jobId, String errorCode, String safeErrorSummary) {
        try {
            finalizerService.failOutlineJob(jobId, errorCode, safeErrorSummary);
        } catch (Exception e) {
            log.error("Failed to mark job {} as FAILED", jobId, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildRetrievalQuery(String topic, String teacherInstruction) {
        StringBuilder query = new StringBuilder();
        if (topic != null && !topic.isBlank()) query.append(topic.trim());
        if (teacherInstruction != null && !teacherInstruction.isBlank()) {
            if (query.length() > 0) query.append(" ");
            query.append(teacherInstruction.trim());
        }
        return query.length() > 0 ? query.toString() : "presentation overview";
    }

    private String buildScopeSnapshot(
            Map<String, RetrievedChunk> sourceMap,
            List<Long> requestedDocumentIds,
            Long notebookId) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("notebookId", notebookId);
            if (requestedDocumentIds != null && !requestedDocumentIds.isEmpty()) {
                snapshot.put("requestedDocumentIds", requestedDocumentIds);
            }
            List<Map<String, Object>> sources = new ArrayList<>();
            for (Map.Entry<String, RetrievedChunk> e : sourceMap.entrySet()) {
                RetrievedChunk c = e.getValue();
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("alias", e.getKey());
                src.put("documentId", c.getDocumentId());
                src.put("chunkId", c.getChunkId());
                src.put("indexVersion", c.getIndexVersion());
                if (c.getPageStart() != null) src.put("pageStart", c.getPageStart());
                if (c.getPageEnd() != null) src.put("pageEnd", c.getPageEnd());
                if (c.getSectionTitle() != null) src.put("sectionTitle", c.getSectionTitle());
                sources.add(src);
            }
            snapshot.put("sources", sources);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{\"error\":\"scope snapshot serialization failed\"}";
        }
    }

    private void assertNotInTransaction(String stage) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(stage + " must not run inside a DB transaction");
        }
    }
}
