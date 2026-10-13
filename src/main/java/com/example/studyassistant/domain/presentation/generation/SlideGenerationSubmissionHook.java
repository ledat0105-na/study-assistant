package com.example.studyassistant.domain.presentation.generation;

import java.time.LocalDate;

public interface SlideGenerationSubmissionHook {
    void afterQuotaReservation(Long ownerId, LocalDate reservationDate, int reservedCount);
}