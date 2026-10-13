package com.example.studyassistant.domain.flashcard.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Deterministic SHA-256 content hash for flashcard duplicate detection.
 *
 * Normalization V1:
 * 1. Unicode NFKC normalization
 * 2. Trim
 * 3. Collapse consecutive whitespace to single space
 * 4. Lowercase using Locale.ROOT
 *
 * Canonical input: normalize(question) + "\n---\n" + normalize(answer)
 *
 * Returns 64-character lowercase hexadecimal.
 *
 * The original question/answer text is preserved after a simple trim for display;
 * only the canonical form is used for hashing. No semantic/fuzzy dedupe in V1.
 *
 * Duplicate scope: same Document + same contentHash + deletedAt IS NULL.
 * A same-content card in a different Document is allowed.
 * Legacy cards with contentHash == null are not retroactively hashed in SF1.6.
 */
public final class FlashcardContentHasher {

    private FlashcardContentHasher() {}

    public static String hash(String question, String answer) {
        String canonical = normalize(question) + "\n---\n" + normalize(answer);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String normalize(String text) {
        if (text == null) return "";
        // Step 1: Unicode NFKC
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        // Step 2: Trim
        String trimmed = nfkc.trim();
        // Step 3: Collapse whitespace
        String collapsed = trimmed.replaceAll("\\s+", " ");
        // Step 4: Locale.ROOT lowercase
        return collapsed.toLowerCase(Locale.ROOT);
    }
}
