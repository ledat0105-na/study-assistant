package com.example.studyassistant.domain.quiz.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

public class QuizContentHasher {
    private QuizContentHasher() {}

    public static String normalizeShortAnswer(String text) {
        if (text == null) return "";
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return nfkc.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String hash(String canonicalDefinition) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalDefinition.getBytes(StandardCharsets.UTF_8));
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
