package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class PresentationFoundationMySQLIntegrationTest {

    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @Autowired private PresentationSlideRepository slideRepository;
    @Autowired private PresentationSlideElementRepository elementRepository;
    @Autowired private PresentationSlideSourceRepository sourceRepository;
    @Autowired private PresentationMediaAssetRepository mediaRepository;
    @Autowired private PresentationGenerationJobRepository jobRepository;
    
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;

    private User testUser;
    private User testUser2;
    private Notebook testNotebook;
    private Notebook testNotebook2;
    private Document testDoc;
    private DocumentChunk testChunk;
    
    private static final String FIXTURE_INDEX_VERSION = "t1.3-v1";

    private List<Long> jobIds = new ArrayList<>();
    private List<Long> sourceIds = new ArrayList<>();
    private List<Long> elementIds = new ArrayList<>();
    private List<Long> slideIds = new ArrayList<>();
    private List<Long> outlineIds = new ArrayList<>();
    private List<Long> mediaIds = new ArrayList<>();
    private List<Long> deckIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            testUser = new User();
            testUser.setUsername("teacher_" + System.currentTimeMillis());
            testUser.setEmail(testUser.getUsername() + "@test.com");
            testUser.setPassword("password");
            testUser.setRole("TEACHER");
            testUser.setFullName("Teacher User");
            testUser = userRepository.save(testUser);

            testUser2 = new User();
            testUser2.setUsername("teacher2_" + System.currentTimeMillis());
            testUser2.setEmail(testUser2.getUsername() + "@test.com");
            testUser2.setPassword("password");
            testUser2.setRole("TEACHER");
            testUser2.setFullName("Teacher User 2");
            testUser2 = userRepository.save(testUser2);
            
            testNotebook = new Notebook();
            testNotebook.setUser(testUser);
            testNotebook.setTitle("Test Notebook");
            testNotebook = notebookRepository.save(testNotebook);

            testNotebook2 = new Notebook();
            testNotebook2.setUser(testUser2);
            testNotebook2.setTitle("Test Notebook 2");
            testNotebook2 = notebookRepository.save(testNotebook2);
            
            testDoc = new Document();
            testDoc.setUser(testUser);
            testDoc.setNotebook(testNotebook);
            testDoc.setFileName("test.pdf");
            testDoc.setFileSize(100L);
            testDoc.setFileType("PDF");
            testDoc.setFilePath("key");
            testDoc.setStatus("READY");
            testDoc.setActiveIndexVersion(FIXTURE_INDEX_VERSION);
            testDoc = documentRepository.save(testDoc);
            
            testChunk = new DocumentChunk();
            testChunk.setDocument(testDoc);
            testChunk.setChunkIndex(1);
            testChunk.setContent("Chunk content");
            testChunk.setTokenCount(10);
            testChunk.setIndexVersion(FIXTURE_INDEX_VERSION);
            testChunk = documentChunkRepository.save(testChunk);
            
            return null;
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            // 1. fixture-owned generation jobs
            jobIds.forEach(id -> jobRepository.findById(id).ifPresent(jobRepository::delete));
            // 2. fixture-owned slide sources
            sourceIds.forEach(id -> sourceRepository.findById(id).ifPresent(sourceRepository::delete));
            // 3. fixture-owned slide elements
            elementIds.forEach(id -> elementRepository.findById(id).ifPresent(elementRepository::delete));
            // 4. fixture-owned slides
            slideIds.forEach(id -> slideRepository.findById(id).ifPresent(slideRepository::delete));
            // 5. clear approved outline pointer on fixture-owned decks
            deckIds.forEach(id -> deckRepository.findById(id).ifPresent(deck -> {
                deck.setApprovedOutlineRevision(null);
                deckRepository.save(deck);
            }));
            deckRepository.flush();
            // 6. fixture-owned outline revisions
            outlineIds.forEach(id -> outlineRepository.findById(id).ifPresent(outlineRepository::delete));
            // 7. fixture-owned media assets
            mediaIds.forEach(id -> mediaRepository.findById(id).ifPresent(mediaRepository::delete));
            // 8. fixture-owned decks
            deckIds.forEach(id -> deckRepository.findById(id).ifPresent(deckRepository::delete));
            
            // 9. fixture-owned document chunk/document/notebook/user
            if (testChunk != null) documentChunkRepository.findById(testChunk.getId()).ifPresent(documentChunkRepository::delete);
            if (testDoc != null) documentRepository.findById(testDoc.getId()).ifPresent(documentRepository::delete);
            if (testNotebook != null) notebookRepository.findById(testNotebook.getId()).ifPresent(notebookRepository::delete);
            if (testNotebook2 != null) notebookRepository.findById(testNotebook2.getId()).ifPresent(notebookRepository::delete);
            if (testUser != null) userRepository.findById(testUser.getId()).ifPresent(userRepository::delete);
            if (testUser2 != null) userRepository.findById(testUser2.getId()).ifPresent(userRepository::delete);
            return null;
        });
    }

    private <T> T track(T entity, List<Long> list, java.util.function.Function<T, Long> idExtractor) {
        list.add(idExtractor.apply(entity));
        return entity;
    }

    private PresentationDeck createDeck(User owner, Notebook notebook) {
        PresentationDeck deck = new PresentationDeck();
        deck.setOwner(owner);
        deck.setNotebook(notebook);
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

    private PresentationSlide createSlide(PresentationDeck deck, int order) {
        PresentationSlide slide = new PresentationSlide();
        slide.setDeckId(deck.getId());
        slide.setSlideOrder(order);
        slide.setTitle("Slide " + order);
        slide.setLayoutType(PresentationSlideLayout.TITLE);
        slide.setCreatedAt(LocalDateTime.now(clock));
        slide.setUpdatedAt(LocalDateTime.now(clock));
        slide = slideRepository.saveAndFlush(slide);
        return track(slide, slideIds, PresentationSlide::getId);
    }
    
    private PresentationMediaAsset createMedia(PresentationDeck deck, User owner) {
        PresentationMediaAsset media = new PresentationMediaAsset();
        media.setOwner(owner);
        media.setDeckId(deck.getId());
        media.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
        media.setOriginalFileName("test.png");
        media.setStoredFileKey("key_" + System.currentTimeMillis());
        media.setMimeType("image/png");
        media.setSizeBytes(100L);
        media.setSha256("sha");
        media.setWidth(100);
        media.setHeight(100);
        media.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
        media.setCreatedAt(LocalDateTime.now(clock));
        media = mediaRepository.saveAndFlush(media);
        return track(media, mediaIds, PresentationMediaAsset::getId);
    }

    @Test
    void testA_allTablesUsable() {
        transactionTemplate.execute(status -> {
            PresentationDeck deck = createDeck(testUser, testNotebook);
            PresentationOutlineRevision rev = createOutline(deck, 1);
            deck.setApprovedOutlineRevision(rev);
            deckRepository.saveAndFlush(deck);
            
            PresentationSlide slide = createSlide(deck, 1);
            PresentationMediaAsset media = createMedia(deck, testUser);
            
            PresentationSlideElement elem = new PresentationSlideElement();
            elem.setDeckId(deck.getId());
            elem.setSlideId(slide.getId());
            elem.setElementType(PresentationElementType.TEXT);
            elem.setZIndex(1);
            elem.setX(BigDecimal.ZERO);
            elem.setY(BigDecimal.ZERO);
            elem.setWidth(BigDecimal.TEN);
            elem.setHeight(BigDecimal.TEN);
            elem.setPayloadJson("{}");
            elem.setStyleJson("{}");
            elem.setMediaAssetId(media.getId());
            elem.setCreatedAt(LocalDateTime.now(clock));
            elem.setUpdatedAt(LocalDateTime.now(clock));
            elem = elementRepository.saveAndFlush(elem);
            track(elem, elementIds, PresentationSlideElement::getId);
            
            PresentationSlideSource src = new PresentationSlideSource();
            src.setSlide(slide);
            src.setDocumentChunk(testChunk);
            src.setDocument(testDoc);
            src.setSourceOrder(1);
            src.setEvidenceExcerpt("x");
            src.setEvidenceHash("y");
            src.setCreatedAt(LocalDateTime.now(clock));
            src = sourceRepository.saveAndFlush(src);
            track(src, sourceIds, PresentationSlideSource::getId);
            
            PresentationGenerationJob job = new PresentationGenerationJob();
            job.setOwner(testUser);
            job.setDeckId(deck.getId());
            job.setJobType(PresentationJobType.OUTLINE);
            job.setStatus(PresentationJobStatus.QUEUED);
            job.setIdempotencyKey("idemp");
            job.setRequestHash("req");
            job.setCreatedAt(LocalDateTime.now(clock));
            job = jobRepository.saveAndFlush(job);
            track(job, jobIds, PresentationGenerationJob::getId);
            
            assertThat(deckRepository.findById(deck.getId())).isPresent();
            return null;
        });
    }

    @Test
    void testB_uniqueDeckSlideOrder() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck = createDeck(testUser, testNotebook);
                createSlide(deck, 1);
                createSlide(deck, 1);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testC_uniqueDeckRevisionNumber() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck = createDeck(testUser, testNotebook);
                createOutline(deck, 1);
                createOutline(deck, 1);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testD_approvedOutlineFromAnotherDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationOutlineRevision rev2 = createOutline(deck2, 1);
                
                deck1.setApprovedOutlineRevision(rev2);
                deckRepository.saveAndFlush(deck1);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testE_slideOutlineFromAnotherDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationOutlineRevision rev2 = createOutline(deck2, 1);
                
                PresentationSlide slide1 = new PresentationSlide();
                slide1.setDeckId(deck1.getId());
                slide1.setSlideOrder(1);
                slide1.setTitle("Slide");
                slide1.setLayoutType(PresentationSlideLayout.TITLE);
                slide1.setGeneratedFromOutlineRevisionId(rev2.getId());
                
                slideRepository.saveAndFlush(slide1);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testF_elementSlideWithWrongDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationSlide slide2 = createSlide(deck2, 1);
                
                PresentationSlideElement elem = new PresentationSlideElement();
                elem.setDeckId(deck1.getId()); // wrong deck
                elem.setSlideId(slide2.getId());
                elem.setElementType(PresentationElementType.TEXT);
                elem.setZIndex(1);
                elem.setX(BigDecimal.ZERO);
                elem.setY(BigDecimal.ZERO);
                elem.setWidth(BigDecimal.TEN);
                elem.setHeight(BigDecimal.TEN);
                elem.setPayloadJson("{}");
                elem.setStyleJson("{}");
                elem.setCreatedAt(LocalDateTime.now(clock));
                elem.setUpdatedAt(LocalDateTime.now(clock));
                
                elementRepository.saveAndFlush(elem);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testG_elementMediaFromAnotherDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationSlide slide1 = createSlide(deck1, 1);
                PresentationMediaAsset media2 = createMedia(deck2, testUser);
                
                PresentationSlideElement elem = new PresentationSlideElement();
                elem.setDeckId(deck1.getId());
                elem.setSlideId(slide1.getId());
                elem.setElementType(PresentationElementType.IMAGE);
                elem.setZIndex(1);
                elem.setX(BigDecimal.ZERO);
                elem.setY(BigDecimal.ZERO);
                elem.setWidth(BigDecimal.TEN);
                elem.setHeight(BigDecimal.TEN);
                elem.setPayloadJson("{}");
                elem.setStyleJson("{}");
                elem.setMediaAssetId(media2.getId());
                elem.setCreatedAt(LocalDateTime.now(clock));
                elem.setUpdatedAt(LocalDateTime.now(clock));
                
                elementRepository.saveAndFlush(elem);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testH_jobOwnerMismatchingDeckOwnerRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                
                PresentationGenerationJob job = new PresentationGenerationJob();
                job.setOwner(testUser2); // wrong owner
                job.setDeckId(deck1.getId());
                job.setJobType(PresentationJobType.OUTLINE);
                job.setStatus(PresentationJobStatus.QUEUED);
                job.setIdempotencyKey("idemp");
                job.setRequestHash("req");
                job.setCreatedAt(LocalDateTime.now(clock));
                
                jobRepository.saveAndFlush(job);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testI_jobOutlineFromAnotherDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationOutlineRevision rev2 = createOutline(deck2, 1);
                
                PresentationGenerationJob job = new PresentationGenerationJob();
                job.setOwner(testUser);
                job.setDeckId(deck1.getId());
                job.setJobType(PresentationJobType.OUTLINE);
                job.setStatus(PresentationJobStatus.QUEUED);
                job.setIdempotencyKey("idemp");
                job.setRequestHash("req");
                job.setOutlineRevisionId(rev2.getId()); // mismatch
                job.setCreatedAt(LocalDateTime.now(clock));
                
                jobRepository.saveAndFlush(job);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testJ_jobTargetSlideFromAnotherDeckRejected() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck1 = createDeck(testUser, testNotebook);
                PresentationDeck deck2 = createDeck(testUser, testNotebook);
                PresentationSlide slide2 = createSlide(deck2, 1);
                
                PresentationGenerationJob job = new PresentationGenerationJob();
                job.setOwner(testUser);
                job.setDeckId(deck1.getId());
                job.setJobType(PresentationJobType.OUTLINE);
                job.setStatus(PresentationJobStatus.QUEUED);
                job.setIdempotencyKey("idemp");
                job.setRequestHash("req");
                job.setTargetSlideId(slide2.getId()); // mismatch
                job.setCreatedAt(LocalDateTime.now(clock));
                
                jobRepository.saveAndFlush(job);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testK_jobIdempotencyUniqueFence() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck = createDeck(testUser, testNotebook);
                
                PresentationGenerationJob job1 = new PresentationGenerationJob();
                job1.setOwner(testUser);
                job1.setDeckId(deck.getId());
                job1.setJobType(PresentationJobType.OUTLINE);
                job1.setStatus(PresentationJobStatus.QUEUED);
                job1.setIdempotencyKey("idemp_same");
                job1.setRequestHash("req1");
                job1.setCreatedAt(LocalDateTime.now(clock));
                job1 = jobRepository.saveAndFlush(job1);
                track(job1, jobIds, PresentationGenerationJob::getId);
                
                PresentationGenerationJob job2 = new PresentationGenerationJob();
                job2.setOwner(testUser);
                job2.setDeckId(deck.getId());
                job2.setJobType(PresentationJobType.OUTLINE);
                job2.setStatus(PresentationJobStatus.QUEUED);
                job2.setIdempotencyKey("idemp_same"); // same owner, deck, type, key
                job2.setRequestHash("req2");
                job2.setCreatedAt(LocalDateTime.now(clock));
                
                jobRepository.saveAndFlush(job2);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testL_citedDocumentChunkRESTRICTPreventsPhysicalDelete() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck = createDeck(testUser, testNotebook);
                PresentationSlide slide = createSlide(deck, 1);
                
                PresentationSlideSource src = new PresentationSlideSource();
                src.setSlide(slide);
                src.setDocumentChunk(testChunk); // chunk bound
                src.setDocument(testDoc);
                src.setSourceOrder(1);
                src.setEvidenceExcerpt("x");
                src.setEvidenceHash("y");
                src.setCreatedAt(LocalDateTime.now(clock));
                src = sourceRepository.saveAndFlush(src);
                track(src, sourceIds, PresentationSlideSource::getId);
                
                documentChunkRepository.delete(testChunk);
                documentChunkRepository.flush();
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testM_referencedMediaRESTRICTPreventsPhysicalDelete() {
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                PresentationDeck deck = createDeck(testUser, testNotebook);
                PresentationSlide slide = createSlide(deck, 1);
                PresentationMediaAsset media = createMedia(deck, testUser);
                
                PresentationSlideElement elem = new PresentationSlideElement();
                elem.setDeckId(deck.getId());
                elem.setSlideId(slide.getId());
                elem.setElementType(PresentationElementType.IMAGE);
                elem.setZIndex(1);
                elem.setX(BigDecimal.ZERO);
                elem.setY(BigDecimal.ZERO);
                elem.setWidth(BigDecimal.TEN);
                elem.setHeight(BigDecimal.TEN);
                elem.setPayloadJson("{}");
                elem.setStyleJson("{}");
                elem.setMediaAssetId(media.getId()); // bound
                elem.setCreatedAt(LocalDateTime.now(clock));
                elem.setUpdatedAt(LocalDateTime.now(clock));
                elem = elementRepository.saveAndFlush(elem);
                track(elem, elementIds, PresentationSlideElement::getId);
                
                mediaRepository.delete(media);
                mediaRepository.flush();
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testN_optimisticVersionStaleWriteRejected() {
        PresentationDeck deck = transactionTemplate.execute(status -> createDeck(testUser, testNotebook));
        
        // Load detached entity at version V
        PresentationDeck staleDeck = transactionTemplate.execute(status -> 
            deckRepository.findById(deck.getId()).get()
        );
        
        // In a separate committed transaction, load current entity, modify and commit -> DB becomes V+1
        transactionTemplate.execute(status -> {
            PresentationDeck freshDeck = deckRepository.findById(deck.getId()).get();
            freshDeck.setTitle("Title Interleaved");
            deckRepository.saveAndFlush(freshDeck);
            return null;
        });
        
        // Modify the stale detached V entity
        staleDeck.setTitle("Title 2");
        
        // Attempt stale save/merge in another transaction
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                deckRepository.saveAndFlush(staleDeck);
                return null;
            });
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
