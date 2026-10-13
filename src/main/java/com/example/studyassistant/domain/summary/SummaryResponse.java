package com.example.studyassistant.domain.summary;

import java.time.LocalDateTime;
import java.util.List;

public record SummaryResponse(
        Long docId,
        String summary,
        boolean cached,
        String indexVersion,
        LocalDateTime generatedAt,
        List<SummaryCitation> citations,
        SummaryContent content) {
}
