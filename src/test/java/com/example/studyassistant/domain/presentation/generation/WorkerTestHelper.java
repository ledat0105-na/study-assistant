package com.example.studyassistant.domain.presentation.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;

import java.time.Clock;

/**
 * Test helper to create a minimal PresentationOutlineGenerationWorker
 * with only the ObjectMapper wired, for parse/validate unit tests.
 */
class WorkerTestHelper {

    static PresentationOutlineGenerationWorker buildMinimalWorker() {
        ObjectMapper om = new ObjectMapper();
        return new PresentationOutlineGenerationWorker(
                Mockito.mock(com.example.studyassistant.domain.presentation.PresentationGenerationJobRepository.class),
                Mockito.mock(com.example.studyassistant.retrieval.service.RetrievalService.class),
                Mockito.mock(com.example.studyassistant.llm.LlmProvider.class),
                om,
                Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class),
                Clock.systemUTC(),
                Mockito.mock(PresentationOutlinePersistenceService.class),
                Mockito.mock(PresentationJobFinalizerService.class),
                Mockito.mock(PresentationEvidenceSelector.class)
        );
    }
}
