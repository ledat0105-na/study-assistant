package com.example.studyassistant.llm;

public interface LlmProvider {
    /**
     * Generates a text response from the LLM based on system and user prompts.
     * @param systemPrompt System instructions
     * @param userPrompt User data/question
     * @param temperature Generation temperature
     * @return Generated text
     */
    String generate(String systemPrompt, String userPrompt, double temperature);
}
