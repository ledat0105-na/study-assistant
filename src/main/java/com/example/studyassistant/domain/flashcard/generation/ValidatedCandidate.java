package com.example.studyassistant.domain.flashcard.generation;

import java.util.List;

/**
 * Internal DTO: a fully validated, hash-computed candidate ready for persistence.
 * Never exposed outside the generation package.
 */
record ValidatedCandidate(
    String question,
    String answer,
    String contentHash,
    List<String> citedLabels  // ordered, validated, unique source labels e.g. ["S1","S3"]
) {}
