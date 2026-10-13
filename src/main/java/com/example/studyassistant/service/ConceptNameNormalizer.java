package com.example.studyassistant.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class ConceptNameNormalizer {

    public String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Concept name cannot be null");
        }

        // 1. Unicode NFKC normalization
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);

        // 2. Trim leading/trailing whitespace
        normalized = normalized.trim();

        // 3. Collapse consecutive whitespace into one ASCII space
        normalized = normalized.replaceAll("\\s+", " ");

        // 4. Lowercase using Locale.ROOT
        normalized = normalized.toLowerCase(Locale.ROOT);

        // 5. Validate non-empty
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Concept name cannot be empty or only whitespace");
        }

        return normalized;
    }
    
    public String canonicalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Concept name cannot be null");
        }
        
        // 1. Unicode NFKC
        String canonical = Normalizer.normalize(input, Normalizer.Form.NFKC);
        
        // 2. Trim
        canonical = canonical.trim();
        
        // 3. Collapse whitespace
        canonical = canonical.replaceAll("\\s+", " ");
        
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException("Concept name cannot be empty or only whitespace");
        }
        
        return canonical;
    }
}
