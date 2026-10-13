package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationGenerationJob;
import com.example.studyassistant.domain.presentation.PresentationGenerationJobRepository;
import com.example.studyassistant.domain.presentation.PresentationJobStatus;
import com.example.studyassistant.domain.presentation.PresentationJobType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PresentationJobRecoveryServiceTest {

    @Mock
    private PresentationGenerationJobRepository jobRepository;

    @Mock
    private PresentationJobFinalizerService finalizerService;

    @InjectMocks
    private PresentationJobRecoveryService recoveryService;

    @Test
    void recoverOrphanedJobs_shouldCancelAllActiveJobs() {
        // Given
        PresentationGenerationJob job1 = new PresentationGenerationJob();
        ReflectionTestUtils.setField(job1, "id", 101L);
        job1.setStatus(PresentationJobStatus.QUEUED);
        job1.setJobType(PresentationJobType.SLIDE_RETRY);

        PresentationGenerationJob job2 = new PresentationGenerationJob();
        ReflectionTestUtils.setField(job2, "id", 102L);
        job2.setStatus(PresentationJobStatus.RUNNING);
        job2.setJobType(PresentationJobType.OUTLINE);

        when(jobRepository.findByStatusIn(List.of(PresentationJobStatus.QUEUED, PresentationJobStatus.RUNNING)))
                .thenReturn(List.of(job1, job2));

        // When
        recoveryService.recoverOrphanedJobs();

        // Then
        verify(jobRepository, times(1)).findByStatusIn(anyList());
        verify(finalizerService, times(1)).cancelOrphanedJob(101L);
        verify(finalizerService, times(1)).cancelOrphanedJob(102L);
        verifyNoMoreInteractions(finalizerService);
    }

    @Test
    void recoverOrphanedJobs_shouldDoNothingWhenNoOrphanedJobs() {
        // Given
        when(jobRepository.findByStatusIn(List.of(PresentationJobStatus.QUEUED, PresentationJobStatus.RUNNING)))
                .thenReturn(List.of());

        // When
        recoveryService.recoverOrphanedJobs();

        // Then
        verify(jobRepository, times(1)).findByStatusIn(anyList());
        verifyNoInteractions(finalizerService);
    }
}
