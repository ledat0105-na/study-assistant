package com.example.studyassistant.service;

import com.example.studyassistant.dto.GradedQuizQuestion;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.QuizAttemptAnswerConceptRepository;
import com.example.studyassistant.entity.Quiz;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class QuizMasteryEvidenceAdapter {

    private final MasteryUpdateEngine masteryUpdateEngine;

    public QuizMasteryEvidenceAdapter(MasteryUpdateEngine masteryUpdateEngine) {
        this.masteryUpdateEngine = masteryUpdateEngine;
    }

    public void applyEvidence(Long userId, Quiz quiz, String submissionToken, LocalDateTime submissionOccurredAt, List<GradedQuizQuestion> gradedQuestions) {
        Long authoritativeNotebookId = resolveAuthoritativeNotebookId(quiz);

        gradedQuestions.stream()
                .filter(gq -> gq.answered() && gq.question().getConcept() != null)
                .sorted(Comparator.comparing((GradedQuizQuestion gq) -> gq.question().getConcept().getId())
                        .thenComparing(gq -> gq.question().getId()))
                .forEach(gq -> {
                    Long conceptNotebookId = gq.question().getConcept().getNotebook().getId();
                    if (!conceptNotebookId.equals(authoritativeNotebookId)) {
                        throw new IllegalStateException("Attribution consistency violation: Concept notebook does not match Quiz notebook");
                    }

                    String idempotencyKey = String.format("QUIZ:%d:SUB:%s:Q:%d", quiz.getId(), submissionToken, gq.question().getId());
                    if (idempotencyKey.length() > 128) {
                        throw new IllegalStateException("Idempotency key exceeds maximum length of 128 characters");
                    }

                    double normalizedScore = gq.correct() ? 1.0 : 0.0;

                    MasteryEvidenceCommand command = new MasteryEvidenceCommand(
                            userId,
                            authoritativeNotebookId,
                            gq.question().getConcept().getId(),
                            "QUIZ_QUESTION_ANSWERED",
                            normalizedScore,
                            "QUIZ_QUESTION",
                            String.valueOf(gq.question().getId()),
                            idempotencyKey,
                            submissionOccurredAt
                    );

                    masteryUpdateEngine.applyEvidence(command);
                });
    }

    
    public void applySnapshotEvidence(QuizAttempt attempt, List<QuizAttemptAnswer> answers, LocalDateTime occurredAt, QuizAttemptAnswerConceptRepository conceptRepo) {
        // Document-only practice quizzes may have no notebook/concept mastery scope.
        // Resolve attribution only when there is actual concept evidence to apply.
        Long notebookId = null;
        
        for (QuizAttemptAnswer ans : answers) {
            // Unanswered questions still give evidence, but they get 0.0 (incorrect)
            // But wait, user prompt says: "unanswered = incorrect evidence."
            // We just need to check if it's answered, well "unanswered = incorrect evidence" means we DO apply mastery for unanswered questions as incorrect.
            double masteryScore = (ans.getIsCorrect() != null && ans.getIsCorrect()) ? 1.0 : 0.0;
            
            List<QuizAttemptAnswerConcept> concepts = conceptRepo.findByAttemptAnswerId(ans.getId());
            for (QuizAttemptAnswerConcept qc : concepts) {
                if (qc.getConcept() == null) continue;
                if (notebookId == null) notebookId = resolveAuthoritativeNotebookId(attempt.getQuiz());
                Notebook conceptNotebook = qc.getConcept().getNotebook();
                if (conceptNotebook == null || conceptNotebook.getId() == null || !notebookId.equals(conceptNotebook.getId())) {
                    throw new IllegalStateException("Attribution consistency violation: Snapshot concept notebook does not match Quiz notebook");
                }
                
                String idempotencyKey = String.format("QUIZ:%d:SUB:%s:Q:%d", attempt.getQuiz().getId(), attempt.getSubmissionToken(), ans.getQuestion().getId());
                
                MasteryEvidenceCommand command = new MasteryEvidenceCommand(
                        attempt.getUser().getId(),
                        notebookId,
                        qc.getConcept().getId(),
                        "QUIZ_QUESTION_ANSWERED",
                        masteryScore,
                        "QUIZ_QUESTION",
                        String.valueOf(ans.getQuestion().getId()),
                        idempotencyKey,
                        occurredAt
                );

                masteryUpdateEngine.applyEvidence(command);
            }
        }
    }
    Long resolveAuthoritativeNotebookId(Quiz quiz) {
        Notebook quizNotebook = quiz.getNotebook();
        Notebook docNotebook = quiz.getDocument() != null ? quiz.getDocument().getNotebook() : null;
        Notebook topicNotebook = (quiz.getTopic() != null && quiz.getTopic().getDocument() != null) 
                ? quiz.getTopic().getDocument().getNotebook() : null;

        Long quizNotebookId = requireNotebookId(quizNotebook, quiz);
        Long docNotebookId = requireNotebookId(docNotebook, quiz);
        Long topicNotebookId = requireNotebookId(topicNotebook, quiz);

        if ((quizNotebookId != null && docNotebookId != null && !quizNotebookId.equals(docNotebookId))
                || (quizNotebookId != null && topicNotebookId != null && !quizNotebookId.equals(topicNotebookId))
                || (docNotebookId != null && topicNotebookId != null && !docNotebookId.equals(topicNotebookId))) {
            throw new IllegalArgumentException("Conflicting notebook context for Quiz " + quiz.getId());
        }

        if (quizNotebookId != null) return quizNotebookId;
        if (docNotebookId != null) return docNotebookId;
        if (topicNotebookId != null) return topicNotebookId;

        throw new IllegalArgumentException("Cannot resolve authoritative notebook for Quiz " + quiz.getId());
    }

    private Long requireNotebookId(Notebook notebook, Quiz quiz) {
        if (notebook == null) return null;
        if (notebook.getId() == null) {
            throw new IllegalArgumentException("Cannot resolve authoritative notebook for Quiz " + quiz.getId());
        }
        return notebook.getId();
    }
}
