package com.example.studyassistant.domain.summary;

import java.util.List;

public record SummaryContent(
        String overview,
        List<KeyPoint> keyPoints,
        List<ImportantTerm> importantTerms,
        String conclusion,
        List<String> sourceRefs) {

    public record KeyPoint(String text, List<String> sourceRefs) {
    }

    public record ImportantTerm(String term, String definition, List<String> sourceRefs) {
    }
}
