package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationDeckStatus;
import com.example.studyassistant.domain.presentation.PresentationElementType;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationMediaLicenseStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaSourceType;
import com.example.studyassistant.domain.presentation.PresentationOutlineRevision;
import com.example.studyassistant.domain.presentation.PresentationOutlineRevisionRepository;
import com.example.studyassistant.domain.presentation.PresentationOutlineStatus;
import com.example.studyassistant.domain.presentation.PresentationSlide;
import com.example.studyassistant.domain.presentation.PresentationSlideElement;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideGenerationState;
import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import com.example.studyassistant.domain.presentation.PresentationSlideRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideSource;
import com.example.studyassistant.domain.presentation.PresentationSlideSourceRepository;
import com.example.studyassistant.domain.presentation.export.PresentationExportStateInvalidException;
import com.example.studyassistant.domain.presentation.media.MediaStorage;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresentationPptxExportMySQLIntegrationTest {

    private static final Path MEDIA_ROOT = createMediaRoot();

    @DynamicPropertySource
    static void mediaStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("presentation.media.storage-root", () -> MEDIA_ROOT.toString());
    }

    @Autowired private PresentationPptxExportService exportService;
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
    @Autowired private MediaStorage mediaStorage;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;

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
    private final List<String> mediaKeys = new ArrayList<>();

    private User owner;
    private User foreignOwner;
    private Notebook notebook;
    private Notebook foreignNotebook;
    private PresentationDeck deck;
    private PresentationDeck foreignDeck;
    private PresentationSlide firstSlide;
    private PresentationSlide blankSlide;
    private PresentationSlideSource source;
    private PresentationMediaAsset pngMedia;

    @BeforeEach
    void setUp() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            owner = createUser("export_teacher");
            foreignOwner = createUser("export_foreign");
            notebook = createNotebook(owner);
            foreignNotebook = createNotebook(foreignOwner);
            deck = createDeck(owner, notebook, "Export deck");
            foreignDeck = createDeck(foreignOwner, foreignNotebook, "Foreign deck");
            PresentationOutlineRevision revision = createApprovedOutline(deck, owner);
            PresentationOutlineRevision foreignRevision = createApprovedOutline(foreignDeck, foreignOwner);
            deck.setApprovedOutlineRevision(revision);
            deckRepository.saveAndFlush(deck);
            foreignDeck.setApprovedOutlineRevision(foreignRevision);
            deckRepository.saveAndFlush(foreignDeck);
            firstSlide = createSlide(deck, revision, 1, "Persisted title", "persisted speaker notes", PresentationSlideGenerationState.GENERATED);
            blankSlide = createSlide(deck, revision, 2, "", null, PresentationSlideGenerationState.EDITED);
            source = createSource(firstSlide);
        });

        byte[] png = imageBytes("png");
        byte[] jpeg = imageBytes("jpeg");
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        transactionTemplate.executeWithoutResult(status -> {
            pngMedia = createMedia(deck, owner, "image/png", png, "fixture.png");
            PresentationMediaAsset jpegMedia = createMedia(deck, owner, "image/jpeg", jpeg, "fixture.jpeg");
            PresentationMediaAsset webpMedia = createMedia(deck, owner, "image/webp", webp, "fixture.webp");
            createTextElement(deck, firstSlide, 0, "{\"text\":\"Persisted body\"}",
                    "{\"fontSizePt\":20,\"bold\":true,\"color\":\"#124578\"}");
            createShapeElement(deck, firstSlide, 1);
            createImageElement(deck, firstSlide, 2, pngMedia);
            createImageElement(deck, firstSlide, 3, pngMedia);
            createImageElement(deck, firstSlide, 4, jpegMedia);
            createImageElement(deck, firstSlide, 5, webpMedia);
        });
    }

    @AfterEach
    void cleanupFixtures() throws Exception {
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
        for (String key : mediaKeys) {
            mediaStorage.delete(key);
        }
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
        mediaKeys.clear();
    }

    @AfterAll
    static void removeMediaRoot() throws IOException {
        if (Files.exists(MEDIA_ROOT)) {
            try (Stream<Path> paths = Files.walk(MEDIA_ROOT)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void realPersistedMediaSlidesNotesArchiveAndSourcesRoundTrip() throws Exception {
        Long sourceId = source.getId();
        String sourceExcerpt = source.getEvidenceExcerpt();
        String sourceHash = source.getEvidenceHash();
        Long firstSlideId = firstSlide.getId();
        Long blankSlideId = blankSlide.getId();
        Long deckId = deck.getId();

        transactionTemplate.executeWithoutResult(status -> {
            PresentationDeck archived = deckRepository.findById(deckId).orElseThrow();
            archived.setStatus(PresentationDeckStatus.ARCHIVED);
            deckRepository.saveAndFlush(archived);
        });

            Long deckVersion = deckRepository.findById(deckId).orElseThrow().getVersion();
            Long firstSlideVersion = slideRepository.findById(firstSlideId).orElseThrow().getVersion();
            Long blankSlideVersion = slideRepository.findById(blankSlideId).orElseThrow().getVersion();
            List<String> elementPayloads = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(firstSlideId).stream()
                .map(PresentationSlideElement::getPayloadJson).toList();
            List<String> elementStyles = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(firstSlideId).stream()
                .map(PresentationSlideElement::getStyleJson).toList();
            List<Long> elementVersions = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(firstSlideId).stream()
                .map(PresentationSlideElement::getVersion).toList();

        try (PptxExportArtifact artifact = exportService.export(owner, deckId);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides()).hasSize(2);
            assertThat(reopened.getPageSize().getWidth()).isEqualTo(960.0);
            assertThat(reopened.getPageSize().getHeight()).isEqualTo(540.0);
            assertThat(reopened.getPictureData()).hasSize(3);
            assertThat(reopened.getPictureData()).extracting(data -> data.getType())
                    .containsExactly(PictureData.PictureType.PNG, PictureData.PictureType.JPEG,
                            PictureData.PictureType.PNG);
            XSLFSlide first = reopened.getSlides().get(0);
            assertThat(first.getShapes()).hasSize(7);
            assertThat(first.getShapes().get(0).toString()).contains("TextBox");
            XSLFNotes notes = reopened.getNotesSlide(first);
            String noteText = notes.getTextParagraphs().stream().flatMap(List::stream)
                    .map(XSLFTextParagraph::getText).reduce("", String::concat);
            assertThat(noteText).contains("persisted speaker notes");
            assertThat(reopened.getSlides().get(1).getShapes()).isEmpty();
        }

        assertThat(sourceRepository.findById(sourceId).orElseThrow().getEvidenceExcerpt()).isEqualTo(sourceExcerpt);
        assertThat(sourceRepository.findById(sourceId).orElseThrow().getEvidenceHash()).isEqualTo(sourceHash);
        assertThat(slideRepository.findById(firstSlideId).orElseThrow().getGenerationState())
                .isEqualTo(PresentationSlideGenerationState.GENERATED);
        assertThat(slideRepository.findById(blankSlideId).orElseThrow().getGenerationState())
                .isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(deckRepository.findById(deckId).orElseThrow().getVersion()).isEqualTo(deckVersion);
        assertThat(slideRepository.findById(firstSlideId).orElseThrow().getVersion()).isEqualTo(firstSlideVersion);
        assertThat(slideRepository.findById(blankSlideId).orElseThrow().getVersion()).isEqualTo(blankSlideVersion);
        List<PresentationSlideElement> reloadedElements = elementRepository
            .findBySlideIdOrderByZIndexAscIdAsc(firstSlideId);
        assertThat(reloadedElements).hasSize(elementPayloads.size());
        assertThat(reloadedElements).extracting(PresentationSlideElement::getPayloadJson)
            .containsExactlyElementsOf(elementPayloads);
        assertThat(reloadedElements).extracting(PresentationSlideElement::getStyleJson)
            .containsExactlyElementsOf(elementStyles);
        assertThat(reloadedElements).extracting(PresentationSlideElement::getVersion)
            .containsExactlyElementsOf(elementVersions);

        assertThatThrownBy(() -> exportService.export(owner, foreignDeck.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        transactionTemplate.executeWithoutResult(status -> {
            PresentationSlideElement text = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(firstSlideId).stream()
                    .filter(element -> element.getElementType() == PresentationElementType.TEXT)
                    .findFirst().orElseThrow();
            text.setPayloadJson("{\"unknown\":\"corrupt-schema\"}");
            elementRepository.saveAndFlush(text);
        });
        assertThatThrownBy(() -> exportService.export(owner, deckId))
                .isInstanceOf(PresentationExportStateInvalidException.class);
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
        notebook.setTitle("PPTX export fixture " + UUID.randomUUID());
        notebook = notebookRepository.saveAndFlush(notebook);
        notebookIds.add(notebook.getId());
        return notebook;
    }

    private PresentationDeck createDeck(User user, Notebook notebook, String title) {
        PresentationDeck deck = new PresentationDeck();
        deck.setOwner(user);
        deck.setNotebook(notebook);
        deck.setTitle(title + " " + UUID.randomUUID());
        deck.setStatus(PresentationDeckStatus.DRAFT);
        deck.setAspectRatio("16:9");
        deck.setThemeKey("DEFAULT");
        deck.setCreatedAt(LocalDateTime.now(clock));
        deck.setUpdatedAt(LocalDateTime.now(clock));
        deck = deckRepository.saveAndFlush(deck);
        deckIds.add(deck.getId());
        return deck;
    }

    private PresentationOutlineRevision createApprovedOutline(PresentationDeck deck, User creator) {
        PresentationOutlineRevision revision = new PresentationOutlineRevision();
        revision.setDeckId(deck.getId());
        revision.setRevisionNumber(1);
        revision.setStatus(PresentationOutlineStatus.APPROVED);
        revision.setGenerationScopeSnapshot("{}");
        revision.setOutlineJson("{}");
        revision.setContentHash("hash-" + UUID.randomUUID());
        revision.setCreatedBy(creator);
        revision.setCreatedAt(LocalDateTime.now(clock));
        revision.setApprovedAt(LocalDateTime.now(clock));
        revision = outlineRepository.saveAndFlush(revision);
        outlineIds.add(revision.getId());
        return revision;
    }

    private PresentationSlide createSlide(PresentationDeck deck, PresentationOutlineRevision revision, int order,
                                          String title, String notes, PresentationSlideGenerationState state) {
        PresentationSlide slide = new PresentationSlide();
        slide.setDeckId(deck.getId());
        slide.setSlideOrder(order);
        slide.setTitle(title);
        slide.setLayoutType(PresentationSlideLayout.BLANK);
        slide.setSpeakerNotes(notes);
        slide.setGenerationState(state);
        slide.setGeneratedFromOutlineRevisionId(revision.getId());
        slide.setGenerationKey("export-" + UUID.randomUUID());
        slide.setCreatedAt(LocalDateTime.now(clock));
        slide.setUpdatedAt(LocalDateTime.now(clock));
        slide = slideRepository.saveAndFlush(slide);
        slideIds.add(slide.getId());
        return slide;
    }

    private PresentationSlideSource createSource(PresentationSlide slide) {
        Document document = new Document();
        document.setUser(owner);
        document.setNotebook(notebook);
        document.setFileName("export-source.pdf");
        document.setFileSize(100L);
        document.setFileType("PDF");
        document.setFilePath("fixture-only-source");
        document.setStatus("READY");
        document.setActiveIndexVersion("pptx-export-v1");
        document = documentRepository.saveAndFlush(document);
        documentIds.add(document.getId());

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(1);
        chunk.setContent("PPTX source excerpt");
        chunk.setTokenCount(4);
        chunk.setIndexVersion("pptx-export-v1");
        chunk = chunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());

        PresentationSlideSource row = new PresentationSlideSource();
        row.setSlide(slide);
        row.setDocument(document);
        row.setDocumentChunk(chunk);
        row.setSourceOrder(1);
        row.setEvidenceExcerpt("PPTX source excerpt");
        row.setEvidenceHash("source-hash");
        row.setPageStart(1);
        row.setPageEnd(1);
        row.setSectionTitle("Export fixture source");
        row.setSourceAlias("S1");
        row.setCreatedAt(LocalDateTime.now(clock));
        row = sourceRepository.saveAndFlush(row);
        sourceIds.add(row.getId());
        return row;
    }

    private PresentationMediaAsset createMedia(PresentationDeck deck, User mediaOwner,
                                                String mimeType, byte[] bytes, String filename) {
        MediaStorage.StoredObject stored;
        try {
            stored = mediaStorage.store(new ByteArrayInputStream(bytes), 10L * 1024 * 1024);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create fixture media", ex);
        }
        mediaKeys.add(stored.key());
        PresentationMediaAsset media = new PresentationMediaAsset();
        media.setOwner(mediaOwner);
        media.setDeckId(deck.getId());
        media.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
        media.setOriginalFileName(filename);
        media.setStoredFileKey(stored.key());
        media.setMimeType(mimeType);
        media.setSizeBytes(stored.sizeBytes());
        media.setSha256(UUID.randomUUID().toString().replace("-", ""));
        media.setWidth(3);
        media.setHeight(2);
        media.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
        media.setCreatedAt(LocalDateTime.now(clock));
        media = mediaRepository.saveAndFlush(media);
        mediaIds.add(media.getId());
        return media;
    }

    private void createTextElement(PresentationDeck deck, PresentationSlide slide, int zIndex,
                                   String payload, String style) {
        PresentationSlideElement element = createElement(deck, slide, PresentationElementType.TEXT, zIndex);
        element.setPayloadJson(payload);
        element.setStyleJson(style);
        PresentationSlideElement persisted = elementRepository.saveAndFlush(element);
        elementIds.add(Objects.requireNonNull(persisted.getId(), "persisted element id"));
    }

    private void createShapeElement(PresentationDeck deck, PresentationSlide slide, int zIndex) {
        PresentationSlideElement element = createElement(deck, slide, PresentationElementType.SHAPE, zIndex);
        element.setPayloadJson("{\"primitive\":\"RECTANGLE\",\"text\":\"inside shape\"}");
        element.setStyleJson("{\"fillColor\":\"#00AA55\",\"strokeColor\":\"#000000\",\"strokeWidthPt\":2}");
        PresentationSlideElement persisted = elementRepository.saveAndFlush(element);
        elementIds.add(Objects.requireNonNull(persisted.getId(), "persisted element id"));
    }

    private void createImageElement(PresentationDeck deck, PresentationSlide slide, int zIndex,
                                    PresentationMediaAsset media) {
        PresentationSlideElement element = createElement(deck, slide, PresentationElementType.IMAGE, zIndex);
        element.setMediaAssetId(media.getId());
        element.setPayloadJson("{\"alt\":\"persisted image\"}");
        element.setStyleJson("{}");
        PresentationSlideElement persisted = elementRepository.saveAndFlush(element);
        elementIds.add(Objects.requireNonNull(persisted.getId(), "persisted element id"));
    }

    private PresentationSlideElement createElement(PresentationDeck deck, PresentationSlide slide,
                                                    PresentationElementType type, int zIndex) {
        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deck.getId());
        element.setSlideId(slide.getId());
        element.setElementType(type);
        element.setZIndex(zIndex);
        element.setX(new java.math.BigDecimal("10.00"));
        element.setY(new java.math.BigDecimal("10.00"));
        element.setWidth(new java.math.BigDecimal("100.00"));
        element.setHeight(new java.math.BigDecimal("50.00"));
        element.setRotation(java.math.BigDecimal.ZERO);
        element.setCreatedAt(LocalDateTime.now(clock));
        element.setUpdatedAt(LocalDateTime.now(clock));
        return element;
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("pptx-export-media-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) throw new IOException("Image writer unavailable");
        image.flush();
        return output.toByteArray();
    }
}
