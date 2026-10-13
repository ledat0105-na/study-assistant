package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.StudentConceptMastery;
import com.example.studyassistant.entity.StudentLearningEvent;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.ConceptSourceRepository;
import com.example.studyassistant.repository.StudentConceptMasteryRepository;
import com.example.studyassistant.repository.StudentLearningEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasteryUpdateEngine {

    private final ConceptRepository conceptRepository;
    private final ConceptSourceRepository conceptSourceRepository;
    private final StudentLearningEventRepository eventRepository;
    private final StudentConceptMasteryRepository masteryRepository;
    private final MasteryHeuristicCalculator calculator;

    public MasteryUpdateEngine(ConceptRepository conceptRepository,
                               ConceptSourceRepository conceptSourceRepository,
                               StudentLearningEventRepository eventRepository,
                               StudentConceptMasteryRepository masteryRepository,
                               MasteryHeuristicCalculator calculator) {
        this.conceptRepository = conceptRepository;
        this.conceptSourceRepository = conceptSourceRepository;
        this.eventRepository = eventRepository;
        this.masteryRepository = masteryRepository;
        this.calculator = calculator;
    }

    @Transactional
    public MasteryUpdateResult applyEvidence(MasteryEvidenceCommand command) {
        // 1. acquire Concept PESSIMISTIC_WRITE
        Concept concept = conceptRepository.findByIdWithPessimisticWrite(command.conceptId())
                .orElseThrow(() -> new MasteryConceptNotFoundException("Concept not found or inaccessible"));

        // 2. ownership/notebook validation
        if (!concept.getNotebook().getId().equals(command.notebookId()) ||
            !concept.getNotebook().getUser().getId().equals(command.userId())) {
            throw new MasteryConceptNotFoundException("Concept not found or inaccessible");
        }

        // 3. active grounding validation
        boolean hasActiveGrounding = conceptSourceRepository.existsActiveGrounding(
                command.conceptId(), command.notebookId(), command.userId());
        if (!hasActiveGrounding) {
            throw new MasteryConceptNotFoundException("Concept not found or inaccessible"); // Masked exception
        }

        // 4. idempotency lookup
        boolean eventExists = eventRepository.findByUserIdAndIdempotencyKeyAndConceptId(
                command.userId(), command.idempotencyKey(), command.conceptId()
        ).isPresent();

        // 5. load snapshot — PESSIMISTIC_WRITE (current read) closes the concurrent cold-start
        // INSERT race: under MySQL REPEATABLE_READ a normal consistent read would retain the
        // pre-transaction snapshot and see "no row" even after a concurrent transaction has
        // already committed the first mastery row.  SELECT ... FOR UPDATE always reads the latest
        // committed data, so the second concurrent transaction correctly takes the UPDATE path.
        StudentConceptMastery mastery = masteryRepository.findByUserIdAndConceptIdWithPessimisticWrite(
                command.userId(), command.conceptId())
                .orElse(null);

        if (eventExists) {
            if (mastery == null) {
                // Defensive fallback: DB constraint hit but no mastery exists?
                return new MasteryUpdateResult(false, command.conceptId(), null, 0, null);
            }
            return new MasteryUpdateResult(
                    false,
                    command.conceptId(),
                    mastery.getMasteryEstimate(),
                    mastery.getEvidenceCount(),
                    mastery.getLastEvidenceAt()
            );
        }

        // 6. calculate new mastery
        Double oldMastery = mastery != null ? mastery.getMasteryEstimate() : null;
        double newScore = calculator.calculateNewMastery(oldMastery, command.normalizedScore());

        // 7. persist event
        StudentLearningEvent event = new StudentLearningEvent(
                concept.getNotebook().getUser(),
                concept,
                command.idempotencyKey(),
                command.eventType(),
                command.normalizedScore(),
                command.sourceType(),
                command.sourceId(),
                command.occurredAt()
        );
        eventRepository.save(event);

        // 8. persist snapshot
        if (mastery == null) {
            mastery = new StudentConceptMastery(
                    concept.getNotebook().getUser(),
                    concept,
                    newScore,
                    1
            );
            mastery.setLastEvidenceAt(command.occurredAt());
            masteryRepository.save(mastery);
        } else {
            mastery.setMasteryEstimate(newScore);
            mastery.setEvidenceCount(mastery.getEvidenceCount() + 1);
            mastery.setLastEvidenceAt(command.occurredAt());
            // Implicitly saved by dirty checking, but we can call save() explicitly
            masteryRepository.save(mastery);
        }

        // 9. return
        return new MasteryUpdateResult(
                true,
                command.conceptId(),
                mastery.getMasteryEstimate(),
                mastery.getEvidenceCount(),
                mastery.getLastEvidenceAt()
        );
    }
}
