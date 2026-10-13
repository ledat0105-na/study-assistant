package com.example.studyassistant.domain.presentation.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaStorageReconciliationRunner implements ApplicationRunner {
    private final PresentationMediaService mediaService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            mediaService.reconcileStorage();
        } catch (Exception e) {
            log.warn("Presentation media storage reconciliation did not complete", e);
        }
    }
}
