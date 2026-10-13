package com.example.studyassistant.domain.presentation.generation;

public interface SlideGenerationLifecycleHook {
    void beforeSlidePersistence(Long jobId, Long slideId);
}
