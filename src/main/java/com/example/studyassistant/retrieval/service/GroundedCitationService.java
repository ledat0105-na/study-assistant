package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GroundedCitationService {

    public GroundedAnswer processCitations(String rawAnswer, Map<String, CitationDto> sourceMap) {
        Pattern pattern = Pattern.compile("\\[([SsGgKkRr]\\d+)\\]");
        Matcher matcher = pattern.matcher(rawAnswer);
        
        Set<String> validCitedIds = new HashSet<>();
        StringBuffer sanitizedAnswer = new StringBuffer();
        
        while (matcher.find()) {
            String citedId = matcher.group(1);
            if (citedId.matches("S\\d+")) {
                if (sourceMap.containsKey(citedId)) {
                    validCitedIds.add(citedId);
                    matcher.appendReplacement(sanitizedAnswer, "[" + citedId + "]");
                } else {
                    log.warn("LLM invented invalid S citation ID: {}", citedId);
                    matcher.appendReplacement(sanitizedAnswer, "");
                }
            } else {
                log.warn("LLM used unsupported citation namespace: {}", citedId);
                matcher.appendReplacement(sanitizedAnswer, "");
            }
        }
        matcher.appendTail(sanitizedAnswer);
        
        String finalAnswer = sanitizedAnswer.toString().replaceAll(" +", " ").trim();
        
        List<CitationDto> validCitations = new ArrayList<>();
        // To maintain order or standard behavior, we use the values from the map 
        // that are linked to validCitedIds. We iterate over the authoritative map to keep its inherent order.
        for (CitationDto dto : sourceMap.values()) {
            if (validCitedIds.contains(dto.getCitationId())) {
                validCitations.add(dto);
            }
        }
        
        String quality = "VALID";
        if (validCitations.isEmpty()) {
            quality = "MISSING";
        }
        
        return GroundedAnswer.builder()
                .answer(finalAnswer)
                .citations(validCitations)
                .hasSufficientEvidence(true)
                .degraded(false)
                .citationQuality(quality)
                .build();
    }
}
