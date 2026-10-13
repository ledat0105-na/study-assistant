package com.example.studyassistant.domain.presentation.generation;

import org.springframework.stereotype.Component;

@Component
public class NoOpSlideGenerationLifecycleHook implements SlideGenerationLifecycleHook {
    @Override
    public void beforeSlidePersistence(Long jobId, Long slideId) {
        // intentional no-op for production path
    }
}
