package com.example.studyassistant.dto;

public record TutorSessionState(
    Long tutorSessionId,
    Long conversationId,
    Long currentConceptId
) {}
