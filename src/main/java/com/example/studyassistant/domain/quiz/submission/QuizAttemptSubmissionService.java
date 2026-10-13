package com.example.studyassistant.domain.quiz.submission;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.QuizMasteryEvidenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAttemptSubmissionService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository attemptAnswerRepository;
    private final QuizAttemptAnswerOptionRepository optionRepository;
    private final QuizAttemptAnswerAcceptedAnswerRepository acceptedAnswerRepository;
    private final QuizAttemptAnswerConceptRepository conceptRepository;
    private final QuizMasteryEvidenceAdapter masteryAdapter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public QuizAttemptResultResponse submit(QuizAttemptSubmitCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Quiz submission must run outside a database transaction");
        }

        if (command.submissionToken() == null || command.submissionToken().isBlank()) {
            throw new IllegalArgumentException("Token is null or blank");
        }
        try {
            UUID.fromString(command.submissionToken());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Token is not a valid UUID");
        }
        
        final LocalDateTime submissionReceivedAt = LocalDateTime.now(clock);

        SubmissionTxOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
            QuizAttempt attempt = attemptRepository.findByIdAndUserIdWithPessimisticWrite(command.attemptId(), command.userId())
                .orElse(null);
            if (attempt == null) {
                return SubmissionTxOutcome.notFound("Attempt not found");
            }

            if ("EXPIRED".equals(attempt.getStatus())) {
                return SubmissionTxOutcome.conflict("Attempt is EXPIRED");
            }
            
            if (!"IN_PROGRESS".equals(attempt.getStatus()) && !"COMPLETED".equals(attempt.getStatus())) {
                return SubmissionTxOutcome.conflict("Corrupt or unknown status");
            }

            // Verify deadline for EXAM
            if (attempt.getModeSnapshot() == QuizMode.EXAM && attempt.getTimeLimitSecondsSnapshot() != null) {
                LocalDateTime deadline = attempt.getStartedAt().plusSeconds(attempt.getTimeLimitSecondsSnapshot());
                if (submissionReceivedAt.isAfter(deadline)) {
                    attempt.setStatus("EXPIRED");
                    attemptRepository.save(attempt);
                    return SubmissionTxOutcome.expired("Attempt is EXPIRED (missed deadline)");
                }
            }

            // The attempt lock and state checks must precede this normal SELECT.
            if (attemptRepository.existsByUserIdAndSubmissionTokenAndIdNot(
                    command.userId(), command.submissionToken(), command.attemptId())) {
                return SubmissionTxOutcome.conflict("Conflict: Token already used by another attempt");
            }

            // Shape validate and Hash
            String payloadHash;
            try {
                payloadHash = computeAndValidateHash(command.answers(), attempt);
            } catch (IllegalArgumentException e) {
                return SubmissionTxOutcome.badRequest(e.getMessage());
            }

            if ("COMPLETED".equals(attempt.getStatus())) {
                if (command.submissionToken().equals(attempt.getSubmissionToken()) && payloadHash.equals(attempt.getSubmissionHash())) {
                    return SubmissionTxOutcome.completed(buildResponse(attempt));
                } else {
                    return SubmissionTxOutcome.conflict("Conflict: Attempt already COMPLETED with different payload or token");
                }
            }

            List<QuizAttemptAnswer> answers = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId());

            double totalEarned = 0;
            double totalPossible = 0;
            int correctCount = 0;

            for (QuizAttemptAnswer ans : answers) {
                QuizAttemptSubmitAnswerPayload sub = command.answers().stream()
                    .filter(a -> a.questionOrder() == ans.getQuestionOrder())
                    .findFirst().orElse(null);

                double possible = ans.getPointsPossible() != null ? ans.getPointsPossible() : 0;
                totalPossible += possible;
                
                double earned = 0;
                boolean isCorrect = false;

                if (sub != null) {
                    if (ans.getQuestionTypeSnapshot() == QuizQuestionType.MCQ_SINGLE || ans.getQuestionTypeSnapshot() == QuizQuestionType.TRUE_FALSE) {
                        List<QuizAttemptAnswerOption> opts = optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ans.getId());
                        List<Integer> selOrders = sub.selectedOptionOrders() == null ? List.of() : sub.selectedOptionOrders();
                        
                        if (selOrders.size() == 1) {
                            int sel = selOrders.get(0);
                            for (QuizAttemptAnswerOption opt : opts) {
                                boolean match = opt.getOptionOrder() == sel;
                                opt.setStudentSelected(match);
                                optionRepository.save(opt);
                                if (match && opt.getIsCorrectSnapshot()) {
                                    isCorrect = true;
                                    earned = possible;
                                }
                            }
                        }
                    } else if (ans.getQuestionTypeSnapshot() == QuizQuestionType.MCQ_MULTI) {
                        List<QuizAttemptAnswerOption> opts = optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ans.getId());
                        List<Integer> selOrders = sub.selectedOptionOrders() == null ? List.of() : sub.selectedOptionOrders();
                        
                        boolean exactMatch = true;
                        boolean answeredAtAll = !selOrders.isEmpty();
                        
                        for (QuizAttemptAnswerOption opt : opts) {
                            boolean selected = selOrders.contains(opt.getOptionOrder());
                            opt.setStudentSelected(selected);
                            optionRepository.save(opt);
                            
                            if (selected != opt.getIsCorrectSnapshot()) {
                                exactMatch = false;
                            }
                        }
                        
                        if (answeredAtAll && exactMatch) {
                            isCorrect = true;
                            earned = possible;
                        }
                    } else if (ans.getQuestionTypeSnapshot() == QuizQuestionType.SHORT_ANSWER) {
                        List<QuizAttemptAnswerAcceptedAnswer> accs = acceptedAnswerRepository.findByAttemptAnswerIdOrderByAcceptedOrderAsc(ans.getId());
                        String raw = sub.shortAnswerText();
                        ans.setStudentAnswer(raw);
                        
                        if (raw != null && !raw.trim().isEmpty()) {
                            String normRaw = normalizeShortAnswer(raw);
                            for (QuizAttemptAnswerAcceptedAnswer acc : accs) {
                                if (normRaw.equals(normalizeShortAnswer(acc.getAcceptedTextSnapshot()))) {
                                    isCorrect = true;
                                    earned = possible;
                                    break;
                                }
                            }
                        }
                    }
                }

                ans.setPointsEarned(earned);
                ans.setIsCorrect(isCorrect);
                ans.setGradedAt(submissionReceivedAt);
                attemptAnswerRepository.save(ans);
                
                totalEarned += earned;
                if (isCorrect) correctCount++;
            }

            if (totalPossible <= 0) {
                return SubmissionTxOutcome.conflict("Corrupt attempt: total points possible <= 0");
            }

            double score = (totalEarned / totalPossible) * 100.0;
            
            attempt.setStatus("COMPLETED");
            attempt.setScore(score);
            attempt.setTotalQuestions(answers.size());
            attempt.setCorrectAnswers(correctCount);
            attempt.setCompletedAt(submissionReceivedAt.truncatedTo(ChronoUnit.SECONDS));
            attempt.setSubmissionToken(command.submissionToken());
            attempt.setSubmissionHash(payloadHash);

            if (!attempt.getMasteryApplied()) {
                masteryAdapter.applySnapshotEvidence(attempt, answers, submissionReceivedAt, conceptRepository);
                attempt.setMasteryApplied(true);
            }
            
            attemptRepository.save(attempt);
            return SubmissionTxOutcome.completed(buildResponse(attempt));
            });
        } catch (DataIntegrityViolationException e) {
            Optional<QuizAttempt> tokenOwner = attemptRepository.findByUserIdAndSubmissionToken(
                    command.userId(), command.submissionToken());
            if (tokenOwner.isPresent() && !tokenOwner.get().getId().equals(command.attemptId())) {
                throw new IllegalStateException("CONFLICT: Token already used by another attempt", e);
            }
            throw e;
        }

        if (outcome == null) {
            throw new IllegalStateException("Transaction outcome is null");
        }

        switch (outcome.type()) {
            case EXPIRED:
                throw new IllegalStateException("EXPIRED: " + outcome.message());
            case CONFLICT:
                throw new IllegalStateException("CONFLICT: " + outcome.message());
            case BAD_REQUEST:
                throw new IllegalArgumentException(outcome.message());
            case NOT_FOUND:
                throw new NoSuchElementException(outcome.message());
            case COMPLETED:
            default:
                return outcome.response();
        }
    }

    private String computeAndValidateHash(List<QuizAttemptSubmitAnswerPayload> answers, QuizAttempt attempt) {
        if (answers == null || answers.isEmpty()) return "EMPTY";
        
        List<QuizAttemptAnswer> dbAnswers = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId());
        Set<Integer> validOrders = dbAnswers.stream().map(QuizAttemptAnswer::getQuestionOrder).collect(Collectors.toSet());
        
        Set<Integer> seenOrders = new HashSet<>();
        for (QuizAttemptSubmitAnswerPayload a : answers) {
            if (!seenOrders.add(a.questionOrder())) {
                throw new IllegalArgumentException("Duplicate questionOrder in submission: " + a.questionOrder());
            }
            if (!validOrders.contains(a.questionOrder())) {
                throw new IllegalArgumentException("Unknown questionOrder in submission: " + a.questionOrder());
            }
            
            QuizAttemptAnswer dbAns = dbAnswers.stream().filter(x -> x.getQuestionOrder() == a.questionOrder()).findFirst().get();
            
            if (a.selectedOptionOrders() != null && !a.selectedOptionOrders().isEmpty()) {
                if (dbAns.getQuestionTypeSnapshot() == QuizQuestionType.SHORT_ANSWER) {
                    throw new IllegalArgumentException("Option answer supplied to SHORT_ANSWER");
                }
                
                List<QuizAttemptAnswerOption> opts = optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(dbAns.getId());
                Set<Integer> validOpts = opts.stream().map(QuizAttemptAnswerOption::getOptionOrder).collect(Collectors.toSet());
                
                Set<Integer> seenOpts = new HashSet<>();
                for (Integer opt : a.selectedOptionOrders()) {
                    if (!seenOpts.add(opt)) {
                        throw new IllegalArgumentException("Duplicate selected optionOrder: " + opt);
                    }
                    if (!validOpts.contains(opt)) {
                        throw new IllegalArgumentException("Unknown selected optionOrder: " + opt);
                    }
                }
                
                if (dbAns.getQuestionTypeSnapshot() == QuizQuestionType.MCQ_SINGLE || dbAns.getQuestionTypeSnapshot() == QuizQuestionType.TRUE_FALSE) {
                    if (a.selectedOptionOrders().size() > 1) {
                        throw new IllegalArgumentException("MCQ_SINGLE/TRUE_FALSE cannot have > 1 selected option");
                    }
                }
            } else if (a.shortAnswerText() != null && !a.shortAnswerText().trim().isEmpty()) {
                if (dbAns.getQuestionTypeSnapshot() != QuizQuestionType.SHORT_ANSWER) {
                    throw new IllegalArgumentException("Short-answer text supplied to option question");
                }
            }
            
            if (dbAns.getQuestionTypeSnapshot() == null) {
                throw new IllegalArgumentException("Corrupt null question type");
            }
        }

        List<QuizAttemptSubmitAnswerPayload> sorted = new ArrayList<>(answers);
        sorted.sort(Comparator.comparingInt(QuizAttemptSubmitAnswerPayload::questionOrder));
        
        StringBuilder sb = new StringBuilder();
        for (QuizAttemptSubmitAnswerPayload a : sorted) {
            sb.append(a.questionOrder()).append(":");
            if (a.selectedOptionOrders() != null && !a.selectedOptionOrders().isEmpty()) {
                List<Integer> opts = new ArrayList<>(a.selectedOptionOrders());
                Collections.sort(opts);
                sb.append(opts.stream().map(String::valueOf).collect(Collectors.joining(",")));
            } else if (a.shortAnswerText() != null && !a.shortAnswerText().trim().isEmpty()) {
                sb.append(normalizeShortAnswer(a.shortAnswerText()));
            } else {
                sb.append("UNANSWERED");
            }
            sb.append("|");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String normalizeShortAnswer(String text) {
        if (text == null) return "";
        String norm = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return norm.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private QuizAttemptResultResponse buildResponse(QuizAttempt attempt) {
        List<QuizAttemptAnswer> answers = attemptAnswerRepository.findByAttemptIdOrderByQuestionOrderAsc(attempt.getId());
        List<QuizAttemptPerQuestionResult> resList = new ArrayList<>();
        
        for (QuizAttemptAnswer ans : answers) {
            resList.add(new QuizAttemptPerQuestionResult(
                ans.getQuestionOrder(),
                ans.getPointsEarned() != null ? ans.getPointsEarned() : 0.0,
                ans.getIsCorrect() != null && ans.getIsCorrect(),
                optionRepository.findByAttemptAnswerIdOrderByOptionOrderAsc(ans.getId()).stream()
                    .filter(option -> Boolean.TRUE.equals(option.getIsCorrectSnapshot()))
                    .map(QuizAttemptAnswerOption::getOptionOrder).toList(),
                ans.getExplanationSnapshot()
            ));
        }
        
        return new QuizAttemptResultResponse(
            attempt.getId(),
            attempt.getQuiz().getId(),
            attempt.getStatus(),
            attempt.getScore(),
            attempt.getTotalQuestions(),
            attempt.getCorrectAnswers(),
            attempt.getCompletedAt(),
            resList
        );
    }
}
