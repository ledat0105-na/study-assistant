package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.controller.PresentationSlideEditorController;
import com.example.studyassistant.domain.presentation.controller.TeacherPresentationController;
import com.example.studyassistant.domain.presentation.dto.PresentationDeckCreateCommand;
import com.example.studyassistant.domain.presentation.dto.PresentationDeckDto;
import com.example.studyassistant.domain.presentation.dto.PresentationGenerationJobDto;
import com.example.studyassistant.domain.presentation.dto.PresentationMediaAssetDto;
import com.example.studyassistant.domain.presentation.dto.PresentationOutlineGenerationCommand;
import com.example.studyassistant.domain.presentation.dto.SlideElementCreateRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementDeleteRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementReadDto;
import com.example.studyassistant.domain.presentation.dto.SlideGenerationCommand;
import com.example.studyassistant.domain.presentation.dto.SlidePatchRequest;
import com.example.studyassistant.domain.presentation.dto.SlideRetryCommand;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportService;
import com.example.studyassistant.domain.presentation.generation.PresentationOutlineGenerationService;
import com.example.studyassistant.domain.presentation.generation.PresentationSlideGenerationService;
import com.example.studyassistant.domain.presentation.media.MediaStorage;
import com.example.studyassistant.domain.presentation.media.PresentationMediaService;
import com.example.studyassistant.domain.presentation.service.PresentationService;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.security.TeacherAuthorizationService;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.entity.UserDailyUsage;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.PlanFeatureRepository;
import com.example.studyassistant.repository.UserDailyUsageRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.service.RetrievalService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresentationTeacherModeE2EMySQLIntegrationTest {

    private static final Path MEDIA_ROOT = createMediaRoot();

    private static final String OUTLINE_JSON =
            "{\"schemaVersion\":1,\"title\":\"Teacher Mode Foundations\",\"learningObjective\":\"Explain the core flow\",\"audience\":\"Teachers\",\"language\":\"EN\",\"sections\":[{\"title\":\"Section 1\",\"purpose\":\"Introduction\",\"slides\":[{\"title\":\"Slide 1\",\"purpose\":\"Explain the concept\",\"slideType\":\"CONTENT\",\"keyPoints\":[\"The concept matters\"],\"sourceRefs\":[\"S1\"]},{\"title\":\"Slide 2\",\"purpose\":\"Summarize\",\"slideType\":\"SUMMARY\",\"keyPoints\":[\"Summarize the lesson\"],\"sourceRefs\":[\"S1\"]},{\"title\":\"Slide 3\",\"purpose\":\"Wrap up\",\"slideType\":\"SUMMARY\",\"keyPoints\":[\"Close the lesson\"],\"sourceRefs\":[\"S1\"]}]}]}";

    @DynamicPropertySource
    static void mediaStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("presentation.media.storage-root", () -> MEDIA_ROOT.toString());
    }

    @Autowired private PresentationService presentationService;
    @Autowired private PresentationOutlineGenerationService outlineGenerationService;
    @Autowired private PresentationSlideGenerationService slideGenerationService;
    @Autowired private PresentationSlideEditorService slideEditorService;
    @Autowired private PresentationPptxExportService exportService;
    @Autowired private PresentationMediaService mediaService;
    @Autowired private PresentationDeckRepository deckRepository;
    @Autowired private PresentationSlideRepository slideRepository;
    @Autowired private PresentationSlideElementRepository elementRepository;
    @Autowired private PresentationSlideSourceRepository sourceRepository;
    @Autowired private PresentationGenerationJobRepository jobRepository;
    @Autowired private PresentationOutlineRevisionRepository outlineRepository;
    @Autowired private PresentationMediaAssetRepository mediaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private PlanFeatureRepository planFeatureRepository;
    @Autowired private UserDailyUsageRepository userDailyUsageRepository;
    @Autowired private MediaStorage mediaStorage;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;

    @MockitoBean private RetrievalService retrievalService;
    @MockitoBean private LlmProvider llmProvider;

    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<LocalDate> usageDates = new LinkedHashSet<>();
    private final Set<Long> notebookIds = new LinkedHashSet<>();
    private final Set<Long> documentIds = new LinkedHashSet<>();
    private final Set<Long> chunkIds = new LinkedHashSet<>();
    private final Set<Long> deckIds = new LinkedHashSet<>();
    private final Set<Long> outlineIds = new LinkedHashSet<>();
    private final Set<Long> slideIds = new LinkedHashSet<>();
    private final Set<Long> jobIds = new LinkedHashSet<>();
    private final Set<Long> mediaIds = new LinkedHashSet<>();
    private final Set<Long> planFeatureIds = new LinkedHashSet<>();
    private final Set<String> mediaKeys = new HashSet<>();

    private User teacher;
    private User student;
    private User foreignTeacher;
    private Notebook notebook;
    private Document document;
    private DocumentChunk chunk;

    @BeforeEach
    void setUp() {
        usageDates.add(LocalDate.now(clock));
        transactionTemplate.executeWithoutResult(status -> {
            String planSuffix = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
            String teacherPlan = "T19_E2E_PREMIUM_" + planSuffix;
            String freePlan = "T19_E2E_FREE_" + planSuffix;
            ensurePlanFeature(teacherPlan, 100L * 1024 * 1024);
            ensurePlanFeature(freePlan, 100L * 1024 * 1024);
            teacher = createUser("teacher_e2e", "TEACHER", teacherPlan);
            student = createUser("student_e2e", "STUDENT", freePlan);
            foreignTeacher = createUser("foreign_teacher_e2e", "TEACHER", teacherPlan);
            notebook = createNotebook(teacher, "E2E notebook");
            document = createDocument(teacher, notebook, "notes.pdf");
            chunk = createChunk(document, "Evidence for teacher mode with citations and notes.", 1, 2, "Introduction", "e2e-v1");
        });

        when(retrievalService.search(anyLong(), anyLong(), anyString(), anyList()))
                .thenReturn(RetrievalResult.builder()
                        .hasSufficientEvidence(true)
                        .totalTokens(32)
                        .chunks(List.of(RetrievedChunk.builder()
                                .chunkId(chunk.getId())
                                .documentId(document.getId())
                                .documentTitle(document.getFileName())
                                .chunkIndex(0)
                                .content(chunk.getContent())
                                .pageStart(1)
                                .pageEnd(2)
                                .sectionTitle("Introduction")
                                .tokenCount(7)
                                .indexVersion("e2e-v1")
                                .build()))
                        .build());

        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn(
                        OUTLINE_JSON,
                        "{\"title\":\"Generated slide title\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"Generated speaker notes\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Teacher mode summary\"},\"style\":{\"fontSizePt\":22,\"color\":\"#123456\"},\"x\":10,\"y\":20,\"width\":380,\"height\":60}],\"sourceRefs\":[\"S1\"]}",
                        "{\"title\":\"Generated slide title\",\"layout\":\"TITLE_CONTENT\",\"speakerNotes\":\"Generated speaker notes\",\"elements\":[{\"type\":\"TEXT\",\"payload\":{\"text\":\"Teacher mode summary\"},\"style\":{\"fontSizePt\":22,\"color\":\"#123456\"},\"x\":10,\"y\":20,\"width\":380,\"height\":60}],\"sourceRefs\":[\"S1\"]}");
    }

    @AfterEach
    void cleanup() throws IOException {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long slideId : slideIds) {
                elementRepository.deleteBySlideId(slideId);
                sourceRepository.deleteBySlideId(slideId);
                slideRepository.findById(slideId).ifPresent(slideRepository::delete);
            }
            slideRepository.flush();
            for (Long jobId : jobIds) {
                jobRepository.findById(jobId).ifPresent(jobRepository::delete);
            }
            jobRepository.flush();
            for (Long deckId : deckIds) {
                PresentationDeck deck = deckRepository.findById(deckId).orElseThrow();
                List<PresentationGenerationJob> deckJobs = findJobsForDeck(deck.getOwner().getId(), deckId);
                assertThat(deckJobs)
                        .extracting(PresentationGenerationJob::getId)
                        .allMatch(jobIds::contains);
                assertThat(deckJobs).isEmpty();
                deckRepository.findById(deckId).ifPresent(fixtureDeck -> {
                    fixtureDeck.setApprovedOutlineRevision(null);
                    deckRepository.saveAndFlush(fixtureDeck);
                });
                deckRepository.flush();
                for (Long outlineId : outlineIds) {
                    assertThat(findJobsForDeck(deck.getOwner().getId(), deckId))
                            .noneMatch(job -> Objects.equals(job.getOutlineRevisionId(), outlineId));
                }
            }
            for (Long deckId : deckIds) {
                List<Long> actualOutlineIds = outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deckId)
                        .stream().map(PresentationOutlineRevision::getId).toList();
                assertThat(actualOutlineIds).allMatch(outlineIds::contains);
            }
            for (Long outlineId : outlineIds) {
                outlineRepository.findById(outlineId).ifPresent(outlineRepository::delete);
            }
            outlineRepository.flush();
            for (Long deckId : deckIds) {
                assertThat(outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deckId)).isEmpty();
            }
            for (Long mediaId : mediaIds) {
                mediaRepository.findById(mediaId).ifPresent(asset -> {
                    mediaRepository.delete(asset);
                });
            }
            mediaRepository.flush();
            for (Long deckId : deckIds) {
                deckRepository.findById(deckId).ifPresent(deckRepository::delete);
            }
            deckRepository.flush();
            for (Long chunkId : chunkIds) {
                chunkRepository.findById(chunkId).ifPresent(chunkRepository::delete);
            }
            chunkRepository.flush();
            for (Long documentId : documentIds) {
                documentRepository.findById(documentId).ifPresent(documentRepository::delete);
            }
            documentRepository.flush();
            for (Long notebookId : notebookIds) {
                notebookRepository.findById(notebookId).ifPresent(notebookRepository::delete);
            }
            notebookRepository.flush();
            for (Long userId : userIds) {
                for (LocalDate usageDate : usageDates) {
                    userDailyUsageRepository.findByUserIdAndUsageDate(userId, usageDate)
                            .ifPresent(userDailyUsageRepository::delete);
                }
            }
            userDailyUsageRepository.flush();
            for (Long planFeatureId : planFeatureIds) {
                planFeatureRepository.findById(planFeatureId).ifPresent(planFeatureRepository::delete);
            }
            planFeatureRepository.flush();
            for (Long userId : userIds) {
                userRepository.findById(userId).ifPresent(userRepository::delete);
            }
            userRepository.flush();
        });

        for (String key : mediaKeys) {
            mediaStorage.delete(key);
        }

        userIds.clear();
        usageDates.clear();
        notebookIds.clear();
        documentIds.clear();
        chunkIds.clear();
        deckIds.clear();
        outlineIds.clear();
        slideIds.clear();
        jobIds.clear();
        mediaIds.clear();
        planFeatureIds.clear();
        mediaKeys.clear();
    }

    @Test
    void e2eTeacherLifecycleToPptx() throws Exception {
        PresentationDeck deck = createDeck(teacher, notebook, "Teacher Lifecycle Deck");

        usageDates.add(LocalDate.now(clock));
        PresentationGenerationJobDto outlineJob = outlineGenerationService.submitOutlineGeneration(
                teacher,
                deck.getId(),
                new PresentationOutlineGenerationCommand("outline-e2e", "Foundations", "Explain core ideas", 3, "Teachers", "EN", List.of(document.getId())));
        jobIds.add(Objects.requireNonNull(outlineJob.jobId()));
        PresentationOutlineRevision generatedRevision = waitForGeneratedOutlineRevision(deck.getId(), outlineJob.jobId());
        presentationService.approveOutline(
                teacher,
                deck.getId(),
                generatedRevision.getId(),
                deckRepository.findById(deck.getId()).orElseThrow().getVersion());

        PresentationOutlineRevision approvedRevision = outlineRepository.findById(generatedRevision.getId()).orElseThrow();
        PresentationDeck persistedDeck = deckRepository.findById(deck.getId()).orElseThrow();
        assertThat(approvedRevision.getStatus()).isEqualTo(PresentationOutlineStatus.APPROVED);
        assertThat(persistedDeck.getApprovedOutlineRevision()).isNotNull();
        assertThat(persistedDeck.getApprovedOutlineRevision().getId()).isEqualTo(approvedRevision.getId());

        PresentationGenerationJob slideJob = slideGenerationService.submitFullGeneration(
                teacher,
                deck.getId(),
                new SlideGenerationCommand("slide-e2e"));
        jobIds.add(Objects.requireNonNull(slideJob.getId()));
        waitForGenerationJobSucceeded(slideJob.getId());

        List<PresentationSlide> generatedSlides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());
        slideIds.addAll(generatedSlides.stream().map(PresentationSlide::getId).toList());
        assertThat(generatedSlides).hasSize(3);
        assertThat(generatedSlides).extracting(PresentationSlide::getGenerationState)
            .containsOnly(PresentationSlideGenerationState.GENERATED);
        assertThat(generatedSlides).allSatisfy(generatedSlide -> {
            List<PresentationSlideSource> sources = sourceRepository.findBySlideIdOrderBySourceOrderAsc(generatedSlide.getId());
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).getDocument().getId()).isEqualTo(document.getId());
            assertThat(sources.get(0).getDocumentChunk().getId()).isEqualTo(chunk.getId());
            assertThat(sources.get(0).getEvidenceExcerpt()).isEqualTo(chunk.getContent());
        });

        PresentationSlide slide = generatedSlides.get(0);
        List<SourceSnapshot> sourcesBeforeEdit = sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId())
            .stream().map(this::snapshotSource).toList();
        Long originalSlideVersion = slide.getVersion();
        slideEditorService.patchSlide(
                teacher,
                deck.getId(),
                slide.getId(),
            new SlidePatchRequest("Edited title", "Edited notes for export", originalSlideVersion));
        PresentationSlide updatedSlide = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(updatedSlide.getGenerationState()).isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(updatedSlide.getTitle()).isEqualTo("Edited title");
        assertThat(updatedSlide.getSpeakerNotes()).isEqualTo("Edited notes for export");
        assertThat(updatedSlide.getVersion()).isGreaterThan(originalSlideVersion);
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId()).stream()
            .map(this::snapshotSource).toList()).containsExactlyElementsOf(sourcesBeforeEdit);

        PresentationMediaAssetDto uploaded = mediaService.upload(
                teacher,
                deck.getId(),
                new MockMultipartFile("file", "teacher-mode.png", "image/png", pngBytes()));
        mediaIds.add(uploaded.id());
        mediaRepository.findById(uploaded.id()).ifPresent(asset -> mediaKeys.add(asset.getStoredFileKey()));

        SlideElementCreateRequest imageRequest = new SlideElementCreateRequest(
                "IMAGE",
                updatedSlide.getVersion(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("180.00"),
                new BigDecimal("120.00"),
                BigDecimal.ZERO,
                uploaded.id(),
                Map.of("alt", "teacher-mode-image"),
                Map.of(),
                0
        );
        slideEditorService.addElement(teacher, deck.getId(), slide.getId(), imageRequest);

        persistedDeck = deckRepository.findById(deck.getId()).orElseThrow();
        assertThat(persistedDeck.getStatus()).isNotEqualTo(PresentationDeckStatus.ARCHIVED);

        try (var artifact = exportService.export(teacher, deck.getId());
             var input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides()).hasSizeGreaterThan(0);
            assertThat(reopened.getPageSize().getWidth()).isEqualTo(960.0);
            assertThat(reopened.getPageSize().getHeight()).isEqualTo(540.0);
            assertThat(reopened.getSlides().get(0).getShapes()).isNotEmpty();
        }

        assertThat(slideRepository.findById(slide.getId()).orElseThrow().getGenerationState())
                .isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId())).isNotEmpty();
    }

    @Test
    void e2eGeneratedSourcesSurviveManualEdit() {
        PresentationDeck deck = createDeck(teacher, notebook, "Source Survival Deck");
        PresentationOutlineRevision revision = createApprovedOutlineViaProductionLifecycle(deck);
        assertThat(revision.getStatus()).isEqualTo(PresentationOutlineStatus.APPROVED);

        PresentationSlide slide = createGeneratedSlide(deck, revision, 1, "Initial title");
        slideIds.add(slide.getId());
        PresentationSlideSource source = createSource(slide, document, chunk, 1);
        sourceRepository.saveAndFlush(source);

        slideEditorService.patchSlide(teacher, deck.getId(), slide.getId(),
                new SlidePatchRequest("Manually updated title", "Updated talk track", slide.getVersion()));

        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId())).hasSize(1);
        assertThat(sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId()).get(0).getEvidenceExcerpt())
                .isEqualTo(chunk.getContent());
    }

    @Test
    void e2eMediaUploadToImagePptx() throws Exception {
        PresentationDeck deck = createDeck(teacher, notebook, "Image Export Deck");
        PresentationOutlineRevision revision = createApprovedOutlineViaProductionLifecycle(deck);
        PresentationSlide slide = createGeneratedSlide(deck, revision, 1, "Image slide");
        slideIds.add(slide.getId());

        PresentationMediaAssetDto asset = mediaService.upload(
                teacher,
                deck.getId(),
                new MockMultipartFile("file", "pptx-image.png", "image/png", pngBytes()));
        mediaIds.add(asset.id());
        mediaRepository.findById(asset.id()).ifPresent(media -> mediaKeys.add(media.getStoredFileKey()));

        slideEditorService.addElement(teacher, deck.getId(), slide.getId(),
                new SlideElementCreateRequest(
                        "IMAGE",
                        slide.getVersion(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("120.00"),
                        new BigDecimal("80.00"),
                        BigDecimal.ZERO,
                        asset.id(),
                        Map.of("alt", "visual"),
                        Map.of(),
                        0));

        try (var artifact = exportService.export(teacher, deck.getId());
             var input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getPictureData()).isNotEmpty();
        }
    }

    @Test
    void e2eEditedSlideProtectedFromAiRetry() {
        PresentationDeck deck = createDeck(teacher, notebook, "Edited Retry Protection Deck");
        PresentationOutlineRevision revision = createApprovedOutlineViaProductionLifecycle(deck);
        List<PresentationSlide> generatedSlides = generateSlidesViaProductionLifecycle(deck, "edited-protection");

        PresentationSlide target = generatedSlides.get(0);
        slideEditorService.patchSlide(teacher, deck.getId(), target.getId(),
            new SlidePatchRequest("Manual retry-protected title", "Manual retry-protected notes", target.getVersion()));
        PresentationSlide edited = slideRepository.findById(target.getId()).orElseThrow();
        SlideElementReadDto manualElement = addTextElement(deck, edited, "Manual element survives retry");
        List<String> elementPayloadsBeforeRetry = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(target.getId())
            .stream().map(PresentationSlideElement::getPayloadJson).toList();

        PresentationGenerationJob fullGenerationReplay = slideGenerationService.submitFullGeneration(
            teacher, deck.getId(), new SlideGenerationCommand("edited-protection-full-replay"));
        jobIds.add(Objects.requireNonNull(fullGenerationReplay.getId()));
        waitForGenerationJobSucceeded(fullGenerationReplay.getId());
        assertThat(fullGenerationReplay.getProgressTotal()).isZero();

        assertThatThrownBy(() -> slideGenerationService.submitSlideRetry(
            teacher,
            deck.getId(),
            target.getId(),
            new SlideRetryCommand("edited-protection-retry", revision.getId(), revision.getContentHash())))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        PresentationSlide persistedEdited = slideRepository.findById(target.getId()).orElseThrow();
        assertThat(persistedEdited.getGenerationState()).isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(persistedEdited.getTitle()).isEqualTo("Manual retry-protected title");
        assertThat(persistedEdited.getSpeakerNotes()).isEqualTo("Manual retry-protected notes");
        assertThat(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(target.getId())
            .stream().map(PresentationSlideElement::getPayloadJson).toList())
            .containsExactlyElementsOf(elementPayloadsBeforeRetry);
        assertThat(elementRepository.findById(manualElement.id()).orElseThrow().getPayloadJson())
            .contains("Manual element survives retry");
        assertThat(generatedSlides.subList(1, generatedSlides.size()))
            .allSatisfy(slide -> assertThat(slideRepository.findById(slide.getId()).orElseThrow().getGenerationState())
                .isEqualTo(PresentationSlideGenerationState.GENERATED));
        assertThat(jobRepository.findByOwnerIdAndDeckIdAndJobType(teacher.getId(), deck.getId(), PresentationJobType.SLIDE_RETRY))
            .isEmpty();
    }

    @Test
    void e2eArchivedReadExportAllowedMutationsRejected() throws Exception {
        PresentationDeck deck = createDeck(teacher, notebook, "Archived Read Export Deck");
        createApprovedOutlineViaProductionLifecycle(deck);
        List<PresentationSlide> slides = generateSlidesViaProductionLifecycle(deck, "archived-read-export");
        PresentationSlide slide = slides.get(0);
        slideEditorService.patchSlide(teacher, deck.getId(), slide.getId(),
            new SlidePatchRequest("Archived readable title", "Archived readable notes", slide.getVersion()));
        PresentationSlide edited = slideRepository.findById(slide.getId()).orElseThrow();
        SlideElementReadDto element = addTextElement(deck, edited, "Archived readable element");
        PresentationMediaAssetDto media = mediaService.upload(teacher, deck.getId(),
            new MockMultipartFile("file", "archived.png", "image/png", pngBytes()));
        mediaIds.add(media.id());
        mediaRepository.findById(media.id()).ifPresent(asset -> mediaKeys.add(asset.getStoredFileKey()));

        PresentationDeck currentDeck = deckRepository.findById(deck.getId()).orElseThrow();
        presentationService.archiveDeck(teacher, deck.getId(), currentDeck.getVersion());
        assertThat(deckRepository.findById(deck.getId()).orElseThrow().getStatus()).isEqualTo(PresentationDeckStatus.ARCHIVED);

        var archivedOutlines = presentationService.getOutlines(teacher, deck.getId());
        assertThat(archivedOutlines).isNotEmpty();
        assertThat(archivedOutlines.get(0).status()).isEqualTo("APPROVED");
        assertThat(archivedOutlines.get(0).contentHash()).isNotBlank();

        var readableSlide = slideEditorService.getSlide(teacher, deck.getId(), slide.getId());
        assertThat(readableSlide.title()).isEqualTo("Archived readable title");
        assertThat(readableSlide.elements()).extracting(com.example.studyassistant.domain.presentation.dto.SlideElementReadDto::id)
            .contains(element.id());
        try (var artifact = exportService.export(teacher, deck.getId());
             var input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides()).hasSize(3);
            assertThat(textIn(reopened.getSlides().get(0))).contains("Archived readable title");
        }

        assertConflict(() -> slideEditorService.patchSlide(teacher, deck.getId(), slide.getId(),
            new SlidePatchRequest("Rejected archived patch", "Rejected archived notes", edited.getVersion())));
        assertConflict(() -> addTextElement(deck, edited, "Rejected archived element"));
        assertConflict(() -> slideEditorService.deleteElement(teacher, deck.getId(), slide.getId(), element.id(),
            new SlideElementDeleteRequest(edited.getVersion(), element.version())));
        MockMultipartFile rejectedUpload = new MockMultipartFile("file", "rejected.png", "image/png", pngBytes());
        assertConflict(() -> mediaService.upload(teacher, deck.getId(), rejectedUpload));
        assertConflict(() -> mediaService.delete(teacher, deck.getId(), media.id()));

        assertThat(elementRepository.findById(element.id())).isPresent();
        assertThat(mediaRepository.findById(media.id())).isPresent();
    }

    @Test
    void e2eStaleEditorVersionRollsBack() {
        PresentationDeck deck = createDeck(teacher, notebook, "Stale Editor Version Deck");
        createApprovedOutlineViaProductionLifecycle(deck);
        PresentationSlide slide = generateSlidesViaProductionLifecycle(deck, "stale-editor-version").get(0);
        Long staleVersion = slide.getVersion();
        List<String> originalElementPayloads = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId())
            .stream().map(PresentationSlideElement::getPayloadJson).toList();

        slideEditorService.patchSlide(teacher, deck.getId(), slide.getId(),
            new SlidePatchRequest("First committed title", "First committed notes", staleVersion));
        PresentationSlide afterSuccessfulEdit = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(afterSuccessfulEdit.getVersion()).isGreaterThan(staleVersion);

        assertConflict(() -> addTextElement(deck, slide, "Must not persist with stale version"));

        PresentationSlide afterRejectedEdit = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(afterRejectedEdit.getGenerationState()).isEqualTo(PresentationSlideGenerationState.EDITED);
        assertThat(afterRejectedEdit.getTitle()).isEqualTo("First committed title");
        assertThat(afterRejectedEdit.getSpeakerNotes()).isEqualTo("First committed notes");
        assertThat(afterRejectedEdit.getVersion()).isEqualTo(afterSuccessfulEdit.getVersion());
        assertThat(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId())
            .stream().map(PresentationSlideElement::getPayloadJson).toList())
            .containsExactlyElementsOf(originalElementPayloads);
    }

    @Test
    void e2eQuotaInvariantNoDoubleCharge() {
        String validOutlineJson = """
            {"schemaVersion":1,"title":"Quota Teacher Mode","learningObjective":"Explain quota invariants","audience":"Teachers","language":"EN","sections":[{"title":"Quota section","purpose":"Explain the contract","slides":[{"title":"Quota slide one","purpose":"Record successful usage","slideType":"CONTENT","keyPoints":["Usage is persisted"],"sourceRefs":["S1"]},{"title":"Quota slide two","purpose":"Check replay","slideType":"SUMMARY","keyPoints":["Replay is idempotent"],"sourceRefs":["S1"]},{"title":"Quota slide three","purpose":"Check failure","slideType":"SUMMARY","keyPoints":["Failed processing releases reservation"],"sourceRefs":["S1"]}]}]}
            """;
        String validSlideJson = """
            {"title":"Quota generated slide","layout":"TITLE_CONTENT","speakerNotes":"Quota test notes","elements":[{"type":"TEXT","payload":{"text":"Quota test content"},"style":{"fontSizePt":22,"color":"#123456"},"x":10,"y":20,"width":380,"height":60}],"sourceRefs":["S1"]}
            """;
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            String systemPrompt = invocation.getArgument(0, String.class);
            return systemPrompt.startsWith("You are a strict educational presentation outline generator.")
                ? validOutlineJson
                : validSlideJson;
        });

        PresentationDeck successfulDeck = createDeck(teacher, notebook, "Quota Success Deck");
        createApprovedOutlineViaProductionLifecycle(successfulDeck);
        String successKey = "quota-success-" + UUID.randomUUID();
        generateSlidesViaProductionLifecycle(successfulDeck, successKey);
        UserDailyUsage afterSuccess = currentUsageForTeacher();
        assertThat(afterSuccess.getPresentationOutlineCount()).isEqualTo(1);
        assertThat(afterSuccess.getPresentationSlideCount()).isEqualTo(3);

        org.mockito.Mockito.clearInvocations(llmProvider);
        PresentationGenerationJob replay = slideGenerationService.submitFullGeneration(
            teacher, successfulDeck.getId(), new SlideGenerationCommand(successKey));
        jobIds.add(Objects.requireNonNull(replay.getId()));
        assertThat(replay.getStatus()).isEqualTo(PresentationJobStatus.SUCCEEDED);
        assertThat(replay.getProgressTotal()).isEqualTo(3);
        List<PresentationGenerationJob> successfulJobs = jobRepository.findByOwnerIdAndDeckIdAndJobType(
            teacher.getId(), successfulDeck.getId(), PresentationJobType.SLIDES);
        assertThat(successfulJobs).hasSize(1);
        assertThat(replay.getId()).isEqualTo(successfulJobs.get(0).getId());
        assertThat(currentUsageForTeacher().getPresentationSlideCount()).isEqualTo(3);
        org.mockito.Mockito.verifyNoInteractions(llmProvider);

        PresentationDeck failedDeck = createDeck(teacher, notebook, "Quota Failure Deck");
        createApprovedOutlineViaProductionLifecycle(failedDeck);
        int slideCountBeforeFailure = currentUsageForTeacher().getPresentationSlideCount();
        assertThat(currentUsageForTeacher().getPresentationOutlineCount()).isEqualTo(2);
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("not-valid-slide-json");

        usageDates.add(LocalDate.now(clock));
        PresentationGenerationJob failedJob = slideGenerationService.submitFullGeneration(
            teacher, failedDeck.getId(), new SlideGenerationCommand("quota-failed-" + UUID.randomUUID()));
        jobIds.add(Objects.requireNonNull(failedJob.getId()));
        PresentationGenerationJob terminalFailure = waitForGenerationJobStatus(failedJob.getId(), PresentationJobStatus.FAILED);
        List<PresentationSlide> failedPlaceholders = slideRepository.findByDeckIdOrderBySlideOrderAsc(failedDeck.getId());
        slideIds.addAll(failedPlaceholders.stream().map(PresentationSlide::getId).toList());

        assertThat(terminalFailure.getErrorSummary()).isNotBlank();
        assertThat(failedPlaceholders).hasSize(3);
        assertThat(failedPlaceholders.get(0).getGenerationState()).isEqualTo(PresentationSlideGenerationState.FAILED);
        assertThat(currentUsageForTeacher().getPresentationSlideCount()).isEqualTo(slideCountBeforeFailure);
    }

    @Test
    void e2ePptxReopenPreservesStructureAndNotes() throws Exception {
        PresentationDeck deck = createDeck(teacher, notebook, "PPTX Round Trip Deck");
        createApprovedOutlineViaProductionLifecycle(deck);
        PresentationSlide slide = generateSlidesViaProductionLifecycle(deck, "pptx-round-trip").get(0);
        slideEditorService.patchSlide(teacher, deck.getId(), slide.getId(),
            new SlidePatchRequest("Round-trip edited title", "Round-trip presenter notes", slide.getVersion()));
        PresentationSlide edited = slideRepository.findById(slide.getId()).orElseThrow();
        PresentationMediaAssetDto media = mediaService.upload(teacher, deck.getId(),
            new MockMultipartFile("file", "round-trip.png", "image/png", pngBytes()));
        mediaIds.add(media.id());
        mediaRepository.findById(media.id()).ifPresent(asset -> mediaKeys.add(asset.getStoredFileKey()));
        slideEditorService.addElement(teacher, deck.getId(), slide.getId(),
            new SlideElementCreateRequest("IMAGE", edited.getVersion(), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("120.00"), new BigDecimal("80.00"), BigDecimal.ZERO, media.id(),
                Map.of("alt", "round-trip visual"), Map.of(), 5));

        try (var artifact = exportService.export(teacher, deck.getId());
             var input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides()).hasSize(3);
            var reopenedSlide = reopened.getSlides().get(0);
            assertThat(textIn(reopenedSlide)).contains("Round-trip edited title", "Teacher mode summary");
            assertThat(reopenedSlide.getShapes()).filteredOn(XSLFPictureShape.class::isInstance).hasSize(1);
            String reopenedNotes = reopened.getNotesSlide(reopenedSlide).getTextParagraphs().stream()
                .flatMap(List::stream)
                .map(org.apache.poi.xslf.usermodel.XSLFTextParagraph::getText)
                .reduce("", String::concat);
            assertThat(reopenedNotes).contains("Round-trip presenter notes");
    }
        }

    @Test
    void e2eStudentForbidden() throws Exception {
        PresentationDeck deck = createDeck(teacher, notebook, "Student Forbidden Deck");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PresentationSlideEditorController(new TeacherAuthorizationService(userRepository), slideEditorService))
                .setControllerAdvice(new PresentationEditorExceptionHandler())
                .build();

        MockHttpSession studentSession = new MockHttpSession();
        studentSession.setAttribute("userId", student.getId());

        mockMvc.perform(get("/api/teacher/presentations/{deckId}/slides/{slideId}", deck.getId(), 999L)
                        .session(studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void e2eForeignTeacherIsolation() {
        PresentationDeck deck = createDeck(teacher, notebook, "Foreign Deck");
        assertThatThrownBy(() -> slideEditorService.getSlide(foreignTeacher, deck.getId(), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void e2eOutlineListAndJobContractExposeRealRevisions() {
        PresentationDeck deck = createDeck(teacher, notebook, "Outline Contract Deck");
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(OUTLINE_JSON);
        usageDates.add(LocalDate.now(clock));

        PresentationGenerationJobDto firstJob = outlineGenerationService.submitOutlineGeneration(teacher, deck.getId(),
                new PresentationOutlineGenerationCommand("outline-contract-1-" + UUID.randomUUID(),
                        "Foundations", "First pass", 3, "Teachers", "EN", List.of(document.getId())));
        jobIds.add(Objects.requireNonNull(firstJob.jobId()));
        PresentationOutlineRevision first = waitForGeneratedOutlineRevision(deck.getId(), firstJob.jobId());

        PresentationGenerationJobDto secondJob = outlineGenerationService.submitOutlineGeneration(teacher, deck.getId(),
                new PresentationOutlineGenerationCommand("outline-contract-2-" + UUID.randomUUID(),
                        "Foundations", "Second pass", 3, "Teachers", "EN", List.of(document.getId())));
        jobIds.add(Objects.requireNonNull(secondJob.jobId()));
        PresentationOutlineRevision second = waitForGeneratedOutlineRevision(deck.getId(), secondJob.jobId());
        assertThat(second.getId()).isNotEqualTo(first.getId());

        var outlines = presentationService.getOutlines(teacher, deck.getId());
        assertThat(outlines).extracting(com.example.studyassistant.domain.presentation.dto.PresentationOutlineRevisionDto::id)
                .containsExactly(second.getId(), first.getId());
        assertThat(outlines).extracting(com.example.studyassistant.domain.presentation.dto.PresentationOutlineRevisionDto::revisionNumber)
                .containsExactly(second.getRevisionNumber(), first.getRevisionNumber());
        assertThat(outlines.get(0).revisionNumber()).isGreaterThan(outlines.get(1).revisionNumber());
        PresentationOutlineRevision persistedSecond = outlineRepository.findById(second.getId()).orElseThrow();
        PresentationOutlineRevision persistedFirst = outlineRepository.findById(first.getId()).orElseThrow();
        assertThat(outlines.get(0).contentHash()).isNotBlank().isEqualTo(persistedSecond.getContentHash());
        assertThat(outlines.get(1).contentHash()).isNotBlank().isEqualTo(persistedFirst.getContentHash());
        assertThat(outlines.get(0).outlineJson()).isEqualTo(persistedSecond.getOutlineJson());
        assertThat(outlines.get(0).createdBy()).isEqualTo(teacher.getId());
        assertThat(outlines).extracting(com.example.studyassistant.domain.presentation.dto.PresentationOutlineRevisionDto::status)
                .containsOnly("DRAFT");

        PresentationGenerationJobDto firstJobDto = presentationService.getJob(teacher, deck.getId(), firstJob.jobId());
        PresentationGenerationJobDto secondJobDto = presentationService.getJob(teacher, deck.getId(), secondJob.jobId());
        assertThat(firstJobDto.status()).isEqualTo("SUCCEEDED");
        assertThat(firstJobDto.outlineRevisionId()).isEqualTo(first.getId());
        assertThat(secondJobDto.outlineRevisionId()).isEqualTo(second.getId());

        assertThatThrownBy(() -> presentationService.getOutlines(foreignTeacher, deck.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PresentationDeck createDeck(User owner, Notebook notebook, String title) {
        PresentationDeckDto dto = presentationService.createDeck(owner, new PresentationDeckCreateCommand(notebook.getId(), title, "desc", "16:9", "DEFAULT"));
        PresentationDeck deck = deckRepository.findById(dto.id()).orElseThrow();
        deckIds.add(deck.getId());
        return deck;
    }

    private List<PresentationSlide> generateSlidesViaProductionLifecycle(PresentationDeck deck, String idempotencyKey) {
        usageDates.add(LocalDate.now(clock));
        PresentationGenerationJob job = slideGenerationService.submitFullGeneration(
                teacher, deck.getId(), new SlideGenerationCommand(idempotencyKey));
        jobIds.add(Objects.requireNonNull(job.getId()));
        waitForGenerationJobSucceeded(job.getId());
        List<PresentationSlide> slides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());
        slideIds.addAll(slides.stream().map(PresentationSlide::getId).toList());
        return slides;
    }

    private SlideElementReadDto addTextElement(PresentationDeck deck, PresentationSlide slide, String text) {
        return slideEditorService.addElement(teacher, deck.getId(), slide.getId(),
                new SlideElementCreateRequest("TEXT", slide.getVersion(), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("240.00"), new BigDecimal("48.00"), BigDecimal.ZERO, null,
                        Map.of("text", text), Map.of(), 10));
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private List<String> textIn(org.apache.poi.xslf.usermodel.XSLFSlide slide) {
        return slide.getShapes().stream()
                .filter(XSLFTextShape.class::isInstance)
                .map(XSLFTextShape.class::cast)
                .map(XSLFTextShape::getText)
                .toList();
    }

    private UserDailyUsage currentUsageForTeacher() {
        LocalDate date = LocalDate.now(clock);
        usageDates.add(date);
        return userDailyUsageRepository.findByUserIdAndUsageDate(teacher.getId(), date).orElseThrow();
    }

    private PresentationOutlineRevision createApprovedOutlineViaProductionLifecycle(PresentationDeck deck) {
        usageDates.add(LocalDate.now(clock));
        PresentationGenerationJobDto outlineJob = outlineGenerationService.submitOutlineGeneration(
                teacher,
                deck.getId(),
                new PresentationOutlineGenerationCommand(
                        "outline-e2e-" + deck.getId() + "-" + UUID.randomUUID(),
                        "Foundations",
                        "Explain core ideas",
                        3,
                        "Teachers",
                        "EN",
                        List.of(document.getId())));
                jobIds.add(Objects.requireNonNull(outlineJob.jobId()));

        PresentationOutlineRevision revision = waitForGeneratedOutlineRevision(deck.getId(), outlineJob.jobId());
        presentationService.approveOutline(
                teacher,
                deck.getId(),
                revision.getId(),
                deckRepository.findById(deck.getId()).orElseThrow().getVersion());

        PresentationOutlineRevision approvedRevision = outlineRepository.findById(revision.getId()).orElseThrow();
        PresentationDeck persistedDeck = deckRepository.findById(deck.getId()).orElseThrow();
        assertThat(approvedRevision.getStatus()).isEqualTo(PresentationOutlineStatus.APPROVED);
        assertThat(persistedDeck.getApprovedOutlineRevision()).isNotNull();
        assertThat(persistedDeck.getApprovedOutlineRevision().getId()).isEqualTo(approvedRevision.getId());
        return approvedRevision;
    }

    private PresentationSlide createGeneratedSlide(PresentationDeck deck, PresentationOutlineRevision revision, int order, String title) {
        PresentationSlide slide = new PresentationSlide();
        slide.setDeckId(deck.getId());
        slide.setSlideOrder(order);
        slide.setTitle(title);
        slide.setLayoutType(PresentationSlideLayout.TITLE_CONTENT);
        slide.setGenerationState(PresentationSlideGenerationState.GENERATED);
        slide.setGenerationKey("g-" + revision.getId() + "-" + order);
        slide.setGeneratedFromOutlineRevisionId(revision.getId());
        slide.setCreatedAt(LocalDateTime.now(clock));
        slide.setUpdatedAt(LocalDateTime.now(clock));
        slide = slideRepository.saveAndFlush(slide);
        slideIds.add(slide.getId());
        return slide;
    }

    private PresentationSlideSource createSource(PresentationSlide slide, Document document, DocumentChunk chunk, int order) {
        PresentationSlideSource source = new PresentationSlideSource();
        source.setSlide(slide);
        source.setDocument(document);
        source.setDocumentChunk(chunk);
        source.setSourceOrder(order);
        source.setEvidenceExcerpt(chunk.getContent());
        source.setEvidenceHash("hash-" + order);
        source.setSourceAlias("S" + order);
        source.setPageStart(1);
        source.setPageEnd(2);
        source.setSectionTitle("Introduction");
        source.setCreatedAt(LocalDateTime.now(clock));
        return sourceRepository.saveAndFlush(source);
    }

    private PresentationOutlineRevision waitForGeneratedOutlineRevision(Long deckId, Long jobId) {
        for (int i = 0; i < 50; i++) {
            PresentationGenerationJob job = jobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == PresentationJobStatus.SUCCEEDED) {
                PresentationOutlineRevision revision = outlineRepository.findByDeckIdOrderByRevisionNumberDesc(deckId)
                        .stream().findFirst().orElseThrow();
                outlineIds.add(Objects.requireNonNull(revision.getId()));
                return revision;
            }
            if (job.getStatus() == PresentationJobStatus.FAILED) {
                throw new IllegalStateException("Outline generation job failed: " + job.getErrorSummary());
            }
            Thread.yield();
        }
        throw new IllegalStateException("Outline job did not complete in time");
    }

    private void waitForGenerationJobSucceeded(Long jobId) {
        waitForGenerationJobStatus(jobId, PresentationJobStatus.SUCCEEDED);
    }

    private PresentationGenerationJob waitForGenerationJobStatus(Long jobId, PresentationJobStatus expectedStatus) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        PresentationGenerationJob job;
        do {
            job = jobRepository.findById(Objects.requireNonNull(jobId)).orElseThrow();
            if (job.getStatus() == expectedStatus) {
                return job;
            }
            if (job.getStatus() == PresentationJobStatus.FAILED
                    || job.getStatus() == PresentationJobStatus.CANCELLED
                    || job.getStatus() == PresentationJobStatus.SUCCEEDED) {
                throw new AssertionError("Slide generation job reached " + job.getStatus()
                        + " instead of " + expectedStatus + ": " + job.getErrorSummary());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Slide generation job did not complete within 30 seconds; expected="
                + expectedStatus + ", status=" + job.getStatus() + ", failure=" + job.getErrorSummary());
    }

    private List<PresentationGenerationJob> findJobsForDeck(Long ownerId, Long deckId) {
        List<PresentationGenerationJob> jobs = new ArrayList<>();
        for (PresentationJobType type : PresentationJobType.values()) {
            jobs.addAll(jobRepository.findByOwnerIdAndDeckIdAndJobType(ownerId, deckId, type));
        }
        return jobs;
    }

    private SourceSnapshot snapshotSource(PresentationSlideSource source) {
        return new SourceSnapshot(
                source.getId(),
                source.getDocument().getId(),
                source.getDocumentChunk().getId(),
                source.getSourceOrder(),
                source.getEvidenceExcerpt(),
                source.getEvidenceHash());
    }

    private record SourceSnapshot(Long id, Long documentId, Long chunkId, Integer sourceOrder,
                                  String evidenceExcerpt, String evidenceHash) {}

    private void ensurePlanFeature(String planName, long storageBytes) {
        PlanFeature feature = planFeatureRepository.findByPlanName(planName)
                .orElseGet(() -> {
                    PlanFeature newFeature = new PlanFeature();
                    newFeature.setPlanName(planName);
                    newFeature.setMaxNotebooks(10);
                    newFeature.setDailyChatLimit(10);
                    newFeature.setDailyQuizLimit(10);
                    newFeature.setDailyFlashcardLimit(10);
                    newFeature.setMonthlyUploadLimit(10);
                    newFeature.setMaxStorageBytes(storageBytes);
                    newFeature.setChatRetentionDays(30);
                    newFeature.setMaxPresentationMediaStorageBytes(storageBytes);
                    return newFeature;
                });
        feature.setPlanName(planName);
        feature.setMaxNotebooks(10);
        feature.setDailyChatLimit(10);
        feature.setDailyQuizLimit(10);
        feature.setDailyFlashcardLimit(10);
        feature.setMonthlyUploadLimit(10);
        feature.setMaxStorageBytes(storageBytes);
        feature.setChatRetentionDays(30);
        feature.setMaxPresentationMediaStorageBytes(storageBytes);
        feature = planFeatureRepository.saveAndFlush(feature);
        planFeatureIds.add(feature.getId());
    }

    private User createUser(String username, String role, String plan) {
        User user = new User();
        user.setUsername(username + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@test.local");
        user.setPassword("pw");
        user.setRole(role);
        user.setPlan(plan);
        user.setFullName(username);
        user = userRepository.saveAndFlush(user);
        userIds.add(user.getId());
        return user;
    }

    private Notebook createNotebook(User owner, String title) {
        Notebook notebook = new Notebook();
        notebook.setUser(owner);
        notebook.setTitle(title);
        notebook = notebookRepository.saveAndFlush(notebook);
        notebookIds.add(notebook.getId());
        return notebook;
    }

    private Document createDocument(User owner, Notebook notebook, String fileName) {
        Document document = new Document();
        document.setUser(owner);
        document.setNotebook(notebook);
        document.setFileName(fileName);
        document.setFileType("PDF");
        document.setFilePath("fixture/" + fileName);
        document.setFileSize(120L);
        document.setStatus("READY");
        document.setActiveIndexVersion("e2e-v1");
        document = documentRepository.saveAndFlush(document);
        documentIds.add(document.getId());
        return document;
    }

    private DocumentChunk createChunk(Document document, String content, int pageStart, int pageEnd, String sectionTitle, String indexVersion) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setPageStart(pageStart);
        chunk.setPageEnd(pageEnd);
        chunk.setSectionTitle(sectionTitle);
        chunk.setTokenCount(9);
        chunk.setIndexVersion(indexVersion);
        chunk.setCreatedAt(LocalDateTime.now(clock));
        chunk = chunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());
        return chunk;
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("t1-9-media-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x112233);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
