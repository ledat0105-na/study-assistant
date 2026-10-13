package com.example.studyassistant.service;

import com.example.studyassistant.dto.GradedQuizQuestion;
import com.example.studyassistant.dto.QuizSubmissionRequest;
import com.example.studyassistant.entity.Quiz;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizQuestion;
import com.example.studyassistant.entity.QuizResult;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.QuizAttemptRepository;
import com.example.studyassistant.repository.QuizQuestionRepository;
import com.example.studyassistant.repository.QuizRepository;
import com.example.studyassistant.repository.QuizResultRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QuizSubmissionService {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private QuizMasteryEvidenceAdapter quizMasteryEvidenceAdapter;

    public QuizResult submitQuiz(Long userId, Long quizId, QuizSubmissionRequest request) {
        // --- Lightweight pre-flight validation (no DB, no transaction) ---
        if (request.getSubmissionToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submissionToken is required");
        }
        if (request.getAnswers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answers map cannot be null");
        }
        if (!request.getSubmissionToken().matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for submissionToken");
        }

        // We need the submissionHash for race-recovery AFTER the transaction ends.
        // Capture it via an atomic reference set inside the transaction so the
        // recovery catch block can compare without re-entering the closed session.
        final String[] capturedHash = {null};

        try {
            return transactionTemplate.execute(status -> {
                // ---- All entity loading happens within this managed session ----

                User user = userRepository.findById(userId).orElseThrow();

                Quiz quiz = quizRepository.findById(quizId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));

                // Ownership check (uses quiz.document.user or quiz.topic.document.user —
                // all navigated while entities are managed by this session)
                if (!isOwner(quiz, userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Bạn không có quyền nộp bài Quiz này");
                }

                // Load authoritative questions inside the session so Concept + Notebook
                // associations are navigable via the active persistence context
                List<QuizQuestion> authoritativeQuestions = quizQuestionRepository.findByQuizId(quizId);
                List<Long> authIds = authoritativeQuestions.stream()
                        .map(QuizQuestion::getId)
                        .collect(Collectors.toList());

                for (Long submittedId : request.getAnswers().keySet()) {
                    if (!authIds.contains(submittedId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Unknown question ID: " + submittedId);
                    }
                }

                // Canonical submission hash — computed once, inside session
                String submissionHash = computeHash(authoritativeQuestions, request.getAnswers());
                capturedHash[0] = submissionHash;

                // --- Precheck: idempotent replay / conflict ---
                Optional<QuizResult> existing = quizResultRepository
                        .findByUserIdAndQuizIdAndSubmissionToken(userId, quizId, request.getSubmissionToken());
                if (existing.isPresent()) {
                    if (existing.get().getSubmissionHash().equals(submissionHash)) {
                        return existing.get();           // identical replay — return immediately
                    } else {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Token reused with different payload");
                    }
                }

                // --- NEW submission path ---

                // Capture submission timestamp once before any mastery dispatch
                LocalDateTime submissionOccurredAt = LocalDateTime.now();

                // Grade questions exactly once — entities are fully managed here
                List<GradedQuizQuestion> gradedQuestions = authoritativeQuestions.stream().map(q -> {
                    String subAnswerObj = request.getAnswers().get(q.getId());
                    boolean answered = subAnswerObj != null;
                    boolean correct = false;
                    if (answered) {
                        String subAnswerStr = subAnswerObj.trim().toUpperCase();
                        correct = subAnswerStr.equals(q.getCorrectAnswer().trim().toUpperCase());
                    }
                    return new GradedQuizQuestion(q, answered, correct);
                }).collect(Collectors.toList());

                int correctAnswers = (int) gradedQuestions.stream()
                        .filter(GradedQuizQuestion::correct).count();
                double score = authoritativeQuestions.isEmpty() ? 0.0
                        : Math.round((((double) correctAnswers / authoritativeQuestions.size()) * 100.0) * 100.0) / 100.0;

                // --- UNIQUE RACE BARRIER: saveAndFlush before mastery ---
                QuizResult result = new QuizResult();
                result.setUser(user);
                result.setQuiz(quiz);
                result.setCorrectAnswers(correctAnswers);
                result.setTotalQuestions(authoritativeQuestions.size());
                result.setScore(score);
                result.setSubmissionToken(request.getSubmissionToken());
                result.setSubmissionHash(submissionHash);
                QuizResult savedResult = quizResultRepository.saveAndFlush(result);

                QuizAttempt attempt = new QuizAttempt();
                attempt.setUser(user);
                attempt.setQuiz(quiz);
                attempt.setTotalQuestions(authoritativeQuestions.size());
                attempt.setCorrectAnswers(correctAnswers);
                attempt.setScore(score);
                attempt.setStatus("COMPLETED");
                attempt.setCompletedAt(LocalDateTime.now());
                quizAttemptRepository.saveAndFlush(attempt);

                // Adapter runs inside the same session — Concept.notebook is navigable
                quizMasteryEvidenceAdapter.applyEvidence(
                        userId, quiz, request.getSubmissionToken(), submissionOccurredAt, gradedQuestions);

                return savedResult;
            });

        } catch (DataIntegrityViolationException e) {
            // Transaction has already ended (rolled back). Perform fresh lookup.
            String hash = capturedHash[0];
            if (hash != null) {
                Optional<QuizResult> raceResult = quizResultRepository
                        .findByUserIdAndQuizIdAndSubmissionToken(userId, quizId, request.getSubmissionToken());
                if (raceResult.isPresent()) {
                    if (raceResult.get().getSubmissionHash().equals(hash)) {
                        return raceResult.get();
                    } else {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Token reused with different payload");
                    }
                }
            }
            throw e;
        }
    }

    String computeHash(List<QuizQuestion> authoritativeQuestions, Map<Long, String> answers) {
        StringBuilder sb = new StringBuilder();
        authoritativeQuestions.stream()
                .sorted(Comparator.comparing(QuizQuestion::getId))
                .forEach(q -> {
                    String answer = answers.get(q.getId());
                    String normalized = (answer == null) ? "<MISSING>" : answer.trim().toUpperCase();
                    sb.append("Q:").append(q.getId()).append(":").append(normalized).append(";");
                });

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private boolean isOwner(Quiz quiz, Long userId) {
        if (quiz.getDocument() != null) {
            return quiz.getDocument().getUser() != null
                    && quiz.getDocument().getUser().getId().equals(userId);
        }
        if (quiz.getTopic() != null && quiz.getTopic().getDocument() != null) {
            return quiz.getTopic().getDocument().getUser().getId().equals(userId);
        }
        return false;
    }
}
