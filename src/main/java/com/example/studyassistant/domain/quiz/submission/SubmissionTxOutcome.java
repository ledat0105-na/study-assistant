package com.example.studyassistant.domain.quiz.submission;

public record SubmissionTxOutcome(
    OutcomeType type,
    QuizAttemptResultResponse response,
    String message
) {
    public enum OutcomeType {
        COMPLETED, EXPIRED, CONFLICT, BAD_REQUEST, NOT_FOUND
    }
    
    public static SubmissionTxOutcome completed(QuizAttemptResultResponse r) { return new SubmissionTxOutcome(OutcomeType.COMPLETED, r, null); }
    public static SubmissionTxOutcome expired(String msg) { return new SubmissionTxOutcome(OutcomeType.EXPIRED, null, msg); }
    public static SubmissionTxOutcome conflict(String msg) { return new SubmissionTxOutcome(OutcomeType.CONFLICT, null, msg); }
    public static SubmissionTxOutcome badRequest(String msg) { return new SubmissionTxOutcome(OutcomeType.BAD_REQUEST, null, msg); }
    public static SubmissionTxOutcome notFound(String msg) { return new SubmissionTxOutcome(OutcomeType.NOT_FOUND, null, msg); }
}
