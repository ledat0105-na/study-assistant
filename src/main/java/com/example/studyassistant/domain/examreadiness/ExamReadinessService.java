package com.example.studyassistant.domain.examreadiness;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.QuizAttempt;
import com.example.studyassistant.entity.QuizMode;
import com.example.studyassistant.entity.StudentConceptMastery;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.QuizAttemptAnswerConceptRepository;
import com.example.studyassistant.repository.QuizAttemptRepository;
import com.example.studyassistant.repository.StudentConceptMasteryRepository;
import com.example.studyassistant.repository.projection.RecentConceptEvidenceProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ExamReadinessService {
    private static final String COMPLETED = "COMPLETED";
    private static final String SCORING_VERSION = "QUIZ_SCORING_V1";

    private final NotebookRepository notebookRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ConceptRepository conceptRepository;
    private final StudentConceptMasteryRepository masteryRepository;
    private final QuizAttemptAnswerConceptRepository answerConceptRepository;
    private final Clock clock;
    private final ExamReadinessCalculator calculator = new ExamReadinessCalculator();

    public ExamReadinessService(
            NotebookRepository notebookRepository,
            QuizAttemptRepository attemptRepository,
            ConceptRepository conceptRepository,
            StudentConceptMasteryRepository masteryRepository,
            QuizAttemptAnswerConceptRepository answerConceptRepository,
            Clock clock) {
        this.notebookRepository = notebookRepository;
        this.attemptRepository = attemptRepository;
        this.conceptRepository = conceptRepository;
        this.masteryRepository = masteryRepository;
        this.answerConceptRepository = answerConceptRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExamReadinessResponse getNotebookReadiness(Long userId, Long notebookId) {
        if (!notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(notebookId, userId)) {
            throw new NoSuchElementException("Notebook not found");
        }

        List<QuizAttempt> attempts = attemptRepository.findEligibleReadinessExamAttempts(
                userId, notebookId, COMPLETED, QuizMode.EXAM, SCORING_VERSION, PageRequest.of(0, 3));
        List<Concept> concepts = conceptRepository.findActivelyGroundedByNotebookAndUser(notebookId, userId);

        List<Long> conceptIds = concepts.stream().map(Concept::getId).toList();
        List<StudentConceptMastery> masteryRows = conceptIds.isEmpty()
                ? List.of()
                : masteryRepository.findByUserIdAndConceptIdIn(userId, conceptIds);

        Map<Long, ExamReadinessCalculator.EvidenceCounts> evidenceByConcept = new HashMap<>();
        if (!conceptIds.isEmpty()) {
            LocalDateTime windowEnd = LocalDateTime.now(clock);
            LocalDateTime windowStart = windowEnd.minusDays(30);
            List<RecentConceptEvidenceProjection> recentEvidence = answerConceptRepository.aggregateRecentEvidence(
                    conceptIds, notebookId, userId, windowStart, windowEnd);
            for (RecentConceptEvidenceProjection row : recentEvidence) {
                evidenceByConcept.put(row.getConceptId(), new ExamReadinessCalculator.EvidenceCounts(
                        row.getEvidenceCount(), row.getCorrectCount()));
            }
        }

        return calculator.calculate(userId, notebookId, attempts, concepts, masteryRows, evidenceByConcept);
    }
}