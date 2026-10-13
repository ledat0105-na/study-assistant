package com.example.studyassistant.retrieval.service;

public interface TokenCounter {
    /**
     * Approximates or exactly counts the number of tokens in the given text.
     * @param text The text to count tokens for.
     * @return The token count.
     */
    int count(String text);
}
