package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.Quiz;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizMode;
import com.example.studyassistant.entity.StudentConceptMastery;
import com.example.studyassistant.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExamReadinessCalculatorTest {
    private final ExamReadinessCalculator calculator = new ExamReadinessCalculator();
    private final Long userId = 7L;
    private final Long notebookId = 9L;

    @Test
    void noEligibleAttemptsAndNoEligibleConceptsReturnInsufficientEvidence() {
        ExamReadinessResponse noAttempts = calculate(List.of(), concepts(1), List.of(), Map.of());
        assertThat(noAttempts.insufficientEvidence()).isTrue();
        assertThat(noAttempts.readinessScore()).isNull();

        ExamReadinessResponse noConcepts = calculate(List.of(exam(80, 1)), List.of(), List.of(), Map.of());
        assertThat(noConcepts.insufficientEvidence()).isTrue();
        assertThat(noConcepts.readinessScore()).isNull();
    }

    @Test
    void examPerformanceUsesSpecifiedWeightsAndRenormalizesAvailableWeights() {
        assertThat(calculate(List.of(exam(80, 1)), concepts(1), List.of(), Map.of()).examPerformance())
                .isEqualTo(0.80);
        assertThat(calculate(List.of(exam(80, 2), exam(60, 1)), concepts(1), List.of(), Map.of()).examPerformance())
            .isCloseTo(0.725, within(1e-12));
        assertThat(calculate(List.of(exam(80, 3), exam(70, 2), exam(60, 1)), concepts(1), List.of(), Map.of())
                .examPerformance()).isEqualTo(0.73);
    }

    @Test
    void onlyNewestThreeEligibleExamsAreUsedAndInvalidKindsAreIgnored() {
        List<QuizAttempt> attempts = new ArrayList<>(List.of(
                exam(10, 1), exam(20, 2), exam(30, 3), exam(90, 4),
                invalidAttempt(100, 5, QuizMode.PRACTICE, "COMPLETED", "QUIZ_SCORING_V1"),
                invalidAttempt(100, 6, QuizMode.EXAM, "EXPIRED", "QUIZ_SCORING_V1"),
                invalidAttempt(100, 7, QuizMode.EXAM, "IN_PROGRESS", "QUIZ_SCORING_V1"),
            invalidAttempt(100, 8, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V0"),
            invalidAttempt(Double.NaN, 9, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1"),
            invalidAttempt(101, 10, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1"),
            invalidAttempt(100, 11, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", false),
            invalidAttempt(100, 12, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1", true, false)));

        ExamReadinessResponse result = calculate(attempts, concepts(1), List.of(), Map.of());

        assertThat(result.eligibleExamAttemptCount()).isEqualTo(3);
        assertThat(result.examPerformance()).isEqualTo((0.50 * 0.90 + 0.30 * 0.30 + 0.20 * 0.20) / 1.0);
    }

    @Test
    void masteryAndCoverageIncludeGroundedConceptsWithoutMastery() {
        List<Concept> concepts = concepts(3);
        List<StudentConceptMastery> mastery = List.of(mastery(concepts.get(0), 0.8, 2), mastery(concepts.get(1), 0.6, 1));

        ExamReadinessResponse result = calculate(List.of(exam(80, 1)), concepts, mastery, Map.of());

        assertThat(result.masteryComponent()).isCloseTo(1.4 / 3.0, within(1e-12));
        assertThat(result.coverageComponent()).isCloseTo(2.0 / 3.0, within(1e-12));
        assertThat(result.coveredConceptCount()).isEqualTo(2);
    }

    @Test
    void numericReferenceCaseRoundsToSixtyThree() {
        List<Concept> concepts = concepts(3);
        List<StudentConceptMastery> mastery = List.of(mastery(concepts.get(0), 0.8, 2), mastery(concepts.get(1), 0.6, 1));

        ExamReadinessResponse result = calculate(
                List.of(exam(80, 3), exam(70, 2), exam(60, 1)), concepts, mastery, Map.of());

        assertThat(result.examPerformance()).isEqualTo(0.73);
        assertThat(result.masteryComponent()).isCloseTo(1.4 / 3.0, within(1e-12));
        assertThat(result.coverageComponent()).isCloseTo(2.0 / 3.0, within(1e-12));
        assertThat(result.readinessScore()).isEqualTo(63);
        assertThat(result.calculationVersion()).isEqualTo("READINESS_V1");
    }

    @Test
    void weakThresholdRequiresThreeAndAccuracyStrictlyBelowPointSix() {
        Concept concept = concepts(1).get(0);
        ExamReadinessResponse below = calculate(List.of(exam(80, 1)), List.of(concept), List.of(),
                Map.of(concept.getId(), new ExamReadinessCalculator.EvidenceCounts(5, 2)));
        assertThat(below.weakConceptCount()).isEqualTo(1);
        assertThat(below.weakConcepts().get(0).accuracy()).isEqualTo(0.4);

        ExamReadinessResponse boundary = calculate(List.of(exam(80, 1)), List.of(concept), List.of(),
                Map.of(concept.getId(), new ExamReadinessCalculator.EvidenceCounts(5, 3)));
        assertThat(boundary.weakConceptCount()).isZero();

        ExamReadinessResponse tooFew = calculate(List.of(exam(80, 1)), List.of(concept), List.of(),
                Map.of(concept.getId(), new ExamReadinessCalculator.EvidenceCounts(2, 0)));
        assertThat(tooFew.weakConceptCount()).isZero();
    }

    @Test
    void weakDefinitionCoversOneOfThreeTwoOfThreeAndJustBelowSixtyPercent() {
        List<Concept> concepts = concepts(3);
        ExamReadinessResponse result = calculate(List.of(exam(80, 1)), concepts, List.of(), Map.of(
                concepts.get(0).getId(), new ExamReadinessCalculator.EvidenceCounts(3, 1),
                concepts.get(1).getId(), new ExamReadinessCalculator.EvidenceCounts(3, 2),
                concepts.get(2).getId(), new ExamReadinessCalculator.EvidenceCounts(1000, 599)));

        assertThat(result.weakConcepts()).extracting(WeakConceptDto::conceptId)
                .containsExactly(concepts.get(0).getId(), concepts.get(2).getId());
        assertThat(result.weakConcepts().get(0).accuracy()).isEqualTo(1.0 / 3.0);
        assertThat(result.weakConcepts().get(1).accuracy()).isEqualTo(0.599);
    }

    @Test
    void eachConceptGetsFullEvidenceAndWeakSortIsDeterministic() {
        List<Concept> concepts = concepts(3);
        Map<Long, ExamReadinessCalculator.EvidenceCounts> counts = Map.of(
                concepts.get(0).getId(), new ExamReadinessCalculator.EvidenceCounts(5, 1),
                concepts.get(1).getId(), new ExamReadinessCalculator.EvidenceCounts(5, 1),
                concepts.get(2).getId(), new ExamReadinessCalculator.EvidenceCounts(3, 0));
        List<StudentConceptMastery> mastery = List.of(
                mastery(concepts.get(0), 0.2, 1), mastery(concepts.get(1), 0.1, 1));

        ExamReadinessResponse result = calculate(List.of(exam(80, 1)), concepts, mastery, counts);

        assertThat(result.weakConceptCount()).isEqualTo(3);
        assertThat(result.weakConcepts()).extracting(WeakConceptDto::conceptId)
                .containsExactly(concepts.get(2).getId(), concepts.get(1).getId(), concepts.get(0).getId());
        assertThat(result.weakConcepts().get(0).evidenceCount()).isEqualTo(3);
        assertThat(result.weakConcepts().get(0).accuracy()).isZero();
    }

    private ExamReadinessResponse calculate(List<QuizAttempt> attempts, List<Concept> concepts,
                                            List<StudentConceptMastery> mastery,
                                            Map<Long, ExamReadinessCalculator.EvidenceCounts> evidence) {
        return calculator.calculate(userId, notebookId, attempts, concepts, mastery, evidence);
    }

    private List<Concept> concepts(int count) {
        User user = new User();
        user.setId(userId);
        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        notebook.setUser(user);
        List<Concept> concepts = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            Concept concept = new Concept();
            concept.setId(id);
            concept.setNotebook(notebook);
            concept.setCanonicalName("Concept " + id);
            concept.setNormalizedName("concept " + id);
            concepts.add(concept);
        }
        return concepts;
    }

    private QuizAttempt exam(double score, int completedMinute) {
        return invalidAttempt(score, completedMinute, QuizMode.EXAM, "COMPLETED", "QUIZ_SCORING_V1");
    }

    private QuizAttempt invalidAttempt(double score, int completedMinute, QuizMode mode, String status, String scoringVersion) {
        return invalidAttempt(score, completedMinute, mode, status, scoringVersion, true, true);
    }

    private QuizAttempt invalidAttempt(double score, int completedMinute, QuizMode mode, String status,
                                       String scoringVersion, boolean completedAtPresent) {
        return invalidAttempt(score, completedMinute, mode, status, scoringVersion, completedAtPresent, true);
    }

    private QuizAttempt invalidAttempt(double score, int completedMinute, QuizMode mode, String status,
                                       String scoringVersion, boolean completedAtPresent, boolean userNotebookMatch) {
        User user = new User();
        user.setId(userNotebookMatch ? userId : userId + 1);
        Notebook notebook = new Notebook();
        notebook.setId(userNotebookMatch ? notebookId : notebookId + 1);
        Quiz quiz = new Quiz();
        quiz.setNotebook(notebook);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId((long) completedMinute);
        attempt.setUser(user);
        attempt.setQuiz(quiz);
        attempt.setStatus(status);
        attempt.setModeSnapshot(mode);
        attempt.setScoringVersion(scoringVersion);
        attempt.setScore(score);
        attempt.setCompletedAt(completedAtPresent ? LocalDateTime.of(2026, 1, 1, 0, completedMinute) : null);
        return attempt;
    }

    private StudentConceptMastery mastery(Concept concept, double level, int evidenceCount) {
        User user = new User();
        user.setId(userId);
        return new StudentConceptMastery(user, concept, level, evidenceCount);
    }
}
