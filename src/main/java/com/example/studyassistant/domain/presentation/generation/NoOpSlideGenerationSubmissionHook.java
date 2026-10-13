package com.example.studyassistant.domain.presentation.generation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NoOpSlideGenerationSubmissionHook implements SlideGenerationSubmissionHook {
    @Override
    public void afterQuotaReservation(Long ownerId, LocalDate reservationDate, int reservedCount) {
    }
}