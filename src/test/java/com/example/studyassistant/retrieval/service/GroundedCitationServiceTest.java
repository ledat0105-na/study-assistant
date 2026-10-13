package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedCitationServiceTest {

    private GroundedCitationService service;
    private Map<String, CitationDto> sourceMap;

    @BeforeEach
    void setUp() {
        service = new GroundedCitationService();
        sourceMap = new LinkedHashMap<>();
        sourceMap.put("S1", CitationDto.builder().citationId("S1").build());
        sourceMap.put("S2", CitationDto.builder().citationId("S2").build());
    }

    @Test
    void c1_validS1() {
        GroundedAnswer answer = service.processCitations("This is a fact [S1].", sourceMap);
        assertEquals("This is a fact [S1].", answer.getAnswer());
        assertEquals(1, answer.getCitations().size());
        assertEquals("S1", answer.getCitations().get(0).getCitationId());
    }

    @Test
    void c2_multipleValid() {
        GroundedAnswer answer = service.processCitations("Facts [S1] and [S2].", sourceMap);
        assertEquals("Facts [S1] and [S2].", answer.getAnswer());
        assertEquals(2, answer.getCitations().size());
    }

    @Test
    void c3_unknownS999() {
        GroundedAnswer answer = service.processCitations("Fact [S999].", sourceMap);
        assertEquals("Fact .", answer.getAnswer());
        assertTrue(answer.getCitations().isEmpty());
    }

    @Test
    void c4_mixedValidAndInvalid() {
        GroundedAnswer answer = service.processCitations("Fact [S1] and [S999].", sourceMap);
        assertEquals("Fact [S1] and .", answer.getAnswer());
        assertEquals(1, answer.getCitations().size());
        assertEquals("S1", answer.getCitations().get(0).getCitationId());
    }

    @Test
    void c5_duplicateSourceCitations() {
        GroundedAnswer answer = service.processCitations("Fact [S1] and [S1].", sourceMap);
        assertEquals("Fact [S1] and [S1].", answer.getAnswer());
        assertEquals(1, answer.getCitations().size()); // Extracted uniquely via validCitedIds set
    }

    @Test
    void c6_noCitations() {
        GroundedAnswer answer = service.processCitations("Just a fact.", sourceMap);
        assertEquals("Just a fact.", answer.getAnswer());
        assertTrue(answer.getCitations().isEmpty());
    }

    @Test
    void c7_malformedSyntax() {
        GroundedAnswer answer = service.processCitations("Fact [S 1] [S1 [1] (S1).", sourceMap);
        assertEquals("Fact [S 1] [S1 [1] (S1).", answer.getAnswer()); // Regex won't match these
        assertTrue(answer.getCitations().isEmpty());
    }

    @Test
    void c8_sourceMapIsSoleAuthority() {
        // Even if we say [S1], if sourceMap doesn't have it, it's stripped
        sourceMap.clear();
        GroundedAnswer answer = service.processCitations("Fact [S1].", sourceMap);
        assertEquals("Fact .", answer.getAnswer());
        assertTrue(answer.getCitations().isEmpty());
    }
}
