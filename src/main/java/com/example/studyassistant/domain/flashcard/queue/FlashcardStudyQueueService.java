package com.example.studyassistant.domain.flashcard.queue;

import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.entity.FlashcardReviewState;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FlashcardStudyQueueService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardStudyQueueService.class);

    private final FlashcardStudyQueueRepository queueRepository;
    private final AdaptiveRecommendationService adaptiveService;
    private final FlashcardStudyQueueProperties properties;
    private final Clock clock;

    public FlashcardStudyQueueService(
            FlashcardStudyQueueRepository queueRepository,
            AdaptiveRecommendationService adaptiveService,
            FlashcardStudyQueueProperties properties,
            Clock clock) {
        this.queueRepository = queueRepository;
        this.adaptiveService = adaptiveService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FlashcardStudyQueueResult getQueue(FlashcardStudyQueueQuery query) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        int dueLimit = normalizeLimit(query.dueLimit(), properties.getDefaultDueLimit(), properties.getMaxDueLimit());
        int newLimit = normalizeLimit(query.newLimit(), properties.getDefaultNewLimit(), properties.getMaxNewLimit());

        List<FlashcardQueueItem> dueCards = new ArrayList<>();
        if (dueLimit > 0) {
            List<FlashcardReviewState> dueStates = queueRepository.findDueStates(query.userId(), query.notebookId(), now, dueLimit);
            for (FlashcardReviewState s : dueStates) {
                Flashcard f = s.getFlashcard();
                dueCards.add(new FlashcardQueueItem(
                        f.getId(),
                        f.getQuestion(),
                        f.getAnswer(),
                        f.getConcept() != null ? f.getConcept().getId() : null,
                        FlashcardQueueType.DUE,
                        s.getDueAt(),
                        s.getState()
                ));
            }
        }

        List<FlashcardQueueItem> newCards = new ArrayList<>();
        if (newLimit > 0) {
            Set<Long> collectedIds = new HashSet<>();
            List<AdaptiveRecommendationDto> adaptiveRecommendations = new ArrayList<>();
            try {
                // Call adaptive service exactly once
                adaptiveRecommendations = adaptiveService.getRecommendations(query.userId(), query.notebookId(), 50); // limit internal adaptive fetch
            } catch (Exception e) {
                log.error("AdaptiveRecommendationService failed during queue retrieval", e);
                throw e; // We do not silently pretend it is empty on genuine exception.
            }

            if (adaptiveRecommendations != null) {
                for (AdaptiveRecommendationDto rec : adaptiveRecommendations) {
                    if (newCards.size() >= newLimit) break;
                    Long conceptId = rec.getConceptId();
                    if (conceptId == null) continue;

                    int limitForConcept = newLimit - newCards.size();
                    List<Flashcard> conceptCards = queueRepository.findNewCardsByConcept(query.userId(), query.notebookId(), conceptId, limitForConcept);
                    
                    for (Flashcard f : conceptCards) {
                        if (collectedIds.add(f.getId())) {
                            newCards.add(mapToNewItem(f));
                        }
                    }
                }
            }

            if (newCards.size() < newLimit) {
                int fallbackLimit = newLimit; // Query a bit more to ensure we get enough unique ones
                List<Flashcard> fallbackCards = queueRepository.findNewCardsFallback(query.userId(), query.notebookId(), fallbackLimit);
                for (Flashcard f : fallbackCards) {
                    if (newCards.size() >= newLimit) break;
                    if (collectedIds.add(f.getId())) {
                        newCards.add(mapToNewItem(f));
                    }
                }
            }
        }

        return new FlashcardStudyQueueResult(now, dueCards, newCards);
    }

    private int normalizeLimit(Integer requested, int defaultValue, int maxValue) {
        if (requested == null || requested <= 0) {
            return defaultValue;
        }
        if (requested > maxValue) {
            return maxValue;
        }
        return requested;
    }

    private FlashcardQueueItem mapToNewItem(Flashcard f) {
        return new FlashcardQueueItem(
                f.getId(),
                f.getQuestion(),
                f.getAnswer(),
                f.getConcept() != null ? f.getConcept().getId() : null,
                FlashcardQueueType.NEW,
                null,
                null
        );
    }
}
