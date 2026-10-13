package com.example.studyassistant.service.tutor;

public record TutorTurnCommand(
    Long userId,
    Long conversationId,
    String message,
    Long conceptId,
    TutorRequestedMode requestedMode
) {}
