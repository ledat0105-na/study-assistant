package com.example.studyassistant.domain.quiz.attempt;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAttemptStartService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizQuestionOptionRepository optionRepository;
    private final QuizQuestionAcceptedAnswerRepository acceptedAnswerRepository;
    private final QuizQuestionConceptRepository questionConceptRepository;
    
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository attemptAnswerRepository;
    private final QuizAttemptAnswerOptionRepository attemptOptionRepository;
    private final QuizAttemptAnswerAcceptedAnswerRepository attemptAcceptedAnswerRepository;
    private final QuizAttemptAnswerConceptRepository attemptConceptRepository;
    private final TransactionTemplate transactionTemplate;

    public QuizAttemptResponse startAttempt(QuizAttemptStartCommand cmd) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("startAttempt must run outside a database transaction");
        }

        return transactionTemplate.execute(status -> {
            Quiz quiz = quizRepository.findById(cmd.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

            if (quiz.getDeletedAt() != null) {
                throw new IllegalArgumentException("Quiz not found"); // mask deleted
            }

            if (quiz.getStatus() != QuizStatus.PUBLISHED) {
                throw new IllegalArgumentException("Quiz is not PUBLISHED");
            }

            // Document-scoped quizzes do not require a notebook. Resolve the owner
            // from the authoritative scope, never from a client-supplied identity.
            User owner = quiz.getDocument() != null ? quiz.getDocument().getUser()
                    : quiz.getNotebook() != null ? quiz.getNotebook().getUser() : null;
            if (owner == null || !owner.getId().equals(cmd.userId())) {
                throw new IllegalArgumentException("Quiz not found or unauthorized");
            }

            // Load questions
            List<QuizQuestion> questions = new ArrayList<>(quizQuestionRepository.findByQuizId(quiz.getId()));
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("Cannot start attempt for empty quiz");
            }
            questions.sort(Comparator.comparing(q -> q.getQuestionOrder() != null ? q.getQuestionOrder() : Integer.MAX_VALUE));
            
            // Validate questions
            Integer prevOrder = null;
            for (QuizQuestion q : questions) {
                if (q.getQuestionOrder() == null) {
                    throw new IllegalArgumentException("Question order is NULL");
                }
                if (prevOrder != null && q.getQuestionOrder().equals(prevOrder)) {
                    throw new IllegalArgumentException("Duplicate question order");
                }
                if (q.getQuestionType() == null) {
                    throw new IllegalArgumentException("Unsupported question type");
                }
                if (q.getPoints() == null || q.getPoints() <= 0) {
                    throw new IllegalArgumentException("Question points must be > 0");
                }
                if (q.getQuestion() == null || q.getQuestion().isBlank()) {
                    throw new IllegalArgumentException("Invalid normalized question structure");
                }
                prevOrder = q.getQuestionOrder();
            }

            // Create Attempt
            QuizAttempt attempt = new QuizAttempt();
            attempt.setQuiz(quiz);
            attempt.setUser(owner);
            attempt.setStatus("IN_PROGRESS");
            attempt.setStartedAt(LocalDateTime.now());
            attempt.setScoringVersion("QUIZ_SCORING_V1");
            attempt.setModeSnapshot(quiz.getMode());
            
            if (quiz.getMode() == QuizMode.EXAM) {
                if (quiz.getTimeLimitSeconds() == null) throw new IllegalArgumentException("EXAM mode requires time limit");
            }
            
            attempt.setTimeLimitSecondsSnapshot(quiz.getTimeLimitSeconds());
            attempt.setPassThresholdSnapshot(quiz.getPassThreshold());
            attempt.setTotalQuestions(questions.size());
            attempt.setMasteryApplied(false);
            
            attempt = attemptRepository.save(attempt);

            List<QuizAttemptQuestionDto> dtoList = new ArrayList<>();

            for (QuizQuestion q : questions) {
                QuizAttemptAnswer aa = new QuizAttemptAnswer();
                aa.setAttempt(attempt);
                aa.setQuestion(q);
                aa.setQuestionOrder(q.getQuestionOrder());
                aa.setQuestionTextSnapshot(q.getQuestion());
                aa.setQuestionTypeSnapshot(q.getQuestionType());
                aa.setPointsPossible(q.getPoints());
                aa.setExplanationSnapshot(q.getExplanation());
                aa = attemptAnswerRepository.save(aa);

                List<QuizAttemptOptionDto> optionDtos = new ArrayList<>();

                if (q.getQuestionType() == QuizQuestionType.MCQ_SINGLE || 
                    q.getQuestionType() == QuizQuestionType.MCQ_MULTI || 
                    q.getQuestionType() == QuizQuestionType.TRUE_FALSE) {
                    
                    List<QuizQuestionOption> opts = optionRepository.findByQuestionIdOrderByOptionOrderAsc(q.getId());
                    for (QuizQuestionOption o : opts) {
                        QuizAttemptAnswerOption ao = new QuizAttemptAnswerOption();
                        ao.setAttemptAnswer(aa);
                        ao.setOptionOrder(o.getOptionOrder());
                        ao.setOptionTextSnapshot(o.getOptionText());
                        ao.setIsCorrectSnapshot(o.getIsCorrect());
                        ao.setStudentSelected(false);
                        attemptOptionRepository.save(ao);
                        
                        optionDtos.add(new QuizAttemptOptionDto(ao.getOptionOrder(), ao.getOptionTextSnapshot()));
                    }
                }

                if (q.getQuestionType() == QuizQuestionType.SHORT_ANSWER) {
                    List<QuizQuestionAcceptedAnswer> accs = acceptedAnswerRepository.findByQuestionIdOrderByAcceptedOrderAsc(q.getId());
                    for (QuizQuestionAcceptedAnswer acc : accs) {
                        QuizAttemptAnswerAcceptedAnswer aaa = new QuizAttemptAnswerAcceptedAnswer();
                        aaa.setAttemptAnswer(aa);
                        aaa.setAcceptedOrder(acc.getAcceptedOrder());
                        aaa.setAcceptedTextSnapshot(acc.getAcceptedText());
                        attemptAcceptedAnswerRepository.save(aaa);
                    }
                }

                List<QuizQuestionConcept> concepts = questionConceptRepository.findByQuestionId(q.getId());
                for (QuizQuestionConcept qc : concepts) {
                    if (qc.getConcept() == null || qc.getConcept().getNotebook() == null ||
                        !qc.getConcept().getNotebook().getId().equals(quiz.getNotebook().getId())) {
                        throw new IllegalArgumentException("Cross-notebook concept detected");
                    }
                    
                    QuizAttemptAnswerConcept aac = new QuizAttemptAnswerConcept();
                    aac.setAttemptAnswer(aa);
                    aac.setConcept(qc.getConcept());
                    attemptConceptRepository.save(aac);
                }

                dtoList.add(new QuizAttemptQuestionDto(
                    q.getId(),
                    aa.getQuestionOrder(),
                    aa.getQuestionTextSnapshot(),
                    aa.getQuestionTypeSnapshot(),
                    aa.getPointsPossible(),
                    optionDtos
                ));
            }

            return new QuizAttemptResponse(
                attempt.getId(),
                quiz.getId(),
                attempt.getModeSnapshot(),
                attempt.getStartedAt(),
                attempt.getTimeLimitSecondsSnapshot(),
                dtoList
            );
        });
    }
}
