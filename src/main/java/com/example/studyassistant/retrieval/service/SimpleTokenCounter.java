package com.example.studyassistant.retrieval.service;

import org.springframework.stereotype.Component;

/**
 * Deterministic approximation for token counting in Checkpoint 2.
 * Assuming roughly 1 word ≈ 1.33 tokens for mixed text (English/Vietnamese).
 * This will be replaced by a real model tokenizer in future checkpoints.
 */
@Component
public class SimpleTokenCounter implements TokenCounter {
    @Override
    public int count(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // Split by whitespace to approximate words
        String[] words = text.trim().split("\\s+");
        // Use a deterministic multiplier (e.g. 1.33)
        // Ensure minimum 1 token for non-empty string
        return Math.max(1, (int) Math.ceil(words.length * 1.33));
    }
}
