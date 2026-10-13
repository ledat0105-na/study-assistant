package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.entity.UserDailyUsage;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.service.RetrievalService;
import com.example.studyassistant.llm.LlmProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class PresentationSlideGenerationMySQLIntegrationTest {

    @Autowired private PresentationSlideGenerationService service;
    @Autowired private PresentationSlideGenerationProcessor processor;
    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @Autowired private PresentationGenerationJobRepository jobRepository;
    @Autowired private PresentationJobFinalizerService finalizerService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private UserDailyUsageRepository userDailyUsageRepository;
    @Autowired private PresentationSlideRepository slideRepository;
    @Autowired private PresentationSlideElementRepository elementRepository;
    @Autowired private PresentationSlideSourceRepository sourceRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;
    @Autowired private com.example.studyassistant.service.QuotaService quotaService;

    @MockitoBean private PresentationSlideGenerationWorker worker;
    @MockitoBean private SlideGenerationLifecycleHook lifecycleHook;
    @MockitoBean private SlideGenerationSubmissionHook submissionHook;
    @MockitoBean private RetrievalService retrievalService;
    @MockitoBean private LlmProvider llmProvider;

    private User testUser;
    private Notebook testNotebook;
    private final Set<Long> deckIds = new LinkedHashSet<>();
    private final Set<Long> outlineIds = new LinkedHashSet<>();
    private final Set<Long> documentIds = new LinkedHashSet<>();
    private final Set<Long> chunkIds = new LinkedHashSet<>();
    private final Set<LocalDate> usageDates = new LinkedHashSet<>();

    @BeforeEach
    void setup() {
        usageDates.add(LocalDate.now(clock));
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("teacher_slide_" + System.currentTimeMillis());
            testUser.setEmail(testUser.getUsername() + "@test.com");
            testUser.setPassword("password");
            testUser.setRole("TEACHER");
            testUser.setFullName("Teacher Slide User");
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
                slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId).forEach(slide -> {
                    sourceRepository.deleteBySlideId(slide.getId());
                    elementRepository.deleteBySlideId(slide.getId());
                });
                sourceRepository.flush();
                elementRepository.flush();
                jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deckId, PresentationJobType.SLIDES)
                        .forEach(jobRepository::delete);
                jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deckId, PresentationJobType.SLIDE_RETRY)
                        .forEach(jobRepository::delete);
                jobRepository.flush();
                slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId).forEach(slideRepository::delete);
                slideRepository.flush();
                deckRepository.findById(deckId).ifPresent(deck -> {
                    deck.setApprovedOutlineRevision(null);
                    deckRepository.save(deck);
                });
                deckRepository.flush();
            });
            outlineIds.forEach(outlineId -> outlineRepository.findById(outlineId).ifPresent(outlineRepository::delete));
            outlineRepository.flush();
            chunkIds.forEach(chunkId -> documentChunkRepository.findById(chunkId).ifPresent(documentChunkRepository::delete));
            documentChunkRepository.flush();
            documentIds.forEach(documentId -> documentRepository.findById(documentId).ifPresent(documentRepository::delete));
            documentRepository.flush();
            if (testUser != null) {
                usageDates.forEach(date -> userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), date)
                        .ifPresent(userDailyUsageRepository::delete));
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
        deck.setTitle("Deck for Slides");
        deck.setCreatedAt(LocalDateTime.now(clock));
        deck.setUpdatedAt(LocalDateTime.now(clock));
        deck = deckRepository.saveAndFlush(deck);
        return track(deck, deckIds, PresentationDeck::getId);
    }

    private PresentationOutlineRevision createOutline(PresentationDeck deck, int revNumber) {
        PresentationOutlineRevision rev = new PresentationOutlineRevision();
        rev.setDeckId(deck.getId());
        rev.setRevisionNumber(revNumber);
        rev.setStatus(PresentationOutlineStatus.APPROVED);
        rev.setGenerationScopeSnapshot("{\"notebookId\":" + testNotebook.getId() + ",\"requestedDocumentIds\":[]}");
        rev.setOutlineJson("{\"schemaVersion\":1,\"title\":\"Test\",\"learningObjective\":\"Obj\",\"audience\":\"All\",\"language\":\"EN\",\"sections\":[{\"title\":\"S1\",\"purpose\":\"Section purpose\",\"slides\":[{\"title\":\"Slide 1\",\"purpose\":\"Teach P1\",\"slideType\":\"CONTENT\",\"keyPoints\":[\"P1\"],\"sourceRefs\":[]}]}]}");
        rev.setContentHash("hash" + revNumber);
        rev.setCreatedBy(deck.getOwner());
        rev.setCreatedAt(LocalDateTime.now(clock));
        rev = outlineRepository.saveAndFlush(rev);
        return track(rev, outlineIds, PresentationOutlineRevision::getId);
    }

    private PresentationGenerationJob createQueuedSlideJob(PresentationDeck deck, PresentationOutlineRevision revision, String idempotencyKey, int slideCount) {
        return transactionTemplate.execute(status -> {
            userRepository.findByIdForUpdate(testUser.getId()).orElseThrow();
            LocalDate reservationDate = LocalDate.now(clock);
            usageDates.add(reservationDate);
            quotaService.checkAndReserveSlideQuota(testUser.getId(), reservationDate, testUser.getPlan(), slideCount);
            LocalDateTime now = LocalDateTime.now(clock);
            PresentationGenerationJob job = new PresentationGenerationJob();
            job.setOwner(testUser);
            job.setDeckId(deck.getId());
            job.setJobType(PresentationJobType.SLIDES);
            job.setStatus(PresentationJobStatus.QUEUED);
            job.setOutlineRevisionId(revision.getId());
            job.setIdempotencyKey(idempotencyKey);
            job.setRequestHash(SlideContentHasher.fullJobRequestHash(deck.getId(), revision.getId(), revision.getContentHash()));
            job.setProgressCurrent(0);
            job.setProgressTotal(slideCount);
            job.setCreatedAt(now);
            job = jobRepository.saveAndFlush(job);

            for (int i = 1; i <= slideCount; i++) {
                PresentationSlide slide = new PresentationSlide();
                slide.setDeckId(deck.getId());
                slide.setSlideOrder(i);
                slide.setTitle("Slide " + i);
                slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
                slide.setGenerationState(PresentationSlideGenerationState.EMPTY);
                slide.setGenerationKey("r" + revision.getId() + "-s" + i);
                slide.setGeneratedFromOutlineRevisionId(revision.getId());
                slide.setCreatedAt(now);
                slide.setUpdatedAt(now);
                slideRepository.saveAndFlush(slide);
            }
            return job;
        });
    }

    private Document createReadyDocument(String fileName, String indexVersion) {
        Document document = new Document();
        document.setUser(testUser);
        document.setNotebook(testNotebook);
        document.setFileName(fileName);
        document.setFilePath("fixture/" + fileName);
        document.setFileType("PDF");
        document.setFileSize(100L);
        document.setStatus("READY");
        document.setActiveIndexVersion(indexVersion);
        document = documentRepository.saveAndFlush(document);
        documentIds.add(document.getId());
        return document;
    }

    private DocumentChunk createReadyChunk(Document document, String content, int pageStart, int pageEnd, String sectionTitle, String indexVersion) {
        return createReadyChunk(document, content, pageStart, pageEnd, sectionTitle, indexVersion, 0);
    }

    private DocumentChunk createReadyChunk(Document document, String content, int pageStart, int pageEnd, String sectionTitle, String indexVersion, int chunkIndex) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setPageStart(pageStart);
        chunk.setPageEnd(pageEnd);
        chunk.setSectionTitle(sectionTitle);
        chunk.setTokenCount(7);
        chunk.setIndexVersion(indexVersion);
        chunk.setCreatedAt(LocalDateTime.now(clock));
        chunk = documentChunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());
        return chunk;
    }

    private PresentationSlideSource createSource(PresentationSlide slide, Document document, DocumentChunk chunk, int sourceOrder) {
        PresentationSlideSource source = new PresentationSlideSource();
        source.setSlide(slide);
        source.setDocument(document);
        source.setDocumentChunk(chunk);
        source.setSourceOrder(sourceOrder);
        source.setEvidenceExcerpt(chunk.getContent());
        source.setEvidenceHash(SlideContentHasher.evidenceHash(chunk.getContent()));
        source.setSourceAlias("S" + sourceOrder);
        source.setCreatedAt(LocalDateTime.now(clock));
        return source;
    }

    @Test
    void testA_deterministicPlaceholders() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        PresentationGenerationJob job = service.submitFullGeneration(testUser, deck.getId(), new SlideGenerationCommand("idemp_A"));
        List<PresentationSlide> slides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());

        assertThat(job.getId()).isNotNull();
        assertThat(slides).hasSize(1);
        assertThat(slides).extracting(PresentationSlide::getSlideOrder).containsExactly(1);
        assertThat(slides.get(0).getGenerationKey()).isEqualTo("r" + deck.getApprovedOutlineRevision().getId() + "-s1");
        assertThat(slides.get(0).getGeneratedFromOutlineRevisionId()).isEqualTo(deck.getApprovedOutlineRevision().getId());
        assertThat(slides.get(0).getTitle()).isEqualTo("Slide 1");
        assertThat(slides.get(0).getLayoutType()).isEqualTo(PresentationSlideLayout.TITLE_CONTENT);
        assertThat(slides.get(0).getGenerationState()).isEqualTo(PresentationSlideGenerationState.EMPTY);
        assertThat(job.getStatus()).isEqualTo(PresentationJobStatus.QUEUED);
        assertThat(job.getProgressTotal()).isEqualTo(1);
        assertThat(job.getProgressCurrent()).isZero();

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock))
                .orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(1);
        verify(worker, times(1)).generateSlidesAsync(anyLong(), eq(deck.getId()), eq(testUser.getId()), anyLong(), anyList(), anyLong(), anyString(), anyList());
    }

    @Test
    void testB_concurrentExactIdempotentReplay() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PresentationGenerationJob> first = executor.submit(() -> {
                latch.await();
                return service.submitFullGeneration(testUser, deck.getId(), new SlideGenerationCommand("idemp_B"));
            });
            Future<PresentationGenerationJob> second = executor.submit(() -> {
                latch.await();
                return service.submitFullGeneration(testUser, deck.getId(), new SlideGenerationCommand("idemp_B"));
            });

            latch.countDown();
            assertThat(first.get().getId()).isEqualTo(second.get().getId());
        } finally {
            executor.shutdown();
        }

        List<PresentationGenerationJob> jobs = jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.SLIDES);
        assertThat(jobs).hasSize(1);
        assertThat(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId())).hasSize(1);

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock))
                .orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(1);
        verify(worker, times(1)).generateSlidesAsync(anyLong(), eq(deck.getId()), eq(testUser.getId()), anyLong(), anyList(), eq(deck.getApprovedOutlineRevision().getId()), eq("hash1"), anyList());
    }

    @Test
    void testC_sameKeyDifferentHash() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        service.submitFullGeneration(testUser, deck.getId(), new SlideGenerationCommand("idemp_C"));
        transactionTemplate.execute(status -> {
            PresentationDeck d = deckRepository.findById(deck.getId()).orElseThrow();
            d.getApprovedOutlineRevision().setContentHash("hash2");
            outlineRepository.save(d.getApprovedOutlineRevision());
            return null;
        });

        assertThrows(ResponseStatusException.class, () -> service.submitFullGeneration(testUser, deck.getId(), new SlideGenerationCommand("idemp_C")));
        assertThat(jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.SLIDES)).hasSize(1);
        assertThat(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId())).hasSize(1);

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock))
                .orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(1);
    }

    @Test
    void testD_authorizedCitations() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        Document document = new Document();
        document.setUser(testUser);
        document.setNotebook(testNotebook);
        document.setFileName("citation-fixture.pdf");
        document.setFilePath("fixture/citation-fixture.pdf");
        document.setFileType("PDF");
        document.setFileSize(100L);
        document.setStatus("READY");
        document.setActiveIndexVersion("index-v1");
        document = documentRepository.saveAndFlush(document);
        documentIds.add(document.getId());

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent("Evidence from the controlled source.");
        chunk.setPageStart(4);
        chunk.setPageEnd(5);
        chunk.setSectionTitle("Evidence section");
        chunk.setTokenCount(7);
        chunk.setIndexVersion("index-v1");
        chunk.setCreatedAt(LocalDateTime.now(clock));
        chunk = documentChunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());

        PresentationGenerationJob job = createQueuedSlideJob(deck, deck.getApprovedOutlineRevision(), "citation_job", 1);
        PresentationSlide slide = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0);

        when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(4)
                                .pageEnd(5)
                                .sectionTitle("Evidence section")
                                .tokenCount(7)
                                .indexVersion("index-v1")
                                .build()))
                        .hasSufficientEvidence(true)
                        .totalTokens(7)
                        .build());
        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
            .thenReturn("{\"title\":\"Generated citation slide\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"hi\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Hello\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":200,\"height\":40}],\"sourceRefs\":[\"S1\"]}");

        processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                deck.getApprovedOutlineRevision().getId(), deck.getApprovedOutlineRevision().getContentHash(),
                List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1"))));

        PresentationSlide persisted = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(persisted.getGenerationState()).isEqualTo(PresentationSlideGenerationState.GENERATED);

        List<PresentationSlideSource> sources = sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId());
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getDocumentChunk().getId()).isEqualTo(chunk.getId());
        assertThat(sources.get(0).getDocument().getId()).isEqualTo(document.getId());
        assertThat(sources.get(0).getSourceAlias()).isEqualTo("S1");
        assertThat(sources.get(0).getSourceOrder()).isEqualTo(1);
        assertThat(sources.get(0).getPageStart()).isEqualTo(4);
        assertThat(sources.get(0).getPageEnd()).isEqualTo(5);
        assertThat(sources.get(0).getSectionTitle()).isEqualTo("Evidence section");
        assertThat(sources.get(0).getEvidenceExcerpt()).isEqualTo(chunk.getContent());
        assertThat(sources.get(0).getEvidenceHash()).isEqualTo(SlideContentHasher.evidenceHash(chunk.getContent()));
    }

    @Test
    void testE_invalidSourceRefsFailClosed() {
        List<String> invalidRefs = List.of("\"\"", "\"S9\"", "3");
        for (int index = 0; index < invalidRefs.size(); index++) {
            PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck created = createDeck();
            PresentationOutlineRevision revision = createOutline(created, 1);
            created.setApprovedOutlineRevision(revision);
            return deckRepository.saveAndFlush(created);
            });
            PresentationOutlineRevision revision = deck.getApprovedOutlineRevision();
            Document document = createReadyDocument("bad-source-" + index + ".pdf", "index-v1");
            DocumentChunk chunk = createReadyChunk(document, "Evidence from source.", 2, 3, "Body", "index-v1");
            PresentationGenerationJob job = createQueuedSlideJob(deck, revision, "idemp_E_" + index, 1);
            PresentationSlide slide = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0);

            when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                    .chunks(List.of(RetrievedChunk.builder()
                        .chunkId(chunk.getId())
                        .documentId(document.getId())
                        .documentTitle(document.getFileName())
                        .chunkIndex(0)
                        .content(chunk.getContent())
                        .pageStart(2)
                        .pageEnd(3)
                        .sectionTitle("Body")
                        .tokenCount(7)
                        .indexVersion("index-v1")
                        .build()))
                    .hasSufficientEvidence(true)
                    .totalTokens(7)
                    .build());
            when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"title\":\"Broken\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"bad\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Hello\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[" + invalidRefs.get(index) + "]}");

            processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                revision.getId(), revision.getContentHash(), List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1"))));

            assertThat(slideRepository.findById(slide.getId()).orElseThrow().getGenerationState()).isEqualTo(PresentationSlideGenerationState.FAILED);
            assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(PresentationJobStatus.FAILED);
            assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId())).isEmpty();
            assertThat(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId())).isEmpty();
        }
    }

    @Test
    void testF_staleApprovedRevision() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        PresentationOutlineRevision revision = deck.getApprovedOutlineRevision();
        Document document = createReadyDocument("stale.pdf", "index-v1");
        DocumentChunk chunk = createReadyChunk(document, "minted evidence", 2, 4, "Body", "index-v1");
        PresentationGenerationJob job = createQueuedSlideJob(deck, revision, "idemp_F", 1);
        PresentationSlide slide = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0);

        when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(2)
                                .pageEnd(4)
                                .sectionTitle("Body")
                                .tokenCount(7)
                                .indexVersion("index-v1")
                                .build()))
                        .hasSufficientEvidence(true)
                        .totalTokens(7)
                        .build());
        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"title\":\"Fresh\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"n\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Fresh text\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[\"S1\"]}");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(lifecycleHook).beforeSlidePersistence(anyLong(), anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                revision.getId(), revision.getContentHash(), List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1")))));

        assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        transactionTemplate.execute(status -> {
            PresentationOutlineRevision stale = outlineRepository.findById(revision.getId()).orElseThrow();
            stale.setContentHash("hash-stale");
            outlineRepository.save(stale);
            return null;
        });
        release.countDown();
        future.get();
        executor.shutdown();

        PresentationGenerationJob savedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(savedJob.getStatus()).isEqualTo(PresentationJobStatus.FAILED);
        assertThat(savedJob.getErrorCode()).isEqualTo("STALE_OUTLINE");
        assertThat(slideRepository.findById(slide.getId()).orElseThrow().getGenerationState()).isEqualTo(PresentationSlideGenerationState.FAILED);
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId())).isEmpty();

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock)).orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isZero();
    }

    @Test
    void testG_duplicatePlannedSlideConcurrency() throws Exception {
        record SubmitResult(PresentationGenerationJob job, ResponseStatusException conflict) {}

        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<SubmitResult> results;
        try {
            Future<SubmitResult> first = executor.submit(() -> {
                latch.await();
                try {
                    return new SubmitResult(service.submitFullGeneration(testUser, deck.getId(),
                            new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_G_1")), null);
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() != 409) throw e;
                    return new SubmitResult(null, e);
                }
            });
            Future<SubmitResult> second = executor.submit(() -> {
                latch.await();
                try {
                    return new SubmitResult(service.submitFullGeneration(testUser, deck.getId(),
                            new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_G_2")), null);
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() != 409) throw e;
                    return new SubmitResult(null, e);
                }
            });

            latch.countDown();
            results = List.of(first.get(), second.get());
        } finally {
            executor.shutdown();
        }

        List<PresentationGenerationJob> successes = results.stream()
                .map(SubmitResult::job)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<ResponseStatusException> conflicts = results.stream()
                .map(SubmitResult::conflict)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertThat(successes).hasSize(1);
        assertThat(conflicts).hasSize(1);
        assertThat(successes.get(0).getId()).isNotNull();
        assertThat(conflicts.get(0).getStatusCode().value()).isEqualTo(409);

        assertThat(jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.SLIDES)).hasSize(1);
        List<PresentationSlide> slides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());
        assertThat(slides).hasSize(1);
        assertThat(slides).extracting(PresentationSlide::getSlideOrder).doesNotHaveDuplicates();
        assertThat(slides).extracting(PresentationSlide::getGenerationKey).doesNotHaveDuplicates();
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock)).orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(1);
    }

    @Test
    void testH_editedPreservation() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        PresentationGenerationJob firstJob = service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_H"));
        PresentationSlide slide = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0);
        transactionTemplate.executeWithoutResult(status -> {
            PresentationSlide editedSlide = slideRepository.findById(slide.getId()).orElseThrow();
            editedSlide.setGenerationState(PresentationSlideGenerationState.EDITED);
            editedSlide.setTitle("Edited title");
            editedSlide.setSpeakerNotes("Edited notes");
            slideRepository.saveAndFlush(editedSlide);

            PresentationSlideElement editedElement = new PresentationSlideElement();
            editedElement.setDeckId(deck.getId());
            editedElement.setSlideId(slide.getId());
            editedElement.setElementType(PresentationElementType.TEXT);
            editedElement.setZIndex(0);
            editedElement.setX(new java.math.BigDecimal("10"));
            editedElement.setY(new java.math.BigDecimal("20"));
            editedElement.setWidth(new java.math.BigDecimal("200"));
            editedElement.setHeight(new java.math.BigDecimal("40"));
            editedElement.setPayloadJson("{\"text\":\"User-edited element\"}");
            editedElement.setStyleJson("{}");
            editedElement.setCreatedAt(LocalDateTime.now(clock));
            editedElement.setUpdatedAt(LocalDateTime.now(clock));
            elementRepository.saveAndFlush(editedElement);

            PresentationGenerationJob terminalFirstJob = jobRepository.findById(firstJob.getId()).orElseThrow();
            terminalFirstJob.setStatus(PresentationJobStatus.SUCCEEDED);
            terminalFirstJob.setStartedAt(LocalDateTime.now(clock));
            terminalFirstJob.setProgressCurrent(terminalFirstJob.getProgressTotal());
            terminalFirstJob.setCompletedAt(LocalDateTime.now(clock));
            jobRepository.saveAndFlush(terminalFirstJob);
        });
        assertThat(jobRepository.findById(firstJob.getId()).orElseThrow().getStatus()).isEqualTo(PresentationJobStatus.SUCCEEDED);

        PresentationGenerationJob secondJob = service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_H_2"));
        PresentationSlide reloaded = slideRepository.findById(slide.getId()).orElseThrow();
        PresentationGenerationJob reloadedFirstJob = jobRepository.findById(firstJob.getId()).orElseThrow();
        assertThat(reloadedFirstJob.getStatus()).isEqualTo(PresentationJobStatus.SUCCEEDED);
        assertThat(reloadedFirstJob.getProgressCurrent()).isEqualTo(reloadedFirstJob.getProgressTotal());
        assertThat(reloadedFirstJob.getProgressTotal()).isEqualTo(1);
        assertThat(reloaded.getGenerationState()).isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(reloaded.getTitle()).isEqualTo("Edited title");
        assertThat(reloaded.getSpeakerNotes()).isEqualTo("Edited notes");
        assertThat(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId()))
                .singleElement()
                .satisfies(element -> {
                    assertThat(element.getPayloadJson()).contains("\"User-edited element\"");
                    assertThat(element.getZIndex()).isZero();
                });
        assertThat(secondJob.getStatus()).isEqualTo(PresentationJobStatus.SUCCEEDED);
        assertThat(secondJob.getProgressTotal()).isZero();
        assertThat(secondJob.getProgressCurrent()).isZero();
        assertThat(secondJob.getCompletedAt()).isNotNull();
        assertThat(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId())).hasSize(1);

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock)).orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(1);
        verify(worker, times(1)).generateSlidesAsync(anyLong(), eq(deck.getId()), eq(testUser.getId()), anyLong(), anyList(), anyLong(), anyString(), anyList());

        ResponseStatusException retryRejected = assertThrows(ResponseStatusException.class, () -> service.submitSlideRetry(testUser, deck.getId(), slide.getId(),
                new com.example.studyassistant.domain.presentation.dto.SlideRetryCommand("retry_H", deck.getApprovedOutlineRevision().getId(), deck.getApprovedOutlineRevision().getContentHash())));
        assertThat(retryRejected.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void testI_failFastPartialFailure() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        Document document = createReadyDocument("failfast.pdf", "index-v1");
        DocumentChunk chunk = createReadyChunk(document, "Evidence for fail-fast.", 4, 6, "Summary", "index-v1");
        PresentationGenerationJob job = createQueuedSlideJob(deck, deck.getApprovedOutlineRevision(), "idemp_I", 3);

        when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(4)
                                .pageEnd(6)
                                .sectionTitle("Summary")
                                .tokenCount(7)
                                .indexVersion("index-v1")
                                .build()))
                        .hasSufficientEvidence(true)
                        .totalTokens(7)
                        .build());

        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"title\":\"Slide 1\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"ok\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Hello\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[\"S1\"]}")
                .thenReturn("{\"title\":\"Slide 2\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"bad\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Bad\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[\"S99\"]}");

        processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                deck.getApprovedOutlineRevision().getId(), deck.getApprovedOutlineRevision().getContentHash(),
                List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1")),
                        new PlannedSlide(2, "Slide 2", "TITLE_CONTENT", List.of("P2")),
                        new PlannedSlide(3, "Slide 3", "TITLE_CONTENT", List.of("P3"))));

        List<PresentationSlide> slides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());
        assertThat(slides.get(0).getGenerationState()).isEqualTo(PresentationSlideGenerationState.GENERATED);
        assertThat(slides.get(1).getGenerationState()).isEqualTo(PresentationSlideGenerationState.FAILED);
        assertThat(slides.get(2).getGenerationState()).isEqualTo(PresentationSlideGenerationState.EMPTY);
        verify(llmProvider, times(2)).generate(anyString(), anyString(), anyDouble());
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(PresentationJobStatus.FAILED);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getProgressCurrent()).isEqualTo(1);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getProgressTotal()).isEqualTo(3);
    }

    @Test
    void testJ_tx1ReservationRollback() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        LocalDate reservationDate = LocalDate.now(clock);
        usageDates.add(reservationDate);
        transactionTemplate.executeWithoutResult(status -> {
            UserDailyUsage usage = new UserDailyUsage();
            usage.setUser(testUser);
            usage.setUsageDate(reservationDate);
            usage.setPresentationSlideCount(4);
            usage.setPresentationOutlineCount(0);
            usage.setChatCount(0);
            usage.setQuizCount(0);
            usage.setFlashcardCount(0);
            userDailyUsageRepository.saveAndFlush(usage);
        });
        doThrow(new IllegalStateException("reserve-then-bomb"))
                .when(submissionHook).afterQuotaReservation(eq(testUser.getId()), eq(reservationDate), eq(1));

        assertThatThrownBy(() -> service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_J")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserve-then-bomb");
        assertThat(jobRepository.findByOwnerIdAndDeckIdAndJobType(testUser.getId(), deck.getId(), PresentationJobType.SLIDES)).isEmpty();
        assertThat(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId())).isEmpty();
        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), reservationDate).orElseThrow().getPresentationSlideCount()).isEqualTo(4);
    }

    @Test
    void testK_sameUserDifferentDeckQuotaConcurrency() throws Exception {
        PresentationDeck firstDeck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });
        PresentationDeck secondDeck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<PresentationGenerationJob> first = executor.submit(() -> {
            latch.await();
            return service.submitFullGeneration(testUser, firstDeck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_K_1"));
        });
        Future<PresentationGenerationJob> second = executor.submit(() -> {
            latch.await();
            return service.submitFullGeneration(testUser, secondDeck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_K_2"));
        });

        latch.countDown();
        first.get();
        second.get();
        executor.shutdown();

        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(clock)).orElseThrow();
        assertThat(usage.getPresentationSlideCount()).isEqualTo(2);
    }

    @Test
    void testL_staleInactiveCitationAfterRetrieval() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        Document document = createReadyDocument("inactive.pdf", "index-v1");
        DocumentChunk chunk = createReadyChunk(document, "citation evidence", 1, 2, "Body", "index-v1");
        PresentationGenerationJob job = createQueuedSlideJob(deck, deck.getApprovedOutlineRevision(), "idemp_L", 1);

        when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(1)
                                .pageEnd(2)
                                .sectionTitle("Body")
                                .tokenCount(7)
                                .indexVersion("index-v1")
                                .build()))
                        .hasSufficientEvidence(true)
                        .totalTokens(7)
                        .build());
        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"title\":\"Stale\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"x\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Hello\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[\"S1\"]}");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(lifecycleHook).beforeSlidePersistence(anyLong(), anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                deck.getApprovedOutlineRevision().getId(), deck.getApprovedOutlineRevision().getContentHash(), List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1")))));

        assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        transactionTemplate.execute(status -> {
            Document staleDocument = documentRepository.findById(document.getId()).orElseThrow();
            staleDocument.setStatus("ARCHIVED");
            documentRepository.save(staleDocument);
            return null;
        });
        release.countDown();
        future.get();
        executor.shutdown();

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(PresentationJobStatus.FAILED);
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(job.getTargetSlideId() != null ? job.getTargetSlideId() : slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0).getId())).isEmpty();
    }

    @Test
    void testM_archivedTx2Fence() throws Exception {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            return deckRepository.saveAndFlush(d);
        });

        Document document = createReadyDocument("archived.pdf", "index-v1");
        DocumentChunk chunk = createReadyChunk(document, "archive evidence", 1, 2, "Body", "index-v1");
        PresentationGenerationJob job = createQueuedSlideJob(deck, deck.getApprovedOutlineRevision(), "idemp_M", 1);

        when(retrievalService.search(eq(testUser.getId()), eq(testNotebook.getId()), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(1)
                                .pageEnd(2)
                                .sectionTitle("Body")
                                .tokenCount(7)
                                .indexVersion("index-v1")
                                .build()))
                        .hasSufficientEvidence(true)
                        .totalTokens(7)
                        .build());
        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("{\"title\":\"Archived\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"x\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Hello\"},\"style\":{\"fontSizePt\":24,\"color\":\"#000000\"},\"x\":10,\"y\":20,\"width\":120,\"height\":30}],\"sourceRefs\":[\"S1\"]}");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(lifecycleHook).beforeSlidePersistence(anyLong(), anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> processor.processJob(job.getId(), deck.getId(), testUser.getId(), testNotebook.getId(), List.of(document.getId()),
                deck.getApprovedOutlineRevision().getId(), deck.getApprovedOutlineRevision().getContentHash(), List.of(new PlannedSlide(1, "Slide 1", "TITLE_CONTENT", List.of("P1")))));

        assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        transactionTemplate.execute(status -> {
            PresentationDeck archived = deckRepository.findById(deck.getId()).orElseThrow();
            archived.setStatus(PresentationDeckStatus.ARCHIVED);
            deckRepository.save(archived);
            return null;
        });
        release.countDown();
        future.get();
        executor.shutdown();

        PresentationGenerationJob savedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(savedJob.getStatus()).isEqualTo(PresentationJobStatus.FAILED);
        assertThat(savedJob.getErrorCode()).isEqualTo("DECK_ARCHIVED");
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId()).get(0).getId())).isEmpty();
    }

    @Test
    void testN_realDbUniqueConstraints() {
        record ConstraintFixture(Long deckId, Long revisionId, Long slideId, Long jobId,
                                 Long documentId, Long firstChunkId, Long secondChunkId) {}

        ConstraintFixture fixture = transactionTemplate.execute(status -> {
            PresentationDeck deck = createDeck();
            PresentationOutlineRevision revision = createOutline(deck, 1);
            deck.setApprovedOutlineRevision(revision);
            deckRepository.saveAndFlush(deck);

            PresentationSlide slide = new PresentationSlide();
            slide.setDeckId(deck.getId());
            slide.setSlideOrder(1);
            slide.setTitle("Slide 1");
            slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            slide.setGenerationState(PresentationSlideGenerationState.EMPTY);
            slide.setGenerationKey("r" + revision.getId() + "-s1");
            slide.setGeneratedFromOutlineRevisionId(revision.getId());
            slide.setCreatedAt(LocalDateTime.now(clock));
            slide.setUpdatedAt(LocalDateTime.now(clock));
            slide = slideRepository.saveAndFlush(slide);

            Document document = createReadyDocument("constraints.pdf", "index-v1");
            DocumentChunk firstChunk = createReadyChunk(document, "first constraint evidence", 1, 1, "Section", "index-v1", 0);
            DocumentChunk secondChunk = createReadyChunk(document, "second constraint evidence", 2, 2, "Section 2", "index-v1", 1);

            PresentationGenerationJob job = new PresentationGenerationJob();
            job.setOwner(testUser);
            job.setDeckId(deck.getId());
            job.setJobType(PresentationJobType.SLIDES);
            job.setStatus(PresentationJobStatus.FAILED);
            job.setOutlineRevisionId(revision.getId());
            job.setIdempotencyKey("constraint-job");
            job.setRequestHash(SlideContentHasher.fullJobRequestHash(deck.getId(), revision.getId(), revision.getContentHash()));
            job.setProgressCurrent(0);
            job.setProgressTotal(1);
            job.setCreatedAt(LocalDateTime.now(clock));
            job = jobRepository.saveAndFlush(job);
            sourceRepository.saveAndFlush(createSource(slide, document, firstChunk, 1));

            return new ConstraintFixture(deck.getId(), revision.getId(), slide.getId(), job.getId(),
                    document.getId(), firstChunk.getId(), secondChunk.getId());
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationDeck deck = deckRepository.findById(fixture.deckId()).orElseThrow();
            PresentationOutlineRevision revision = outlineRepository.findById(fixture.revisionId()).orElseThrow();
            PresentationSlide duplicateOrder = new PresentationSlide();
            duplicateOrder.setDeckId(deck.getId());
            duplicateOrder.setSlideOrder(1);
            duplicateOrder.setTitle("Duplicate order");
            duplicateOrder.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            duplicateOrder.setGenerationState(PresentationSlideGenerationState.EMPTY);
            duplicateOrder.setGenerationKey("different-key");
            duplicateOrder.setGeneratedFromOutlineRevisionId(revision.getId());
            duplicateOrder.setCreatedAt(LocalDateTime.now(clock));
            duplicateOrder.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(duplicateOrder);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationDeck deck = deckRepository.findById(fixture.deckId()).orElseThrow();
            PresentationSlide original = slideRepository.findById(fixture.slideId()).orElseThrow();
            PresentationSlide duplicateKey = new PresentationSlide();
            duplicateKey.setDeckId(deck.getId());
            duplicateKey.setSlideOrder(2);
            duplicateKey.setTitle("Duplicate generation key");
            duplicateKey.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            duplicateKey.setGenerationState(PresentationSlideGenerationState.EMPTY);
            duplicateKey.setGenerationKey(original.getGenerationKey());
            duplicateKey.setGeneratedFromOutlineRevisionId(original.getGeneratedFromOutlineRevisionId());
            duplicateKey.setCreatedAt(LocalDateTime.now(clock));
            duplicateKey.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(duplicateKey);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationGenerationJob original = jobRepository.findById(fixture.jobId()).orElseThrow();
            User owner = userRepository.findById(original.getOwner().getId()).orElseThrow();
            PresentationDeck deck = deckRepository.findById(fixture.deckId()).orElseThrow();
            PresentationGenerationJob duplicateKey = new PresentationGenerationJob();
            duplicateKey.setOwner(owner);
            duplicateKey.setDeckId(deck.getId());
            duplicateKey.setJobType(original.getJobType());
            duplicateKey.setStatus(PresentationJobStatus.FAILED);
            duplicateKey.setOutlineRevisionId(original.getOutlineRevisionId());
            duplicateKey.setIdempotencyKey(original.getIdempotencyKey());
            duplicateKey.setRequestHash(original.getRequestHash());
            duplicateKey.setProgressCurrent(0);
            duplicateKey.setProgressTotal(1);
            duplicateKey.setCreatedAt(LocalDateTime.now(clock));
            jobRepository.saveAndFlush(duplicateKey);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationSlide persistedSlide = slideRepository.findById(fixture.slideId()).orElseThrow();
            Document persistedDocument = documentRepository.findById(fixture.documentId()).orElseThrow();
            DocumentChunk differentChunk = documentChunkRepository.findById(fixture.secondChunkId()).orElseThrow();
            sourceRepository.saveAndFlush(createSource(persistedSlide, persistedDocument, differentChunk, 1));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationSlide persistedSlide = slideRepository.findById(fixture.slideId()).orElseThrow();
            Document persistedDocument = documentRepository.findById(fixture.documentId()).orElseThrow();
            DocumentChunk sameChunk = documentChunkRepository.findById(fixture.firstChunkId()).orElseThrow();
            sourceRepository.saveAndFlush(createSource(persistedSlide, persistedDocument, sameChunk, 2));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testO_priorRevisionGeneratedEdited409() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev1 = createOutline(d, 1);
            PresentationOutlineRevision rev2 = createOutline(d, 2);
            d.setApprovedOutlineRevision(rev2);
            deckRepository.saveAndFlush(d);

            PresentationSlide generated = new PresentationSlide();
            generated.setDeckId(d.getId());
            generated.setSlideOrder(1);
            generated.setTitle("Old generated");
            generated.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            generated.setGenerationState(PresentationSlideGenerationState.GENERATED);
            generated.setGeneratedFromOutlineRevisionId(rev1.getId());
            generated.setGenerationKey("r" + rev1.getId() + "-s1");
            generated.setCreatedAt(LocalDateTime.now(clock));
            generated.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(generated);

            PresentationSlide edited = new PresentationSlide();
            edited.setDeckId(d.getId());
            edited.setSlideOrder(2);
            edited.setTitle("Old edited");
            edited.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            edited.setGenerationState(PresentationSlideGenerationState.EDITED);
            edited.setGeneratedFromOutlineRevisionId(rev1.getId());
            edited.setGenerationKey("r" + rev1.getId() + "-s2");
            edited.setCreatedAt(LocalDateTime.now(clock));
            edited.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(edited);
            return d;
        });

        assertThrows(ResponseStatusException.class, () -> service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_O")));
    }

    @Test
    void testP_terminalStaleEmptyFailedCleanup() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev1 = createOutline(d, 1);
            PresentationOutlineRevision rev2 = createOutline(d, 2);
            d.setApprovedOutlineRevision(rev2);
            deckRepository.saveAndFlush(d);

            PresentationSlide stale = new PresentationSlide();
            stale.setDeckId(d.getId());
            stale.setSlideOrder(1);
            stale.setTitle("Old");
            stale.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            stale.setGenerationState(PresentationSlideGenerationState.EMPTY);
            stale.setGeneratedFromOutlineRevisionId(rev1.getId());
            stale.setGenerationKey("r" + rev1.getId() + "-s1");
            stale.setCreatedAt(LocalDateTime.now(clock));
            stale.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(stale);
            return d;
        });

        PresentationGenerationJob job = service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_P"));
        assertThat(job.getStatus()).isEqualTo(PresentationJobStatus.QUEUED);
        assertThat(slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId())).extracting(PresentationSlide::getGeneratedFromOutlineRevisionId).contains(deck.getApprovedOutlineRevision().getId());
    }

    @Test
    void testQ_activeOldRevisionJobNewRequest409() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev1 = createOutline(d, 1);
            PresentationOutlineRevision rev2 = createOutline(d, 2);
            d.setApprovedOutlineRevision(rev2);
            deckRepository.saveAndFlush(d);

            PresentationSlide slide = new PresentationSlide();
            slide.setDeckId(d.getId());
            slide.setSlideOrder(1);
            slide.setTitle("Old");
            slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            slide.setGenerationState(PresentationSlideGenerationState.EMPTY);
            slide.setGeneratedFromOutlineRevisionId(rev1.getId());
            slide.setGenerationKey("r" + rev1.getId() + "-s1");
            slide.setCreatedAt(LocalDateTime.now(clock));
            slide.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(slide);

            PresentationGenerationJob oldJob = new PresentationGenerationJob();
            oldJob.setOwner(testUser);
            oldJob.setDeckId(d.getId());
            oldJob.setJobType(PresentationJobType.SLIDES);
            oldJob.setStatus(PresentationJobStatus.QUEUED);
            oldJob.setOutlineRevisionId(rev1.getId());
            oldJob.setIdempotencyKey("old_q");
            oldJob.setRequestHash("hash-old");
            oldJob.setProgressCurrent(0);
            oldJob.setProgressTotal(1);
            oldJob.setCreatedAt(LocalDateTime.now(clock));
            jobRepository.saveAndFlush(oldJob);
            return d;
        });

        assertThrows(ResponseStatusException.class, () -> service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_Q")));
    }

    @Test
    void testR_midnightReleaseUsesOriginalReservationDate() {
        LocalDate reservationDate = LocalDate.now(clock).minusDays(1);
        LocalDate currentDate = LocalDate.now(clock);
        usageDates.add(reservationDate);
        PresentationGenerationJob job = transactionTemplate.execute(status -> {
            PresentationDeck deck = createDeck();
            PresentationGenerationJob reservedJob = new PresentationGenerationJob();
            reservedJob.setOwner(testUser);
            reservedJob.setDeckId(deck.getId());
            reservedJob.setJobType(PresentationJobType.SLIDES);
            reservedJob.setStatus(PresentationJobStatus.RUNNING);
            reservedJob.setIdempotencyKey("midnight_R");
            reservedJob.setRequestHash("hashR");
            reservedJob.setProgressCurrent(1);
            reservedJob.setProgressTotal(2);
            reservedJob.setCreatedAt(reservationDate.atStartOfDay());
            reservedJob = jobRepository.saveAndFlush(reservedJob);

            UserDailyUsage oldUsage = new UserDailyUsage();
            oldUsage.setUser(testUser);
            oldUsage.setUsageDate(reservationDate);
            oldUsage.setPresentationSlideCount(2);
            oldUsage.setPresentationOutlineCount(0);
            oldUsage.setChatCount(0);
            oldUsage.setQuizCount(0);
            oldUsage.setFlashcardCount(0);
            userDailyUsageRepository.saveAndFlush(oldUsage);

            UserDailyUsage todayUsage = new UserDailyUsage();
            todayUsage.setUser(testUser);
            todayUsage.setUsageDate(currentDate);
            todayUsage.setPresentationSlideCount(7);
            todayUsage.setPresentationOutlineCount(0);
            todayUsage.setChatCount(0);
            todayUsage.setQuizCount(0);
            todayUsage.setFlashcardCount(0);
            userDailyUsageRepository.saveAndFlush(todayUsage);
            return reservedJob;
        });

        PresentationDeck deck = deckRepository.findById(job.getDeckId()).orElseThrow();
        assertThat(deck.getOwner().getId()).isEqualTo(job.getOwner().getId());
        finalizerService.finalizeSlideJob(job.getId(), testUser.getId(), PresentationJobStatus.FAILED, "TEST", "midnight");

        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), reservationDate).orElseThrow().getPresentationSlideCount()).isEqualTo(1);
        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), currentDate).orElseThrow().getPresentationSlideCount()).isEqualTo(7);
    }

    @Test
    void testS_finalizationInvokedTwiceReleaseOnce() {
        LocalDate reservationDate = LocalDate.now(clock);
        PresentationGenerationJob job = transactionTemplate.execute(status -> {
            PresentationDeck deck = createDeck();
            PresentationGenerationJob reservedJob = new PresentationGenerationJob();
            reservedJob.setOwner(testUser);
            reservedJob.setDeckId(deck.getId());
            reservedJob.setJobType(PresentationJobType.SLIDES);
            reservedJob.setStatus(PresentationJobStatus.RUNNING);
            reservedJob.setIdempotencyKey("finalize_S");
            reservedJob.setRequestHash("hashS");
            reservedJob.setProgressCurrent(0);
            reservedJob.setProgressTotal(3);
            reservedJob.setCreatedAt(reservationDate.atStartOfDay());
            reservedJob = jobRepository.saveAndFlush(reservedJob);

            UserDailyUsage usage = new UserDailyUsage();
            usage.setUser(testUser);
            usage.setUsageDate(reservationDate);
            usage.setPresentationSlideCount(5);
            usage.setPresentationOutlineCount(0);
            usage.setChatCount(0);
            usage.setQuizCount(0);
            usage.setFlashcardCount(0);
            userDailyUsageRepository.saveAndFlush(usage);
            return reservedJob;
        });
        PresentationDeck deck = deckRepository.findById(job.getDeckId()).orElseThrow();
        assertThat(deck.getOwner().getId()).isEqualTo(job.getOwner().getId());

        finalizerService.finalizeSlideJob(job.getId(), testUser.getId(), PresentationJobStatus.FAILED, "TEST", "first");
        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), reservationDate).orElseThrow().getPresentationSlideCount()).isEqualTo(2);
        finalizerService.finalizeSlideJob(job.getId(), testUser.getId(), PresentationJobStatus.FAILED, "TEST", "second");
        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), reservationDate).orElseThrow().getPresentationSlideCount()).isEqualTo(2);

        PresentationGenerationJob invalidJob = transactionTemplate.execute(status -> {
            PresentationDeck invalidDeck = createDeck();
            PresentationGenerationJob invalid = new PresentationGenerationJob();
            invalid.setOwner(testUser);
            invalid.setDeckId(invalidDeck.getId());
            invalid.setJobType(PresentationJobType.SLIDES);
            invalid.setStatus(PresentationJobStatus.RUNNING);
            invalid.setIdempotencyKey("finalize_S_invalid");
            invalid.setRequestHash("hashS-invalid");
            invalid.setProgressCurrent(0);
            invalid.setProgressTotal(3);
            invalid.setCreatedAt(reservationDate.atStartOfDay());
            return jobRepository.saveAndFlush(invalid);
        });
        assertThatThrownBy(() -> finalizerService.finalizeSlideJob(invalidJob.getId(), testUser.getId(), PresentationJobStatus.FAILED, "TEST", "over-release"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot release more than current usage");
        assertThat(userDailyUsageRepository.findByUserIdAndUsageDate(testUser.getId(), reservationDate).orElseThrow().getPresentationSlideCount()).isEqualTo(2);
    }

    @Test
    void testT_zeroWorkJob() {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck d = createDeck();
            PresentationOutlineRevision rev = createOutline(d, 1);
            d.setApprovedOutlineRevision(rev);
            deckRepository.saveAndFlush(d);

            PresentationSlide slide = new PresentationSlide();
            slide.setDeckId(d.getId());
            slide.setSlideOrder(1);
            slide.setTitle("Already generated");
            slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            slide.setGenerationState(PresentationSlideGenerationState.GENERATED);
            slide.setGeneratedFromOutlineRevisionId(rev.getId());
            slide.setGenerationKey("r" + rev.getId() + "-s1");
            slide.setCreatedAt(LocalDateTime.now(clock));
            slide.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.saveAndFlush(slide);
            return d;
        });

        PresentationGenerationJob result = service.submitFullGeneration(testUser, deck.getId(), new com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand("idemp_T"));
        assertThat(result.getStatus()).isEqualTo(PresentationJobStatus.SUCCEEDED);
        assertThat(result.getProgressTotal()).isZero();
        assertThat(result.getProgressCurrent()).isZero();
        assertThat(result.getCompletedAt()).isNotNull();
        verify(worker, never()).generateSlidesAsync(anyLong(), anyLong(), anyLong(), anyLong(), anyList(), anyLong(), anyString(), anyList());
    }
}

