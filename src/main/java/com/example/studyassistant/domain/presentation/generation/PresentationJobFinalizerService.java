package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationGenerationJob;
import com.example.studyassistant.domain.presentation.PresentationJobStatus;
import com.example.studyassistant.entity.UserDailyUsage;
import com.example.studyassistant.domain.presentation.PresentationGenerationJobRepository;
import com.example.studyassistant.repository.UserDailyUsageRepository;
import com.example.studyassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PresentationJobFinalizerService {

    private final UserRepository userRepository;
    private final PresentationGenerationJobRepository jobRepository;
    private final UserDailyUsageRepository userDailyUsageRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void failOutlineJob(long jobId, String errorCode, String errorSummary) {
        // Use the same lock order as submission: owner, then job.
        Long ownerId = jobRepository.findById(jobId).orElseThrow().getOwner().getId();
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(ownerId).orElseThrow();
            PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
            if (job.getStatus() != PresentationJobStatus.QUEUED && job.getStatus() != PresentationJobStatus.RUNNING) return;
            if (job.getJobType() != com.example.studyassistant.domain.presentation.PresentationJobType.OUTLINE) {
                throw new IllegalArgumentException("Expected outline job");
            }
            UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(
                    ownerId, job.getCreatedAt().toLocalDate()).orElseThrow(() -> new IllegalStateException("Usage row missing"));
            int current = usage.getPresentationOutlineCount() == null ? 0 : usage.getPresentationOutlineCount();
            if (current < 1) throw new IllegalStateException("Outline reservation missing");
            usage.setPresentationOutlineCount(current - 1);
            userDailyUsageRepository.save(usage);
            job.setStatus(PresentationJobStatus.FAILED);
            job.setErrorCode(errorCode); job.setErrorSummary(errorSummary);
            job.setCompletedAt(LocalDateTime.now(clock));
            jobRepository.save(job);
        });
    }

    public void finalizeSlideJob(long jobId, long ownerId, PresentationJobStatus terminalStatus, String errorCode, String errorSummary) {
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(ownerId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));

            if (job.getStatus() == PresentationJobStatus.SUCCEEDED ||
                job.getStatus() == PresentationJobStatus.FAILED ||
                job.getStatus() == PresentationJobStatus.CANCELLED) {
                return;
            }

            int progressTotal = job.getProgressTotal() != null ? job.getProgressTotal() : 0;
            int progressCurrent = job.getProgressCurrent() != null ? job.getProgressCurrent() : 0;

            if (progressTotal < 0 || progressCurrent < 0 || progressCurrent > progressTotal) {
                throw new IllegalStateException("Invalid progress state");
            }

            int release = progressTotal - progressCurrent;

            if (release > 0) {
                LocalDate reservationDate = job.getCreatedAt().toLocalDate();
                UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(ownerId, reservationDate)
                        .orElseThrow(() -> new IllegalStateException("Usage row missing"));

                if (release > usage.getPresentationSlideCount()) {
                    throw new IllegalStateException("Cannot release more than current usage");
                }

                usage.setPresentationSlideCount(usage.getPresentationSlideCount() - release);
                userDailyUsageRepository.save(usage);
            }

            job.setStatus(terminalStatus);
            job.setErrorCode(errorCode);
            job.setErrorSummary(errorSummary);
            job.setCompletedAt(LocalDateTime.now(clock));
            jobRepository.save(job);
        });
    }

    public void cancelOrphanedJob(long jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));

            if (job.getStatus() == PresentationJobStatus.SUCCEEDED ||
                job.getStatus() == PresentationJobStatus.FAILED ||
                job.getStatus() == PresentationJobStatus.CANCELLED) {
                return; // Already terminal
            }

            Long ownerId = job.getOwner().getId();
            userRepository.findByIdForUpdate(ownerId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            if (job.getJobType() == com.example.studyassistant.domain.presentation.PresentationJobType.SLIDES ||
                job.getJobType() == com.example.studyassistant.domain.presentation.PresentationJobType.SLIDE_RETRY) {
                
                int progressTotal = job.getProgressTotal() != null ? job.getProgressTotal() : 0;
                int progressCurrent = job.getProgressCurrent() != null ? job.getProgressCurrent() : 0;
                
                int release = progressTotal - progressCurrent;
                if (release > 0) {
                    LocalDate reservationDate = job.getCreatedAt().toLocalDate();
                    UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(ownerId, reservationDate)
                            .orElse(null);
                    
                    if (usage != null) {
                        int currentCount = usage.getPresentationSlideCount() != null ? usage.getPresentationSlideCount() : 0;
                        int safeRelease = Math.min(release, currentCount);
                        usage.setPresentationSlideCount(currentCount - safeRelease);
                        userDailyUsageRepository.save(usage);
                    }
                }
            } else if (job.getJobType() == com.example.studyassistant.domain.presentation.PresentationJobType.OUTLINE) {
                LocalDate reservationDate = job.getCreatedAt().toLocalDate();
                UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(ownerId, reservationDate)
                        .orElse(null);
                
                if (usage != null) {
                    int currentCount = usage.getPresentationOutlineCount() != null ? usage.getPresentationOutlineCount() : 0;
                    if (currentCount > 0) {
                        usage.setPresentationOutlineCount(currentCount - 1);
                        userDailyUsageRepository.save(usage);
                    }
                }
            }

            job.setStatus(PresentationJobStatus.CANCELLED);
            job.setErrorCode("SERVER_RESTART");
            job.setErrorSummary("Job was abandoned due to a server restart and has been safely cancelled.");
            job.setCompletedAt(LocalDateTime.now(clock));
            jobRepository.save(job);
        });
    }
}
