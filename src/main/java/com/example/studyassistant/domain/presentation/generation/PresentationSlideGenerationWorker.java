package com.example.studyassistant.domain.presentation.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PresentationSlideGenerationWorker {

    private final PresentationSlideGenerationProcessor processor;

    @Async
    public void generateSlidesAsync(
            long jobId, long deckId, long ownerId,
            long notebookId, List<Long> frozenDocumentIds,
            long outlineRevisionId, String outlineContentHash,
            List<PlannedSlide> targetSlides) {

        processor.processJob(jobId, deckId, ownerId, notebookId, frozenDocumentIds, outlineRevisionId, outlineContentHash, targetSlides);
    }
}
