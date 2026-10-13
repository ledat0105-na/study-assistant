package com.example.studyassistant.domain.presentation.media;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationDeckStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationMediaLicenseStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaSourceType;
import com.example.studyassistant.domain.presentation.PresentationOutlineRevisionRepository;
import com.example.studyassistant.domain.presentation.PresentationSlide;
import com.example.studyassistant.domain.presentation.PresentationSlideElement;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import com.example.studyassistant.domain.presentation.PresentationSlideRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideSourceRepository;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.PlanFeatureRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresentationMediaMySQLIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("presentation.media.storage-root", () -> STORAGE_ROOT.toString());
    }

    @Autowired private PresentationMediaService mediaService;
    @Autowired private MediaStorage mediaStorage;
    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationMediaAssetRepository mediaRepository;
    @Autowired private PresentationSlideRepository slideRepository;
    @Autowired private PresentationSlideElementRepository elementRepository;
    @Autowired private PresentationSlideSourceRepository sourceRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private PlanFeatureRepository planFeatureRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;

    private User owner;
    private Notebook notebook;
    private PlanFeature plan;
    private String planName;
    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> notebookIds = new LinkedHashSet<>();
    private final Set<Long> deckIds = new LinkedHashSet<>();
    private final Set<Long> slideIds = new LinkedHashSet<>();
    private final Set<Long> mediaIds = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            planName = "MEDIA_TEST_" + UUID.randomUUID().toString().replace("-", "");
            plan = new PlanFeature();
            plan.setPlanName(planName);
            plan.setMaxNotebooks(10);
            plan.setDailyChatLimit(10);
            plan.setDailyQuizLimit(10);
            plan.setDailyFlashcardLimit(10);
            plan.setMonthlyUploadLimit(10);
            plan.setMaxStorageBytes(100L * 1024 * 1024);
            plan.setChatRetentionDays(30);
            plan.setMaxPresentationMediaStorageBytes(100L * 1024 * 1024);
            plan = planFeatureRepository.saveAndFlush(plan);

            owner = createUser("owner");
            notebook = new Notebook();
            notebook.setUser(owner);
            notebook.setTitle("Media test notebook");
            notebook = notebookRepository.saveAndFlush(notebook);
            notebookIds.add(notebook.getId());
        });
    }

    @AfterEach
    void cleanFixtures() {
        List<String> storedKeys = transactionTemplate.execute(status -> {
            List<String> keys = new ArrayList<>();
            for (Long slideId : slideIds) {
                sourceRepository.deleteBySlideId(slideId);
                elementRepository.deleteBySlideId(slideId);
            }
            for (Long mediaId : mediaIds) {
                mediaRepository.findById(mediaId).ifPresent(asset -> {
                    keys.add(asset.getStoredFileKey());
                    mediaRepository.delete(asset);
                });
            }
            for (Long deckId : deckIds) {
                slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId).forEach(slide -> {
                    sourceRepository.deleteBySlideId(slide.getId());
                    elementRepository.deleteBySlideId(slide.getId());
                    slideRepository.delete(slide);
                });
                deckRepository.findById(deckId).ifPresent(deck -> {
                    deck.setApprovedOutlineRevision(null);
                    deckRepository.save(deck);
                });
                deckRepository.flush();
                outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deckId).forEach(outlineRepository::delete);
            }
            slideRepository.flush();
            mediaRepository.flush();
            deckIds.forEach(deckId -> deckRepository.findById(deckId).ifPresent(deckRepository::delete));
            deckRepository.flush();
            notebookIds.forEach(id -> notebookRepository.findById(id).ifPresent(notebookRepository::delete));
            notebookRepository.flush();
            userIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
            planFeatureRepository.findByPlanName(planName).ifPresent(planFeatureRepository::delete);
            return keys;
        });

        for (String key : storedKeys) {
            try {
                mediaStorage.delete(key);
            } catch (IOException ignored) {
            }
        }
        deckIds.clear();
        slideIds.clear();
        mediaIds.clear();
        notebookIds.clear();
        userIds.clear();
    }

    @AfterAll
    static void deleteStorageRoot() throws IOException {
        if (Files.exists(STORAGE_ROOT)) {
            try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void K_foreignDeckIsNotFound() throws Exception {
        PresentationDeck deck = createDeck(owner);
        var upload = mediaService.upload(owner, deck.getId(), pngUpload("owner.png"));
        Long assetId = upload.id();
        mediaIds.add(assetId);
        User foreign = transactionTemplate.execute(status -> createUser("foreign"));

        assertThatThrownBy(() -> mediaService.get(foreign, deck.getId(), assetId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void N_mediaListAndGetAreOwnerScoped() throws Exception {
        PresentationDeck deck = createDeck(owner);
        var upload = mediaService.upload(owner, deck.getId(), pngUpload("owner.png"));
        mediaIds.add(upload.id());
        assertThat(mediaService.list(owner, deck.getId())).extracting(dto -> dto.id()).containsExactly(upload.id());
        assertThat(mediaService.get(owner, deck.getId(), upload.id()).contentHash()).isEqualTo(upload.contentHash());
    }

    @Test
    void P_sameBytesCreateSeparateOwnedAssets() throws Exception {
        PresentationDeck deck = createDeck(owner);
        byte[] bytes = pngBytes();
        var first = mediaService.upload(owner, deck.getId(), pngUpload("first.png", bytes));
        var second = mediaService.upload(owner, deck.getId(), pngUpload("second.png", bytes));
        mediaIds.add(first.id());
        mediaIds.add(second.id());
        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(mediaRepository.findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(deck.getId(), owner.getId())).hasSize(2);
        assertThat(mediaRepository.sumSizeBytesByOwnerId(owner.getId())).isEqualTo((long) bytes.length * 2);
    }

    @Test
    void Q_concurrentUploadsAcrossDecksRespectOwnerAggregateQuota() throws Exception {
        byte[] bytes = pngBytes();
        plan.setMaxPresentationMediaStorageBytes((long) bytes.length);
        transactionTemplate.executeWithoutResult(status -> planFeatureRepository.saveAndFlush(plan));
        PresentationDeck firstDeck = createDeck(owner);
        PresentationDeck secondDeck = createDeck(owner);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<UploadOutcome> outcomes;
        try {
            Future<UploadOutcome> first = executor.submit(() -> uploadAfterLatch(start, firstDeck, "q-first.png", bytes));
            Future<UploadOutcome> second = executor.submit(() -> uploadAfterLatch(start, secondDeck, "q-second.png", bytes));
            start.countDown();
            outcomes = List.of(first.get(), second.get());
        } finally {
            executor.shutdown();
        }
        assertThat(outcomes.stream().filter(UploadOutcome::success).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> outcome.status() == HttpStatus.PAYLOAD_TOO_LARGE).count()).isEqualTo(1);
        List<PresentationMediaAsset> firstAssets = mediaRepository.findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(firstDeck.getId(), owner.getId());
        List<PresentationMediaAsset> secondAssets = mediaRepository.findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(secondDeck.getId(), owner.getId());
        assertThat(firstAssets.size() + secondAssets.size()).isEqualTo(1);
        List<PresentationMediaAsset> createdAssets = new ArrayList<>(firstAssets);
        createdAssets.addAll(secondAssets);
        createdAssets.forEach(asset -> mediaIds.add(asset.getId()));
        assertThat(mediaRepository.sumSizeBytesByOwnerId(owner.getId())).isEqualTo(bytes.length);
    }

    @Test
    void T_referencedAssetCannotBeDeleted() throws Exception {
        PresentationDeck deck = createDeck(owner);
        var uploaded = mediaService.upload(owner, deck.getId(), pngUpload("referenced.png"));
        mediaIds.add(uploaded.id());
        PresentationSlide slide = createSlide(deck);
        transactionTemplate.executeWithoutResult(status -> {
            PresentationSlideElement element = new PresentationSlideElement();
            element.setDeckId(deck.getId());
            element.setSlideId(slide.getId());
            element.setMediaAssetId(uploaded.id());
            element.setElementType(com.example.studyassistant.domain.presentation.PresentationElementType.IMAGE);
            element.setZIndex(0);
            element.setX(new java.math.BigDecimal("1"));
            element.setY(new java.math.BigDecimal("1"));
            element.setWidth(new java.math.BigDecimal("10"));
            element.setHeight(new java.math.BigDecimal("10"));
            element.setPayloadJson("{}");
            element.setStyleJson("{}");
            element.setCreatedAt(LocalDateTime.now(clock));
            element.setUpdatedAt(LocalDateTime.now(clock));
            elementRepository.saveAndFlush(element);
        });

        assertThatThrownBy(() -> mediaService.delete(owner, deck.getId(), uploaded.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void U_unreferencedDeleteRemovesAssetAndPhysicalFile() throws Exception {
        PresentationDeck deck = createDeck(owner);
        var uploaded = mediaService.upload(owner, deck.getId(), pngUpload("delete.png"));
        mediaIds.add(uploaded.id());
        String storedKey = mediaRepository.findByIdAndDeckIdAndOwnerId(uploaded.id(), deck.getId(), owner.getId())
                .orElseThrow().getStoredFileKey();
        mediaService.delete(owner, deck.getId(), uploaded.id());
        assertThat(mediaRepository.findById(uploaded.id())).isEmpty();
        assertThatThrownBy(() -> mediaStorage.open(storedKey)).isInstanceOf(IOException.class);
    }

    @Test
    void X_archivedDeckAllowsScopedReadButRejectsUploadAndDelete() throws Exception {
        PresentationDeck deck = createDeck(owner);
        var uploaded = mediaService.upload(owner, deck.getId(), pngUpload("archived.png"));
        mediaIds.add(uploaded.id());
        String storedKey = mediaRepository.findByIdAndDeckIdAndOwnerId(uploaded.id(), deck.getId(), owner.getId())
                .orElseThrow().getStoredFileKey();
        transactionTemplate.executeWithoutResult(status -> {
            PresentationDeck archived = deckRepository.findOwnedByIdForUpdate(deck.getId(), owner.getId()).orElseThrow();
            archived.setStatus(PresentationDeckStatus.ARCHIVED);
            deckRepository.saveAndFlush(archived);
        });

        assertThat(mediaService.get(owner, deck.getId(), uploaded.id()).id()).isEqualTo(uploaded.id());
        try (var content = mediaService.openContent(owner, deck.getId(), uploaded.id()).inputStream()) {
            assertThat(content.readAllBytes()).isNotEmpty();
        }
        assertThatThrownBy(() -> mediaService.upload(owner, deck.getId(), pngUpload("blocked.png")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> mediaService.delete(owner, deck.getId(), uploaded.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        try (var storedContent = mediaStorage.open(storedKey)) {
            assertThat(storedContent.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void W_crossDeckImageElementIsRejectedByCompositeForeignKey() throws Exception {
        PresentationDeck mediaDeck = createDeck(owner);
        PresentationDeck slideDeck = createDeck(owner);
        PresentationMediaAsset media = new PresentationMediaAsset();
        media.setOwner(owner);
        media.setDeckId(mediaDeck.getId());
        media.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
        media.setOriginalFileName("foreign-deck.png");
        media.setStoredFileKey(UUID.randomUUID().toString());
        media.setMimeType("image/png");
        media.setSizeBytes(16L);
        media.setSha256("a".repeat(64));
        media.setWidth(1);
        media.setHeight(1);
        media.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
        media.setCreatedAt(LocalDateTime.now(clock));
        media = mediaRepository.saveAndFlush(media);
        mediaIds.add(media.getId());
        Long mediaId = media.getId();
        PresentationSlide slide = createSlide(slideDeck);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            PresentationSlideElement element = new PresentationSlideElement();
            element.setDeckId(slideDeck.getId());
            element.setSlideId(slide.getId());
            element.setMediaAssetId(mediaId);
            element.setElementType(com.example.studyassistant.domain.presentation.PresentationElementType.IMAGE);
            element.setZIndex(0);
            element.setX(new java.math.BigDecimal("1"));
            element.setY(new java.math.BigDecimal("1"));
            element.setWidth(new java.math.BigDecimal("10"));
            element.setHeight(new java.math.BigDecimal("10"));
            element.setPayloadJson("{}");
            element.setStyleJson("{}");
            element.setCreatedAt(LocalDateTime.now(clock));
            element.setUpdatedAt(LocalDateTime.now(clock));
            elementRepository.saveAndFlush(element);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UploadOutcome uploadAfterLatch(CountDownLatch start, PresentationDeck deck, String filename, byte[] bytes) throws Exception {
        start.await();
        try {
            var result = mediaService.upload(owner, deck.getId(), pngUpload(filename, bytes));
            return new UploadOutcome(true, null);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != HttpStatus.PAYLOAD_TOO_LARGE.value()) throw e;
            return new UploadOutcome(false, HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private PresentationDeck createDeck(User deckOwner) {
        PresentationDeck deck = transactionTemplate.execute(status -> {
            PresentationDeck created = new PresentationDeck();
            created.setOwner(deckOwner);
            created.setNotebook(notebook);
            created.setTitle("Media fixture deck");
            created.setStatus(PresentationDeckStatus.DRAFT);
            created.setCreatedAt(LocalDateTime.now(clock));
            created.setUpdatedAt(LocalDateTime.now(clock));
            return deckRepository.saveAndFlush(created);
        });
        deckIds.add(deck.getId());
        return deck;
    }

    private PresentationSlide createSlide(PresentationDeck deck) {
        PresentationSlide slide = transactionTemplate.execute(status -> {
            PresentationSlide created = new PresentationSlide();
            created.setDeckId(deck.getId());
            created.setSlideOrder(1);
            created.setTitle("Slide");
            created.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
            created.setCreatedAt(LocalDateTime.now(clock));
            created.setUpdatedAt(LocalDateTime.now(clock));
            return slideRepository.saveAndFlush(created);
        });
        slideIds.add(slide.getId());
        return slide;
    }

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("media_" + suffix + "_" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.test");
        user.setPassword("integration-test");
        user.setFullName("Media test user");
        user.setRole("TEACHER");
        user.setPlan(planName);
        user = userRepository.saveAndFlush(user);
        userIds.add(user.getId());
        return user;
    }

    private MockMultipartFile pngUpload(String filename) throws IOException {
        return pngUpload(filename, pngBytes());
    }

    private MockMultipartFile pngUpload(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "image/png", bytes);
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(5, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x4288aa);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("presentation-media-mysql-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private record UploadOutcome(boolean success, HttpStatus status) {}
}
