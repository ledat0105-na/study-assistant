package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizMode;
import com.example.studyassistant.entity.StudentConceptMastery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ExamReadinessCalculator {
    static final String VERSION = "READINESS_V1";
    private static final double[] EXAM_WEIGHTS = {0.50, 0.30, 0.20};

    ExamReadinessResponse calculate(
            Long userId,
            Long notebookId,
            List<QuizAttempt> examAttempts,
            List<Concept> eligibleConcepts,
            List<StudentConceptMastery> masteryRows,
            Map<Long, EvidenceCounts> evidenceByConcept) {
        List<QuizAttempt> eligibleAttempts = examAttempts.stream()
                .filter(attempt -> isEligibleExam(attempt, userId, notebookId))
                .sorted(Comparator.comparing(QuizAttempt::getCompletedAt).reversed()
                        .thenComparing(QuizAttempt::getId, Comparator.reverseOrder()))
                .limit(3)
                .toList();

        Map<Long, StudentConceptMastery> masteryByConcept = new HashMap<>();
        for (StudentConceptMastery mastery : masteryRows) {
            masteryByConcept.put(mastery.getConcept().getId(), mastery);
        }

        double masteryTotal = 0.0;
        int coveredCount = 0;
        List<WeakConceptDto> weakConcepts = new ArrayList<>();
        for (Concept concept : eligibleConcepts) {
            StudentConceptMastery mastery = masteryByConcept.get(concept.getId());
            double masteryLevel = boundedMastery(mastery);
            masteryTotal += masteryLevel;
            if (mastery != null && mastery.getEvidenceCount() != null && mastery.getEvidenceCount() > 0) {
                coveredCount++;
            }

            EvidenceCounts evidence = evidenceByConcept.get(concept.getId());
            if (evidence != null && evidence.evidenceCount() >= 3) {
                double accuracy = (double) evidence.correctCount() / evidence.evidenceCount();
                if (accuracy < 0.60) {
                    weakConcepts.add(new WeakConceptDto(
                            concept.getId(), concept.getCanonicalName(), accuracy,
                            evidence.evidenceCount(), masteryLevel));
                }
            }
        }
        weakConcepts.sort(Comparator.comparingDouble(WeakConceptDto::accuracy)
                .thenComparingDouble(WeakConceptDto::masteryLevel)
                .thenComparing(WeakConceptDto::conceptId));

        int attemptCount = eligibleAttempts.size();
        int conceptCount = eligibleConcepts.size();
        double examPerformance = weightedExamPerformance(eligibleAttempts);
        double masteryComponent = conceptCount == 0 ? 0.0 : masteryTotal / conceptCount;
        double coverageComponent = conceptCount == 0 ? 0.0 : (double) coveredCount / conceptCount;
        boolean insufficientEvidence = attemptCount == 0 || conceptCount == 0;
        Integer score = insufficientEvidence ? null : (int) Math.max(0, Math.min(100,
                Math.round(100.0 * (0.50 * examPerformance
                        + 0.35 * masteryComponent
                        + 0.15 * coverageComponent))));

        return new ExamReadinessResponse(notebookId, VERSION, insufficientEvidence, score,
                examPerformance, masteryComponent, coverageComponent, attemptCount,
                conceptCount, coveredCount, weakConcepts.size(), List.copyOf(weakConcepts));
    }

    private boolean isEligibleExam(QuizAttempt attempt, Long userId, Long notebookId) {
        return attempt.getUser() != null && Objects.equals(attempt.getUser().getId(), userId)
                && attempt.getQuiz() != null && attempt.getQuiz().getNotebook() != null
                && Objects.equals(attempt.getQuiz().getNotebook().getId(), notebookId)
                && "COMPLETED".equals(attempt.getStatus())
                && attempt.getModeSnapshot() == QuizMode.EXAM
                && "QUIZ_SCORING_V1".equals(attempt.getScoringVersion())
                && attempt.getScore() != null && attempt.getScore() >= 0.0 && attempt.getScore() <= 100.0
                && attempt.getCompletedAt() != null;
    }

    private double weightedExamPerformance(List<QuizAttempt> attempts) {
        if (attempts.isEmpty()) return 0.0;
        double weightedScore = 0.0;
        double availableWeight = 0.0;
        for (int index = 0; index < attempts.size(); index++) {
            double weight = EXAM_WEIGHTS[index];
            weightedScore += weight * (attempts.get(index).getScore() / 100.0);
            availableWeight += weight;
        }
        return weightedScore / availableWeight;
    }

    private double boundedMastery(StudentConceptMastery mastery) {
        if (mastery == null || mastery.getMasteryEstimate() == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, mastery.getMasteryEstimate()));
    }

    record EvidenceCounts(long evidenceCount, long correctCount) {}
}