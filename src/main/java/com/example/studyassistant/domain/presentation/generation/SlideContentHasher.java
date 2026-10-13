package com.example.studyassistant.domain.presentation.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

/**
 * Deterministic SHA-256 hashing for T1.5 slide generation request identity.
 *
 * schema_version=2 for all slide job hashes.
 * NFKC + trim + whitespace collapse. NO lowercase.
 */
public final class SlideContentHasher {

    private SlideContentHasher() {}

    /**
     * Request hash for a full-deck SLIDES job.
     * Canonical fields:
     *   schema_version=2
     *   jobType=SLIDES
     *   deckId
     *   approvedOutlineRevisionId
     *   approvedOutlineContentHash
     */
    public static String fullJobRequestHash(long deckId, long revisionId, String contentHash) {
        String canonical = "schema_version=2\n" +
                "jobType=SLIDES\n" +
                "deckId=" + deckId + "\n" +
                "approvedOutlineRevisionId=" + revisionId + "\n" +
                "approvedOutlineContentHash=" + normalize(contentHash) + "\n";
        return sha256Hex(canonical);
    }

    /**
     * Request hash for a SLIDE_RETRY job.
     * Canonical fields:
     *   schema_version=2
     *   jobType=SLIDE_RETRY
     *   deckId
     *   approvedOutlineRevisionId
     *   approvedOutlineContentHash
     *   targetSlideOrder
     */
    public static String retryRequestHash(long deckId, long revisionId, String contentHash, int targetSlideOrder) {
        String canonical = "schema_version=2\n" +
                "jobType=SLIDE_RETRY\n" +
                "deckId=" + deckId + "\n" +
                "approvedOutlineRevisionId=" + revisionId + "\n" +
                "approvedOutlineContentHash=" + normalize(contentHash) + "\n" +
                "targetSlideOrder=" + targetSlideOrder + "\n";
        return sha256Hex(canonical);
    }

    /**
     * Evidence hash for a text excerpt.
     */
    public static String evidenceHash(String evidenceExcerpt) {
        if (evidenceExcerpt == null) return sha256Hex("");
        return sha256Hex(evidenceExcerpt);
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
