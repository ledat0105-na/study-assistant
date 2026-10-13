package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.dto.SlideElementCreateRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementDeleteRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementUpdateRequest;
import com.example.studyassistant.domain.presentation.dto.SlidePatchRequest;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
class PresentationSlideEditorMySQLIntegrationTest {

    @Autowired private PresentationSlideEditorService editorService;
    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @Autowired private PresentationSlideRepository slideRepository;
    @Autowired private PresentationSlideElementRepository elementRepository;
    @Autowired private PresentationSlideSourceRepository sourceRepository;
    @Autowired private PresentationMediaAssetRepository mediaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;
    @MockitoSpyBean private PresentationSlideRepository slideRepositorySpy;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> notebookIds = new ArrayList<>();
    private final List<Long> documentIds = new ArrayList<>();
    private final List<Long> chunkIds = new ArrayList<>();
    private final List<Long> deckIds = new ArrayList<>();
    private final List<Long> outlineIds = new ArrayList<>();
    private final List<Long> slideIds = new ArrayList<>();
    private final List<Long> elementIds = new ArrayList<>();
    private final List<Long> sourceIds = new ArrayList<>();
    private final List<Long> mediaIds = new ArrayList<>();

    private User owner;
    private User foreignOwner;
    private Notebook ownerNotebook;
    private Notebook foreignNotebook;
    private PresentationDeck deck;
    private PresentationDeck secondDeck;
    private PresentationDeck foreignDeck;
    private PresentationSlide slide;
    private PresentationSlide foreignSlide;
    private PresentationSlideSource source;
    private PresentationMediaAsset sameDeckMedia;
    private PresentationMediaAsset secondDeckMedia;
    private PresentationMediaAsset foreignDeckMedia;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            owner = createUser("owner");
            foreignOwner = createUser("foreign");
            ownerNotebook = createNotebook(owner);
            foreignNotebook = createNotebook(foreignOwner);
            deck = createApprovedDeck(owner, ownerNotebook, "primary");
            secondDeck = createApprovedDeck(owner, ownerNotebook, "second");
            foreignDeck = createApprovedDeck(foreignOwner, foreignNotebook, "foreign");
            slide = createSlide(deck, 1);
            foreignSlide = createSlide(foreignDeck, 1);
            source = createSource(slide);
            sameDeckMedia = createMedia(deck, owner, "same-deck-key");
            secondDeckMedia = createMedia(secondDeck, owner, "second-deck-key");
            foreignDeckMedia = createMedia(foreignDeck, foreignOwner, "foreign-deck-key");
        });
    }

    @AfterEach
    void cleanFixtures() {
        transactionTemplate.executeWithoutResult(status -> {
            sourceIds.forEach(id -> sourceRepository.findById(id).ifPresent(sourceRepository::delete));
            elementIds.forEach(id -> elementRepository.findById(id).ifPresent(elementRepository::delete));
            slideIds.forEach(id -> slideRepository.findById(id).ifPresent(slideRepository::delete));
            deckIds.forEach(id -> deckRepository.findById(id).ifPresent(entry -> {
                entry.setApprovedOutlineRevision(null);
                deckRepository.save(entry);
            }));
            deckRepository.flush();
            outlineIds.forEach(id -> outlineRepository.findById(id).ifPresent(outlineRepository::delete));
            mediaIds.forEach(id -> mediaRepository.findById(id).ifPresent(mediaRepository::delete));
            deckIds.forEach(id -> deckRepository.findById(id).ifPresent(deckRepository::delete));
            chunkIds.forEach(id -> chunkRepository.findById(id).ifPresent(chunkRepository::delete));
            documentIds.forEach(id -> documentRepository.findById(id).ifPresent(documentRepository::delete));
            notebookIds.forEach(id -> notebookRepository.findById(id).ifPresent(notebookRepository::delete));
            userIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
        });
        userIds.clear();
        notebookIds.clear();
        documentIds.clear();
        chunkIds.clear();
        deckIds.clear();
        outlineIds.clear();
        slideIds.clear();
        elementIds.clear();
        sourceIds.clear();
        mediaIds.clear();
    }

    @Test
    void generatedReadAndManualTextShapeEditsPreserveSourcesAndEditedState() {
        assertThat(editorService.getSlide(owner, deck.getId(), slide.getId()).generationState()).isEqualTo("GENERATED");
        Long sourceId = source.getId();
        String excerpt = source.getEvidenceExcerpt();
        String hash = source.getEvidenceHash();
        Integer sourceOrder = source.getSourceOrder();

        var titleEdit = editorService.patchSlide(owner, deck.getId(), slide.getId(),
                new SlidePatchRequest("Updated title", null, versionOfSlide(slide.getId())));
        assertThat(titleEdit.title()).isEqualTo("Updated title");
        assertThat(titleEdit.generationState()).isEqualTo("EDITED");
        var notesEdit = editorService.patchSlide(owner, deck.getId(), slide.getId(),
                new SlidePatchRequest(null, "Updated notes", titleEdit.version()));
        assertThat(notesEdit.speakerNotes()).isEqualTo("Updated notes");
        assertThat(notesEdit.generationState()).isEqualTo("EDITED");

        var text = editorService.addElement(owner, deck.getId(), slide.getId(), createRequest("TEXT",
                versionOfSlide(slide.getId()), null, Map.of("text", "before"), Map.of("fontSizePt", 18)));
        assertThat(text.elementType()).isEqualTo("TEXT");
        assertThat(text.payload()).containsEntry("text", "before");
        trackElement(text.id());
        var updatedText = editorService.updateElement(owner, deck.getId(), slide.getId(), text.id(), updateRequest(
                "TEXT", versionOfSlide(slide.getId()), text.version(), null, Map.of("text", "after"), Map.of("fontSizePt", 20)));
        assertThat(updatedText.payload()).containsEntry("text", "after");

        var shape = editorService.addElement(owner, deck.getId(), slide.getId(), createRequest("SHAPE",
                versionOfSlide(slide.getId()), null, Map.of("primitive", "RECTANGLE", "text", "shape"),
                Map.of("fillColor", "#AABBCC")));
        trackElement(shape.id());
        var updatedShape = editorService.updateElement(owner, deck.getId(), slide.getId(), shape.id(), updateRequest(
                "SHAPE", versionOfSlide(slide.getId()), shape.version(), null,
                Map.of("primitive", "ELLIPSE", "text", "updated"), Map.of("fillColor", "#112233")));
        assertThat(updatedShape.payload()).containsEntry("primitive", "ELLIPSE");

        editorService.deleteElement(owner, deck.getId(), slide.getId(), text.id(),
                new SlideElementDeleteRequest(versionOfSlide(slide.getId()), updatedText.version()));
        editorService.deleteElement(owner, deck.getId(), slide.getId(), shape.id(),
                new SlideElementDeleteRequest(versionOfSlide(slide.getId()), updatedShape.version()));
        assertThat(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId())).isEmpty();
        assertThat(slideRepository.findById(slide.getId()).orElseThrow().getGenerationState())
                .isEqualTo(PresentationSlideGenerationState.EDITED);

        PresentationSlideSource reloaded = sourceRepository.findById(sourceId).orElseThrow();
        assertThat(reloaded.getEvidenceExcerpt()).isEqualTo(excerpt);
        assertThat(reloaded.getEvidenceHash()).isEqualTo(hash);
        assertThat(reloaded.getSourceOrder()).isEqualTo(sourceOrder);
        assertThat(reloaded.getSlide().getId()).isEqualTo(slide.getId());
    }

    @Test
    void sameDeckImageIsAcceptedAndOtherDeckMediaIsNotFound() {
        var accepted = editorService.addElement(owner, deck.getId(), slide.getId(), createRequest("IMAGE",
                versionOfSlide(slide.getId()), sameDeckMedia.getId(), Map.of("alt", "owned"), Map.of()));
        trackElement(accepted.id());
        assertThat(accepted.mediaAssetId()).isEqualTo(sameDeckMedia.getId());

        assertStatus(HttpStatus.NOT_FOUND, () -> editorService.addElement(owner, deck.getId(), slide.getId(), createRequest(
                "IMAGE", versionOfSlide(slide.getId()), secondDeckMedia.getId(), Map.of("alt", "cross-deck"), Map.of())));
        assertStatus(HttpStatus.NOT_FOUND, () -> editorService.addElement(owner, deck.getId(), slide.getId(), createRequest(
                "IMAGE", versionOfSlide(slide.getId()), foreignDeckMedia.getId(), Map.of("alt", "foreign"), Map.of())));
    }

    @Test
    void foreignSlideAndElementAreNotFound() {
        PresentationSlideElement foreignElement = createTextElement(foreignDeck.getId(), foreignSlide.getId(), "foreign");

        assertStatus(HttpStatus.NOT_FOUND, () -> editorService.getSlide(owner, deck.getId(), foreignSlide.getId()));
        assertStatus(HttpStatus.NOT_FOUND, () -> editorService.updateElement(owner, deck.getId(), slide.getId(),
                foreignElement.getId(), updateRequest("TEXT", versionOfSlide(slide.getId()), foreignElement.getVersion(),
                        null, Map.of("text", "attempt"), Map.of("fontSizePt", 18))));
    }

    @Test
    void archivedDeckAllowsReadsAndRejectsMutation() {
        transactionTemplate.executeWithoutResult(status -> {
            PresentationDeck archived = deckRepository.findById(deck.getId()).orElseThrow();
            archived.setStatus(PresentationDeckStatus.ARCHIVED);
            deckRepository.saveAndFlush(archived);
        });

        assertThat(editorService.getSlide(owner, deck.getId(), slide.getId()).id()).isEqualTo(slide.getId());
        assertThat(editorService.listSlides(owner, deck.getId())).extracting(dto -> dto.id()).contains(slide.getId());
        assertStatus(HttpStatus.CONFLICT, () -> editorService.patchSlide(owner, deck.getId(), slide.getId(),
                new SlidePatchRequest("blocked", null, versionOfSlide(slide.getId()))));
    }

    @Test
    void staleSlideAndElementVersionsReturnConflict() {
        assertStatus(HttpStatus.CONFLICT, () -> editorService.patchSlide(owner, deck.getId(), slide.getId(),
                new SlidePatchRequest("stale", null, -1L)));
        assertStatus(HttpStatus.CONFLICT, () -> editorService.addElement(owner, deck.getId(), slide.getId(),
                createRequest("TEXT", -1L, null, Map.of("text", "stale"), Map.of("fontSizePt", 18))));

        PresentationSlideElement element = createTextElement(deck.getId(), slide.getId(), "current");
        assertStatus(HttpStatus.CONFLICT, () -> editorService.updateElement(owner, deck.getId(), slide.getId(), element.getId(),
                updateRequest("TEXT", versionOfSlide(slide.getId()), -1L, null, Map.of("text", "stale"), Map.of("fontSizePt", 18))));
        assertStatus(HttpStatus.CONFLICT, () -> editorService.deleteElement(owner, deck.getId(), slide.getId(), element.getId(),
                new SlideElementDeleteRequest(versionOfSlide(slide.getId()), -1L)));
    }

    @Test
    void failedMutationAfterElementFlushRollsBackElementAndSlide() {
        PresentationSlideElement element = createTextElement(deck.getId(), slide.getId(), "before");
        Long originalSlideVersion = versionOfSlide(slide.getId());
        Long originalElementVersion = element.getVersion();
        Long elementId = element.getId();
        SlideElementUpdateRequest request = updateRequest("TEXT", originalSlideVersion, originalElementVersion,
                null, Map.of("text", "after"), Map.of("fontSizePt", 18));

        doThrow(new IllegalStateException("deterministic failure after element flush"))
                .when(slideRepositorySpy).saveAndFlush(any(PresentationSlide.class));
        assertThatThrownBy(() -> editorService.updateElement(owner, deck.getId(), slide.getId(), elementId, request))
                .isInstanceOf(IllegalStateException.class);

        PresentationSlide reloadedSlide = slideRepository.findById(slide.getId()).orElseThrow();
        PresentationSlideElement reloadedElement = elementRepository.findById(elementId).orElseThrow();
        assertThat(reloadedSlide.getGenerationState()).isEqualTo(PresentationSlideGenerationState.GENERATED);
        assertThat(reloadedSlide.getVersion()).isEqualTo(originalSlideVersion);
        assertThat(reloadedElement.getPayloadJson()).contains("before").doesNotContain("after");
        assertThat(reloadedElement.getVersion()).isEqualTo(originalElementVersion);
    }

    @Test
    void invalidGeometryAndTypedPayloadAreRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> editorService.addElement(owner, deck.getId(), slide.getId(),
                new SlideElementCreateRequest("TEXT", versionOfSlide(slide.getId()), new BigDecimal("-1"), BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, Map.of("text", "ok"), Map.of("fontSizePt", 18), 0)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> editorService.addElement(owner, deck.getId(), slide.getId(),
                createRequest("TEXT", versionOfSlide(slide.getId()), null, Map.of("text", "x".repeat(2001)), Map.of("fontSizePt", 18))));
    }

    private User createUser(String prefix) {
        User user = new User();
        String unique = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        user.setUsername(unique);
        user.setEmail(unique + "@test.com");
        user.setPassword("integration-test-password");
        user.setFullName(prefix);
        user.setRole("TEACHER");
        user.setPlan("PREMIUM");
        user = userRepository.saveAndFlush(user);
        userIds.add(user.getId());
        return user;
    }

    private Notebook createNotebook(User user) {
        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Slide editor fixture " + UUID.randomUUID());
        notebook = notebookRepository.saveAndFlush(notebook);
        notebookIds.add(notebook.getId());
        return notebook;
    }

    private PresentationDeck createApprovedDeck(User user, Notebook notebook, String suffix) {
        PresentationDeck created = new PresentationDeck();
        created.setOwner(user);
        created.setNotebook(notebook);
        created.setTitle("Slide editor " + suffix + " " + UUID.randomUUID());
        created.setStatus(PresentationDeckStatus.DRAFT);
        created.setAspectRatio("16:9");
        created.setThemeKey("DEFAULT");
        created.setCreatedAt(LocalDateTime.now(clock));
        created.setUpdatedAt(LocalDateTime.now(clock));
        created = deckRepository.saveAndFlush(created);
        deckIds.add(created.getId());

        PresentationOutlineRevision revision = new PresentationOutlineRevision();
        revision.setDeckId(created.getId());
        revision.setRevisionNumber(1);
        revision.setStatus(PresentationOutlineStatus.APPROVED);
        revision.setGenerationScopeSnapshot("{}");
        revision.setOutlineJson("{}");
        revision.setContentHash("hash-" + UUID.randomUUID());
        revision.setCreatedBy(user);
        revision.setCreatedAt(LocalDateTime.now(clock));
        revision.setApprovedAt(LocalDateTime.now(clock));
        revision = outlineRepository.saveAndFlush(revision);
        outlineIds.add(revision.getId());
        created.setApprovedOutlineRevision(revision);
        return deckRepository.saveAndFlush(created);
    }

    private PresentationSlide createSlide(PresentationDeck parentDeck, int order) {
        PresentationSlide created = new PresentationSlide();
        created.setDeckId(parentDeck.getId());
        created.setSlideOrder(order);
        created.setTitle("Generated slide");
        created.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
        created.setGenerationState(PresentationSlideGenerationState.GENERATED);
        created.setGeneratedFromOutlineRevisionId(parentDeck.getApprovedOutlineRevision().getId());
        created.setGenerationKey("integration-" + UUID.randomUUID());
        created.setCreatedAt(LocalDateTime.now(clock));
        created.setUpdatedAt(LocalDateTime.now(clock));
        created = slideRepository.saveAndFlush(created);
        slideIds.add(created.getId());
        return created;
    }

    private PresentationSlideSource createSource(PresentationSlide parentSlide) {
        Document document = new Document();
        document.setUser(owner);
        document.setNotebook(ownerNotebook);
        document.setFileName("source.pdf");
        document.setFileSize(100L);
        document.setFileType("PDF");
        document.setFilePath("fixture-source-key");
        document.setStatus("READY");
        document.setActiveIndexVersion("slide-editor-v1");
        document = documentRepository.saveAndFlush(document);
        documentIds.add(document.getId());

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(1);
        chunk.setContent("Source evidence text");
        chunk.setTokenCount(4);
        chunk.setIndexVersion("slide-editor-v1");
        chunk = chunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());

        PresentationSlideSource created = new PresentationSlideSource();
        created.setSlide(parentSlide);
        created.setDocument(document);
        created.setDocumentChunk(chunk);
        created.setSourceOrder(1);
        created.setEvidenceExcerpt("Source evidence text");
        created.setEvidenceHash("source-hash");
        created.setPageStart(1);
        created.setPageEnd(1);
        created.setSectionTitle("Fixture section");
        created.setSourceAlias("S1");
        created.setCreatedAt(LocalDateTime.now(clock));
        created = sourceRepository.saveAndFlush(created);
        sourceIds.add(created.getId());
        return created;
    }

    private PresentationMediaAsset createMedia(PresentationDeck parentDeck, User mediaOwner, String key) {
        PresentationMediaAsset asset = new PresentationMediaAsset();
        asset.setOwner(mediaOwner);
        asset.setDeckId(parentDeck.getId());
        asset.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
        asset.setOriginalFileName("fixture.png");
        asset.setStoredFileKey(key + "-" + UUID.randomUUID());
        asset.setMimeType("image/png");
        asset.setSizeBytes(100L);
        asset.setSha256(UUID.randomUUID().toString().replace("-", ""));
        asset.setWidth(100);
        asset.setHeight(100);
        asset.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
        asset.setCreatedAt(LocalDateTime.now(clock));
        asset = mediaRepository.saveAndFlush(asset);
        mediaIds.add(asset.getId());
        return asset;
    }

    private PresentationSlideElement createTextElement(Long deckId, Long slideId, String text) {
        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deckId);
        element.setSlideId(slideId);
        element.setElementType(PresentationElementType.TEXT);
        element.setZIndex(0);
        element.setX(BigDecimal.ZERO);
        element.setY(BigDecimal.ZERO);
        element.setWidth(new BigDecimal("100.00"));
        element.setHeight(new BigDecimal("50.00"));
        element.setRotation(BigDecimal.ZERO);
        element.setPayloadJson("{\"text\":\"" + text + "\"}");
        element.setStyleJson("{\"fontSizePt\":18}");
        element.setCreatedAt(LocalDateTime.now(clock));
        element.setUpdatedAt(LocalDateTime.now(clock));
        element = elementRepository.saveAndFlush(element);
        trackElement(element.getId());
        return element;
    }

    private SlideElementCreateRequest createRequest(String type, Long slideVersion, Long mediaId,
                                                     Map<String, Object> payload, Map<String, Object> style) {
        return new SlideElementCreateRequest(type, slideVersion, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, mediaId, payload, style, 0);
    }

    private SlideElementUpdateRequest updateRequest(String type, Long slideVersion, Long elementVersion, Long mediaId,
                                                     Map<String, Object> payload, Map<String, Object> style) {
        return new SlideElementUpdateRequest(type, slideVersion, elementVersion, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, mediaId, payload, style, 0);
    }

    private Long versionOfSlide(Long slideId) {
        return slideRepository.findById(slideId).orElseThrow().getVersion();
    }

    private void trackElement(Long id) {
        Long trackedId = Objects.requireNonNull(id, "tracked element id");
        if (!elementIds.contains(trackedId)) {
            elementIds.add(trackedId);
        }
    }

    private static void assertStatus(HttpStatus expected, ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
