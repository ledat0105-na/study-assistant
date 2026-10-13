package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationGenerationJob;
import com.example.studyassistant.domain.presentation.PresentationGenerationJobRepository;
import com.example.studyassistant.domain.presentation.PresentationJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Recovers PresentationGenerationJobs that were left in QUEUED or RUNNING
 * state due to an unexpected application termination (server restart/crash).
 *
 * Runs once upon ApplicationReadyEvent, safely marking them CANCELLED
 * and releasing their exactly-once quota.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationJobRecoveryService {

    private final PresentationGenerationJobRepository jobRepository;
    private final PresentationJobFinalizerService finalizerService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanedJobs() {
        log.info("Starting recovery of orphaned presentation jobs...");

        List<PresentationGenerationJob> orphanedJobs = jobRepository.findByStatusIn(
                List.of(PresentationJobStatus.QUEUED, PresentationJobStatus.RUNNING)
        );

        if (orphanedJobs.isEmpty()) {
            log.info("No orphaned presentation jobs found. Recovery complete.");
            return;
        }

        int recoveredCount = 0;
        for (PresentationGenerationJob job : orphanedJobs) {
            try {
                log.info("Recovering orphaned job: id={}, type={}, status={}",
                        job.getId(), job.getJobType(), job.getStatus());

                finalizerService.cancelOrphanedJob(job.getId());
                recoveredCount++;
            } catch (Exception e) {
                log.error("Failed to recover orphaned job: id={}", job.getId(), e);
            }
        }

        log.info("Finished recovery of orphaned presentation jobs. Recovered {} jobs.", recoveredCount);
    }
}
