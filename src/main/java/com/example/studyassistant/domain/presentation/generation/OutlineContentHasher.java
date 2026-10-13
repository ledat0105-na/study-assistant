package com.example.studyassistant.domain.presentation.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic SHA-256 hashing utilities for outline generation.
 *
 * Two hash functions:
 *
 * 1. contentHash(OutlineJsonSchema) — hashes the validated outline content.
 *    Equivalent validated outlines produce the same hash regardless of LLM
 *    whitespace variations or JSON property ordering.
 *    Canonical form: sorted section/slide field concatenation, lowercase, NFKC normalized.
 *
 * 2. requestHash(fields...) — hashes the semantic generation request.
 *    Used for idempotency key discrimination.
 *    Canonical form: stable ordered fields, trimmed, nulls normalized to "".
 */
public final class OutlineContentHasher {

    private static final int SCHEMA_VERSION = 1;

    private OutlineContentHasher() {}

    /**
     * Computes deterministic SHA-256 content hash for a validated outline.
     * Input: canonical string built from validated fields, NOT raw LLM text.
     * Returns 64-character lowercase hex.
     */
    public static String contentHash(OutlineJsonSchema outline) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("v").append(SCHEMA_VERSION).append("\n");
        canonical.append("title:").append(normalize(outline.title())).append("\n");
        canonical.append("objective:").append(normalize(outline.learningObjective())).append("\n");
        canonical.append("audience:").append(normalize(outline.audience())).append("\n");
        canonical.append("language:").append(normalize(outline.language())).append("\n");

        if (outline.sections() != null) {
            for (OutlineJsonSchema.OutlineSection section : outline.sections()) {
                canonical.append("section:").append(normalize(section.title())).append("\n");
                canonical.append("purpose:").append(normalize(section.purpose())).append("\n");
                if (section.slides() != null) {
                    for (OutlineJsonSchema.OutlineSlide slide : section.slides()) {
                        canonical.append("slide:").append(normalize(slide.title())).append("\n");
                        canonical.append("slidepurpose:").append(normalize(slide.purpose())).append("\n");
                        if (slide.keyPoints() != null) {
                            for (String kp : slide.keyPoints()) {
                                canonical.append("kp:").append(normalize(kp)).append("\n");
                            }
                        }
                        if (slide.sourceRefs() != null) {
                            List<String> sorted = new ArrayList<>(slide.sourceRefs());
                            sorted.sort(String::compareTo);
                            for (String ref : sorted) {
                                canonical.append("ref:").append(ref).append("\n");
                            }
                        }
                    }
                }
            }
        }
        return sha256Hex(canonical.toString());
    }

    /**
     * Computes deterministic SHA-256 request hash for idempotency discrimination.
     *
     * Canonical fields (in this order):
     *   schema_version=1
     *   deckId
     *   topic (normalized)
     *   teacherInstruction (normalized)
     *   targetSlideCount (or "" if null)
     *   audience (normalized, or "")
     *   language (normalized, or "")
     *   documentIds (sorted, comma-joined, or "")
     */
    public static String requestHash(
            long deckId,
            String topic,
            String teacherInstruction,
            Integer targetSlideCount,
            String audience,
            String language,
            List<Long> documentIds
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("schema_version=1\n");
        canonical.append("deckId=").append(deckId).append("\n");
        canonical.append("topic=").append(normalize(topic)).append("\n");
        canonical.append("teacherInstruction=").append(normalize(teacherInstruction)).append("\n");
        canonical.append("targetSlideCount=").append(targetSlideCount != null ? targetSlideCount : "").append("\n");
        canonical.append("audience=").append(normalize(audience)).append("\n");
        canonical.append("language=").append(normalize(language)).append("\n");

        if (documentIds != null && !documentIds.isEmpty()) {
            List<Long> sorted = new ArrayList<>(documentIds);
            sorted.sort(Long::compareTo);
            StringBuilder ids = new StringBuilder();
            for (Long id : sorted) {
                if (ids.length() > 0) ids.append(",");
                ids.append(id);
            }
            canonical.append("documentIds=").append(ids).append("\n");
        } else {
            canonical.append("documentIds=\n");
        }
        return sha256Hex(canonical.toString());
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return nfkc.trim().replaceAll("\\s+", " ");
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
