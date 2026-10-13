package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.domain.presentation.dto.PresentationOutlineGenerationCommand;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.QuotaService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class PresentationOutlineGenerationMySQLIntegrationTest {

    @Autowired private PresentationOutlineGenerationService service;
    @MockitoBean
    private PresentationOutlineGenerationWorker worker;
    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @MockitoSpyBean private PresentationGenerationJobRepository jobRepository;
    @Autowired private PresentationOutlinePersistenceService persistenceService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private UserDailyUsageRepository userDailyUsageRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;

    private User testUser;
    private Notebook testNotebook;
    
    private Set<Long> deckIds = new LinkedHashSet<>();
    private Set<Long> jobIds = new LinkedHashSet<>();
    private Set<Long> outlineIds = new LinkedHashSet<>();

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("teacher_" + System.currentTimeMillis());
            testUser.setEmail(testUser.getUsername() + "@test.com");
            testUser.setPassword("password");
            testUser.setRole("TEACHER");
            testUser.setFullName("Teacher User");
            testUser.setPlan("PREMIUM");
            testUser = userRepository.save(testUser);

            testNotebook = new Notebook();
            testNotebook.setUser(testUser);
            testNotebook.setTitle("Test Notebook");
            testNotebook = notebookRepository.save(testNotebook);
            return null;
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            deckIds.forEach(deckId -> {
                jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deckId, PresentationJobType.OUTLINE)
                        .forEach(jobRepository::delete);
                deckRepository.findById(deckId).ifPresent(deck -> {
                    deck.setApprovedOutlineRevision(null);
                    deckRepository.save(deck);
                });
                deckRepository.flush();
                outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deckId)
                        .forEach(outlineRepository::delete);
            });
            if (testUser != null) {
                userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), java.time.LocalDate.now())
                        .ifPresent(userDailyUsageRepository::delete);
            }
            deckIds.forEach(deckId -> deckRepository.findById(deckId).ifPresent(deckRepository::delete));
            if (testNotebook != null) notebookRepository.findById(testNotebook.getId()).ifPresent(notebookRepository::delete);
            if (testUser != null) userRepository.findById(testUser.getId()).ifPresent(userRepository::delete);
            return null;
        });
    }

    private <T> T track(T entity, Set<Long> set, java.util.function.Function<T, Long> idExtractor) {
        set.add(idExtractor.apply(entity));
        return entity;
    }

    private PresentationDeck createDeck() {
        PresentationDeck deck = new PresentationDeck();
        deck.setOwner(testUser);
        deck.setNotebook(testNotebook);
        deck.setTitle("Deck");
        deck.setCreatedAt(LocalDateTime.now(clock));
        deck.setUpdatedAt(LocalDateTime.now(clock));
        deck = deckRepository.saveAndFlush(deck);
        return track(deck, deckIds, PresentationDeck::getId);
    }

    private PresentationOutlineRevision createOutline(PresentationDeck deck, int revNumber) {
        PresentationOutlineRevision rev = new PresentationOutlineRevision();
        rev.setDeckId(deck.getId());
        rev.setRevisionNumber(revNumber);
        rev.setStatus(PresentationOutlineStatus.DRAFT);
        rev.setGenerationScopeSnapshot("{}");
        rev.setOutlineJson("{}");
        rev.setContentHash("hash" + revNumber);
        rev.setCreatedBy(deck.getOwner());
        rev.setCreatedAt(LocalDateTime.now(clock));
        rev = outlineRepository.saveAndFlush(rev);
        return track(rev, outlineIds, PresentationOutlineRevision::getId);
    }

    // A. concurrent same key + same hash
    @Test
    void testA_concurrentSameKeySameHash() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck());
        PresentationOutlineGenerationCommand cmd = new PresentationOutlineGenerationCommand(
                "idemp_concurrent_A", "Topic A", null, 10, null, null, null);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                // We reload the user in each thread to avoid hibernate detached entity issues
                User currentUser = userRepository.findById(testUser.getId()).orElseThrow();
                var dto = service.submitOutlineGeneration(currentUser, deck.getId(), cmd);
                track(jobRepository.findById(dto.jobId()).orElseThrow(), jobIds, PresentationGenerationJob::getId);
                return dto.jobId();
            }));
        }

        latch.countDown(); // start all
        
        Long firstJobId = futures.get(0).get();
        Long secondJobId = futures.get(1).get();

        // Both should resolve to the exact same job ID
        assertThat(firstJobId).isEqualTo(secondJobId);

        // Verify exactly one job in DB
        List<PresentationGenerationJob> jobs = jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.OUTLINE);
        assertThat(jobs).hasSize(1);

        // Verify exactly one quota increment
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), java.time.LocalDate.now())
                .orElseThrow();
        assertThat(usage.getPresentationOutlineCount()).isEqualTo(1);

        executor.shutdown();
    }

    // B. same key + different hash -> conflict
    @Test
    void testB_sameKeyDifferentHash() {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck());
        PresentationOutlineGenerationCommand cmd1 = new PresentationOutlineGenerationCommand(
                "idemp_seq_B", "Topic A", null, 10, null, null, null);
        PresentationOutlineGenerationCommand cmd2 = new PresentationOutlineGenerationCommand(
                "idemp_seq_B", "Topic B", null, 10, null, null, null); // Diff topic -> diff hash

        var dto = service.submitOutlineGeneration(testUser, deck.getId(), cmd1);
        track(jobRepository.findById(dto.jobId()).orElseThrow(), jobIds, PresentationGenerationJob::getId);

        assertThatThrownBy(() -> service.submitOutlineGeneration(testUser, deck.getId(), cmd2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONFLICT");

        // Quota should still be 1 from the first request
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), java.time.LocalDate.now())
                .orElseThrow();
        assertThat(usage.getPresentationOutlineCount()).isEqualTo(1);
    }

    // C. concurrent revision persistence same deck
    @Test
    void testC_concurrentRevisionPersistence() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck());
        
        // Ensure starting jobs
        PresentationGenerationJob job1 = new PresentationGenerationJob();
        job1.setOwner(testUser);
        job1.setDeckId(deck.getId());
        job1.setJobType(PresentationJobType.OUTLINE);
        job1.setStatus(PresentationJobStatus.QUEUED);
        job1.setIdempotencyKey("rev1");
        job1.setRequestHash("hash1");
        job1.setCreatedAt(LocalDateTime.now(clock));
        job1 = jobRepository.save(job1);
        track(job1, jobIds, PresentationGenerationJob::getId);

        PresentationGenerationJob job2 = new PresentationGenerationJob();
        job2.setOwner(testUser);
        job2.setDeckId(deck.getId());
        job2.setJobType(PresentationJobType.OUTLINE);
        job2.setStatus(PresentationJobStatus.QUEUED);
        job2.setIdempotencyKey("rev2");
        job2.setRequestHash("hash2");
        job2.setCreatedAt(LocalDateTime.now(clock));
        job2 = jobRepository.save(job2);
        track(job2, jobIds, PresentationGenerationJob::getId);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        
        final Long j1 = job1.getId();
        final Long j2 = job2.getId();
        
        // Mocking the call to persistRevisionAndCompleteJob isn't trivial since it's private.
        // But we can trigger the worker explicitly (or via reflection) to just run the persistence step.
        // Here we just call the worker synchronously. Wait, generateOutlineAsync will do LLM call.
        // We'll just extract the transactional logic to test it directly.
        
        futures.add(executor.submit(() -> {
            latch.await();
            try {
                persistenceService.persistRevisionAndCompleteJob(j1, deck.getId(), testUser.getId(), "{}", "cHash1", "{}");
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        }));

        futures.add(executor.submit(() -> {
            latch.await();
            try {
                persistenceService.persistRevisionAndCompleteJob(j2, deck.getId(), testUser.getId(), "{}", "cHash2", "{}");
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        }));

        latch.countDown();
        futures.get(0).get();
        futures.get(1).get();

        executor.shutdown();
        
        // Verify two revisions with sequential numbers
        List<PresentationOutlineRevision> revisions = outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deck.getId());
        revisions.forEach(r -> track(r, outlineIds, PresentationOutlineRevision::getId));
        
        assertThat(revisions).hasSize(2);
        assertThat(revisions).extracting(PresentationOutlineRevision::getRevisionNumber)
                             .containsExactlyInAnyOrder(1, 2);
    }

    // D. approved pointer preserved
    @Test
    void testD_approvedPointerPreserved() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        PresentationGenerationJob job = new PresentationGenerationJob();
        job.setOwner(testUser);
        job.setDeckId(deck.getId());
        job.setJobType(PresentationJobType.OUTLINE);
        job.setStatus(PresentationJobStatus.QUEUED);
        job.setIdempotencyKey("idemp_new");
        job.setRequestHash("req_new");
        job.setCreatedAt(LocalDateTime.now(clock));
        job = jobRepository.save(job);
        track(job, jobIds, PresentationGenerationJob::getId);

        // Run persistence manually
        persistenceService.persistRevisionAndCompleteJob(job.getId(), deck.getId(), testUser.getId(), "{}", "cHash_new", "{}");

        Integer approvedRevisionNumber = transactionTemplate.execute(status -> {
            PresentationDeck reloaded = deckRepository.findById(deck.getId()).orElseThrow();
            assertThat(reloaded.getApprovedOutlineRevision()).isNotNull();
            return reloaded.getApprovedOutlineRevision().getRevisionNumber();
        });

        assertThat(approvedRevisionNumber).isEqualTo(1);
        
        // Job successfully added revision 2
        PresentationGenerationJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        PresentationOutlineRevision newRev = outlineRepository.findById(updatedJob.getOutlineRevisionId()).orElseThrow();
        track(newRev, outlineIds, PresentationOutlineRevision::getId);
        assertThat(newRev.getRevisionNumber()).isEqualTo(2);
        assertThat(newRev.getStatus()).isEqualTo(PresentationOutlineStatus.DRAFT);
    }

    // E. quota + job atomicity (force fail)
    @Test
    void testE_quotaAndJobAtomicity() {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck());
        PresentationOutlineGenerationCommand cmd = new PresentationOutlineGenerationCommand(
                "idemp_fail", "Topic", null, 10, null, null, null);
                
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("forced"))
            .when(jobRepository).save(org.mockito.ArgumentMatchers.any(PresentationGenerationJob.class));

        try {
            service.submitOutlineGeneration(testUser, deck.getId(), cmd);
        } catch (Exception e) {
            // Expected to fail with DB constraint violation
        }

        // Verify quota is ZERO
        userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), java.time.LocalDate.now())
                .ifPresent(usage -> assertThat(usage.getPresentationOutlineCount()).isEqualTo(0));
                
        List<PresentationGenerationJob> jobs = jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.OUTLINE);
        assertThat(jobs).isEmpty();
    }

    // F. real MySQL unique idempotency fence
    @Test
    void testF_idempotencyUniqueFence() {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck());
        
        PresentationGenerationJob job1 = new PresentationGenerationJob();
        job1.setOwner(testUser);
        job1.setDeckId(deck.getId());
        job1.setJobType(PresentationJobType.OUTLINE);
        job1.setStatus(PresentationJobStatus.QUEUED);
        job1.setIdempotencyKey("fence");
        job1.setRequestHash("h1");
        job1.setCreatedAt(LocalDateTime.now(clock));
        job1 = jobRepository.save(job1);
        track(job1, jobIds, PresentationGenerationJob::getId);
        
        PresentationGenerationJob job2 = new PresentationGenerationJob();
        job2.setOwner(testUser);
        job2.setDeckId(deck.getId());
        job2.setJobType(PresentationJobType.OUTLINE);
        job2.setStatus(PresentationJobStatus.QUEUED);
        job2.setIdempotencyKey("fence"); // Same key
        job2.setRequestHash("h2");
        job2.setCreatedAt(LocalDateTime.now(clock));
        
        assertThatThrownBy(() -> jobRepository.saveAndFlush(job2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
