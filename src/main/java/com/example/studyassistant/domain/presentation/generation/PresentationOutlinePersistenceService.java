package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationOutlinePersistenceService {

    private final UserRepository userRepository;
    private final PresentationDeckRepository deckRepository;
    private final PresentationOutlineRevisionRepository outlineRevisionRepository;
    private final PresentationGenerationJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void persistRevisionAndCompleteJob(
            Long jobId,
            Long deckId,
            Long ownerId,
            String validatedOutlineJson,
            String contentHash,
            String scopeSnapshot) {

        transactionTemplate.execute(status -> {
            PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, ownerId)
                    .orElseThrow(() -> new IllegalStateException("Deck not found or owner mismatch"));

            User owner = deck.getOwner();

            if (PresentationDeckStatus.ARCHIVED.equals(deck.getStatus())) {
                throw new IllegalStateException("Deck was archived during generation");
            }

            int nextRevision = outlineRevisionRepository.findFirstByDeckIdOrderByRevisionNumberDesc(deckId)
                    .map(r -> r.getRevisionNumber() + 1)
                    .orElse(1);

            PresentationOutlineRevision revision = new PresentationOutlineRevision();
            revision.setDeckId(deckId);
            revision.setRevisionNumber(nextRevision);
            revision.setStatus(PresentationOutlineStatus.DRAFT);
            revision.setGenerationScopeSnapshot(scopeSnapshot);
            revision.setOutlineJson(validatedOutlineJson);
            revision.setContentHash(contentHash);
            revision.setCreatedBy(owner);
            revision.setCreatedAt(LocalDateTime.now(clock));
            outlineRevisionRepository.save(revision);

            if (!PresentationDeckStatus.OUTLINE_READY.equals(deck.getStatus())
                    && !PresentationDeckStatus.ARCHIVED.equals(deck.getStatus())) {
                deck.setStatus(PresentationDeckStatus.OUTLINE_READY);
                deck.setUpdatedAt(LocalDateTime.now(clock));
                deckRepository.save(deck);
            }

            PresentationGenerationJob job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
            job.setStatus(PresentationJobStatus.SUCCEEDED);
            job.setProgressCurrent(3);
            job.setOutlineRevisionId(revision.getId());
            job.setCompletedAt(LocalDateTime.now(clock));
            jobRepository.save(job);

            log.info("Outline revision {} (rev #{}) persisted for deck {}, job {} SUCCEEDED",
                    revision.getId(), nextRevision, deckId, jobId);
            return null;
        });
    }
}
