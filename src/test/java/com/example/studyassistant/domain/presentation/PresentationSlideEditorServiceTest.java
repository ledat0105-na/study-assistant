package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.dto.*;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PresentationSlideEditorServiceTest {

    @Autowired
    private PresentationSlideEditorService editorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private PresentationDeckRepository deckRepository;

    @Autowired
    private PresentationSlideRepository slideRepository;

    @Autowired
    private PresentationSlideElementRepository elementRepository;

    @Autowired
    private PresentationSlideSourceRepository sourceRepository;

    @Autowired
    private PresentationOutlineRevisionRepository outlineRepository;

    @Autowired
    private PresentationMediaAssetRepository mediaAssetRepository;

    private User teacher;
    private Notebook notebook;
    private PresentationDeck deck;
    private PresentationOutlineRevision approvedRevision;
    private PresentationSlide slide;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> notebookIds = new ArrayList<>();
    private final List<Long> deckIds = new ArrayList<>();
    private final List<Long> outlineIds = new ArrayList<>();
    private final List<Long> slideIds = new ArrayList<>();
    private final List<Long> mediaIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setUsername("teacher-slide-editor-test");
        teacher.setEmail("teacher-slide-editor-test@test.com");
        teacher.setPassword("pw");
        teacher.setRole("TEACHER");
        teacher.setPlan("PREMIUM");
        teacher = userRepository.save(teacher);
        userIds.add(teacher.getId());

        notebook = new Notebook();
        notebook.setUser(teacher);
        notebook.setTitle("Notebook");
        notebook = notebookRepository.save(notebook);
        notebookIds.add(notebook.getId());

        deck = new PresentationDeck();
        deck.setOwner(teacher);
        deck.setNotebook(notebook);
        deck.setTitle("Deck");
        deck.setStatus(PresentationDeckStatus.DRAFT);
        deck.setAspectRatio("16:9");
        deck.setThemeKey("DEFAULT");
        deck.setCreatedAt(LocalDateTime.now());
        deck.setUpdatedAt(LocalDateTime.now());
        deck = deckRepository.save(deck);
        deckIds.add(deck.getId());

        approvedRevision = new PresentationOutlineRevision();
        approvedRevision.setDeckId(deck.getId());
        approvedRevision.setRevisionNumber(1);
        approvedRevision.setStatus(PresentationOutlineStatus.APPROVED);
        approvedRevision.setGenerationScopeSnapshot("{}");
        approvedRevision.setOutlineJson("{}");
        approvedRevision.setContentHash("hash-1");
        approvedRevision.setCreatedBy(teacher);
        approvedRevision.setCreatedAt(LocalDateTime.now());
        approvedRevision.setApprovedAt(LocalDateTime.now());
        approvedRevision = outlineRepository.saveAndFlush(approvedRevision);
        outlineIds.add(approvedRevision.getId());

        deck.setApprovedOutlineRevision(approvedRevision);
        deck = deckRepository.saveAndFlush(deck);

        slide = new PresentationSlide();
        slide.setDeckId(deck.getId());
        slide.setSlideOrder(1);
        slide.setTitle("Old title");
        slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
        slide.setGenerationState(PresentationSlideGenerationState.GENERATED);
        slide.setGeneratedFromOutlineRevisionId(approvedRevision.getId());
        slide.setGenerationKey("r1-s1");
        slide.setCreatedAt(LocalDateTime.now());
        slide.setUpdatedAt(LocalDateTime.now());
        slide = slideRepository.saveAndFlush(slide);
        slideIds.add(slide.getId());
    }

    @AfterEach
    void tearDown() {
        for (Long slideId : slideIds) {
            sourceRepository.findBySlideIdOrderBySourceOrderAsc(slideId).forEach(sourceRepository::delete);
            elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slideId).forEach(elementRepository::delete);
            slideRepository.findById(slideId).ifPresent(slideRepository::delete);
        }
        for (Long deckId : deckIds) {
            deckRepository.findById(deckId).ifPresent(deckEntry -> {
                deckEntry.setApprovedOutlineRevision(null);
                deckRepository.save(deckEntry);
            });
        }
        deckRepository.flush();
        mediaIds.forEach(id -> mediaAssetRepository.findById(id).ifPresent(mediaAssetRepository::delete));
        outlineIds.forEach(id -> outlineRepository.findById(id).ifPresent(outlineRepository::delete));
        deckIds.forEach(id -> deckRepository.findById(id).ifPresent(deckRepository::delete));
        notebookIds.forEach(id -> notebookRepository.findById(id).ifPresent(notebookRepository::delete));
        userIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
        userIds.clear();
        notebookIds.clear();
        deckIds.clear();
        outlineIds.clear();
        slideIds.clear();
        mediaIds.clear();
    }

    @Test
    void patchSlide_setsEditedState_andRequiresCurrentApprovedRevision() {
        SlidePatchRequest request = new SlidePatchRequest("Updated title", "Updated notes", slide.getVersion());

        SlideReadDto updated = editorService.patchSlide(teacher, deck.getId(), slide.getId(), request);

        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.generationState()).isEqualTo(PresentationSlideGenerationState.EDITED.name());

        PresentationSlide reloaded = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(reloaded.getGenerationState()).isEqualTo(PresentationSlideGenerationState.EDITED);
    }

    @Test
    void staleVersion_throwsConflict() {
        SlidePatchRequest request = new SlidePatchRequest("Bad title", "Bad notes", 999L);

        assertThatThrownBy(() -> editorService.patchSlide(teacher, deck.getId(), slide.getId(), request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void staleSlideVersion_forElementCreate_throwsConflict() {
        SlideElementCreateRequest request = new SlideElementCreateRequest(
            "TEXT",
            999L,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            null,
            Map.of("text", "hello"),
            Map.of("fill", "#ffffff"),
            0
        );

        assertThatThrownBy(() -> editorService.addElement(teacher, deck.getId(), slide.getId(), request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void staleElementVersion_throwsConflict() {
        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deck.getId());
        element.setSlideId(slide.getId());
        element.setElementType(PresentationElementType.TEXT);
        element.setZIndex(0);
        element.setX(new BigDecimal("10.00"));
        element.setY(new BigDecimal("20.00"));
        element.setWidth(new BigDecimal("100.00"));
        element.setHeight(new BigDecimal("50.00"));
        element.setRotation(BigDecimal.ZERO);
        element.setPayloadJson("{\"text\":\"hello\"}");
        element.setStyleJson("{\"fontSize\":18}");
        element.setCreatedAt(LocalDateTime.now());
        element.setUpdatedAt(LocalDateTime.now());
        element = elementRepository.saveAndFlush(element);

        SlideElementUpdateRequest request = new SlideElementUpdateRequest(
            "TEXT",
            slide.getVersion(),
            999L,
            new BigDecimal("15.00"),
            new BigDecimal("25.00"),
            new BigDecimal("110.00"),
            new BigDecimal("60.00"),
            BigDecimal.ZERO,
            null,
            Map.of("text", "updated"),
            Map.of("fontSize", 20),
            1
        );

        Long elementId = element.getId();

        assertThatThrownBy(() -> editorService.updateElement(teacher, deck.getId(), slide.getId(), elementId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void deleteElement_staleVersions_throwsConflict() {
        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deck.getId());
        element.setSlideId(slide.getId());
        element.setElementType(PresentationElementType.TEXT);
        element.setZIndex(0);
        element.setX(new BigDecimal("1.00"));
        element.setY(new BigDecimal("2.00"));
        element.setWidth(new BigDecimal("10.00"));
        element.setHeight(new BigDecimal("8.00"));
        element.setRotation(BigDecimal.ZERO);
        element.setPayloadJson("{\"text\":\"hello\"}");
        element.setStyleJson("{\"fontSize\":16}");
        element.setCreatedAt(LocalDateTime.now());
        element.setUpdatedAt(LocalDateTime.now());
        element = elementRepository.saveAndFlush(element);

        SlideElementDeleteRequest request = new SlideElementDeleteRequest(999L, element.getVersion());
        Long elementId = element.getId();

        assertThatThrownBy(() -> editorService.deleteElement(teacher, deck.getId(), slide.getId(), elementId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void failedElementMutation_rollsBackSlideAndElementState() {
        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deck.getId());
        element.setSlideId(slide.getId());
        element.setElementType(PresentationElementType.TEXT);
        element.setZIndex(0);
        element.setX(new BigDecimal("1.00"));
        element.setY(new BigDecimal("2.00"));
        element.setWidth(new BigDecimal("10.00"));
        element.setHeight(new BigDecimal("8.00"));
        element.setRotation(BigDecimal.ZERO);
        element.setPayloadJson("{\"text\":\"before\"}");
        element.setStyleJson("{\"fontSize\":16}");
        element.setCreatedAt(LocalDateTime.now());
        element.setUpdatedAt(LocalDateTime.now());
        element = elementRepository.saveAndFlush(element);

        slide.setGenerationState(PresentationSlideGenerationState.GENERATED);
        slideRepository.saveAndFlush(slide);

        PresentationSlide currentSlide = slideRepository.findById(slide.getId()).orElseThrow();
        PresentationSlideElement currentElement = elementRepository.findById(element.getId()).orElseThrow();

        Long originalVersion = currentElement.getVersion();
        PresentationSlideGenerationState originalState = slide.getGenerationState();
        Long elementId = element.getId();
        Long slideVersion = currentSlide.getVersion();

        SlideElementUpdateRequest request = new SlideElementUpdateRequest(
            "TEXT",
            currentSlide.getVersion(),
            currentElement.getVersion(),
            new BigDecimal("3.00"),
            new BigDecimal("4.00"),
            new BigDecimal("20.00"),
            new BigDecimal("12.00"),
            BigDecimal.ZERO,
            null,
            Map.of("text", "x".repeat(10000)),
            Map.of("fontSize", 18),
            1
        );

        assertThatThrownBy(() -> editorService.updateElement(teacher, deck.getId(), slide.getId(), elementId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        PresentationSlide reloadedSlide = slideRepository.findById(slide.getId()).orElseThrow();
        PresentationSlideElement reloadedElement = elementRepository.findById(element.getId()).orElseThrow();
        assertThat(reloadedSlide.getGenerationState()).isEqualTo(originalState);
        assertThat(reloadedSlide.getVersion()).isEqualTo(slideVersion);
        assertThat(reloadedElement.getPayloadJson()).contains("before");
        assertThat(reloadedElement.getPayloadJson()).doesNotContain("x".repeat(1000));
        assertThat(reloadedElement.getVersion()).isEqualTo(originalVersion);
    }

    @Test
    void imageElement_requiresSameDeckMedia() {
        User other = new User();
        other.setUsername("other-teacher");
        other.setEmail("other-teacher@test.com");
        other.setPassword("pw");
        other.setRole("TEACHER");
        other.setPlan("PREMIUM");
        other = userRepository.save(other);
        userIds.add(other.getId());

        PresentationDeck otherDeck = new PresentationDeck();
        otherDeck.setOwner(other);
        otherDeck.setNotebook(notebook);
        otherDeck.setTitle("Other deck");
        otherDeck.setStatus(PresentationDeckStatus.DRAFT);
        otherDeck.setAspectRatio("16:9");
        otherDeck.setThemeKey("DEFAULT");
        otherDeck.setCreatedAt(LocalDateTime.now());
        otherDeck.setUpdatedAt(LocalDateTime.now());
        otherDeck = deckRepository.save(otherDeck);
        deckIds.add(otherDeck.getId());

        PresentationMediaAsset media = new PresentationMediaAsset();
        media.setOwner(other);
        media.setDeckId(otherDeck.getId());
        media.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
        media.setOriginalFileName("img.png");
        media.setStoredFileKey("/tmp/img.png");
        media.setMimeType("image/png");
        media.setSizeBytes(1024L);
        media.setSha256("abcd");
        media.setWidth(100);
        media.setHeight(100);
        media.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
        media.setCreatedAt(LocalDateTime.now());
        media = mediaAssetRepository.saveAndFlush(media);
        mediaIds.add(media.getId());

        SlideElementCreateRequest request = new SlideElementCreateRequest(
            "IMAGE",
            slide.getVersion(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            media.getId(),
            Map.of(),
            Map.of(),
            0
        );

        assertThatThrownBy(() -> editorService.addElement(teacher, deck.getId(), slide.getId(), request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
