package com.example.studyassistant.service;

/** A bounded active chunk already has enough concepts; preserve all existing evidence. */
class ConceptCandidateLimitException extends RuntimeException {
    ConceptCandidateLimitException() { super("Active chunk concept limit reached"); }
}
