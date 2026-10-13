package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationElementType;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationSlide;
import com.example.studyassistant.domain.presentation.PresentationSlideElement;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideGenerationState;
import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import com.example.studyassistant.domain.presentation.PresentationSlideRepository;
import com.example.studyassistant.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresentationPptxExportSnapshotServiceTest {

    @Mock private PresentationDeckRepository deckRepository;
    @Mock private PresentationSlideRepository slideRepository;
    @Mock private PresentationSlideElementRepository elementRepository;
    @Mock private PresentationMediaAssetRepository mediaRepository;

    private PresentationPptxExportSnapshotService snapshotService;
    private User owner;

    @BeforeEach
    void setUp() {
        snapshotService = new PresentationPptxExportSnapshotService(deckRepository, slideRepository,
                elementRepository, mediaRepository, new ObjectMapper());
        owner = new User();
        owner.setId(7L);
    }

    @Test
    void incompleteSlidesCannotBeExported() {
        for (var state : List.of(PresentationSlideGenerationState.EMPTY, PresentationSlideGenerationState.FAILED)) {
            PresentationSlide unfinished = slide(11L, 1);
            when(unfinished.getGenerationState()).thenReturn(state);
            mockDeck(1L, List.of(unfinished), Map.of());
            assertInvalid(1L);
        }
    }

    @Test
    void malformedTextAndFractionalFontSizeAreRejected() {
        mockDeck(1L, List.of(slide(11L, 1)), Map.of(11L, List.of(
                element(101L, 1L, 11L, PresentationElementType.TEXT, "{\"other\":\"x\"}", "{}"))));
        assertInvalid(1L);

        mockDeck(2L, List.of(slide(12L, 1)), Map.of(12L, List.of(
                element(102L, 2L, 12L, PresentationElementType.TEXT,
                        "{\"text\":\"x\"}", "{\"fontSizePt\":18.5}"))));
        assertInvalid(2L);
    }

    @Test
    void slideAndPerSlideElementLimitsAreEnforced() {
        List<PresentationSlide> tooManySlides = new ArrayList<>();
        for (int index = 0; index <= PresentationPptxExportSnapshotService.MAX_SLIDES; index++) {
            tooManySlides.add(slide(1000L + index, index + 1));
        }
        mockDeck(3L, tooManySlides, Map.of());
        assertInvalid(3L);

        PresentationSlide onlySlide = slide(2000L, 1);
        List<PresentationSlideElement> tooManyElements = new ArrayList<>();
        for (int index = 0; index <= PresentationPptxExportSnapshotService.MAX_ELEMENTS_PER_SLIDE; index++) {
            tooManyElements.add(element(3000L + index, 4L, onlySlide.getId(), PresentationElementType.TEXT,
                    "{\"text\":\"ok\"}", "{}"));
        }
        mockDeck(4L, List.of(onlySlide), Map.of(onlySlide.getId(), tooManyElements));
        assertInvalid(4L);
    }

    @Test
    void invalidGeometryAndMissingImageMediaAreRejected() {
        PresentationSlide slide = slide(500L, 1);
        mockDeck(5L, List.of(slide), Map.of(slide.getId(), List.of(
                element(501L, 5L, slide.getId(), PresentationElementType.TEXT,
                        "{\"text\":\"ok\"}", "{}", new BigDecimal("-1")))));
        assertInvalid(5L);

        PresentationSlide imageSlide = slide(600L, 1);
        PresentationSlideElement image = element(601L, 6L, imageSlide.getId(), PresentationElementType.IMAGE,
                "{}", "{}");
        when(image.getMediaAssetId()).thenReturn(777L);
        mockDeck(6L, List.of(imageSlide), Map.of(imageSlide.getId(), List.of(image)));
        when(mediaRepository.findByIdAndDeckIdAndOwnerId(777L, 6L, owner.getId())).thenReturn(Optional.empty());
        assertInvalid(6L);
    }

    @Test
    void uniqueMediaByteLimitCountsEachAssetOnce() {
        PresentationSlide slide = slide(700L, 1);
        List<PresentationSlideElement> uniqueImages = new ArrayList<>();
        long tenMiB = 10L * 1024 * 1024;
        for (long mediaId = 1; mediaId <= 11; mediaId++) {
            PresentationSlideElement image = element(800L + mediaId, 7L, slide.getId(),
                    PresentationElementType.IMAGE, "{}", "{}");
            when(image.getMediaAssetId()).thenReturn(mediaId);
            uniqueImages.add(image);
            PresentationMediaAsset asset = media(mediaId, tenMiB, 7L);
            when(mediaRepository.findByIdAndDeckIdAndOwnerId(mediaId, 7L, owner.getId()))
                    .thenReturn(Optional.of(asset));
        }
        mockDeck(7L, List.of(slide), Map.of(slide.getId(), uniqueImages));
        assertInvalid(7L);

        PresentationSlide repeatedSlide = slide(701L, 1);
        List<PresentationSlideElement> repeatedImages = new ArrayList<>();
        for (long elementId = 900; elementId < 911; elementId++) {
            PresentationSlideElement image = element(elementId, 8L, repeatedSlide.getId(),
                    PresentationElementType.IMAGE, "{}", "{}");
            when(image.getMediaAssetId()).thenReturn(1L);
            repeatedImages.add(image);
        }
        PresentationMediaAsset repeatedAsset = media(1L, tenMiB, 8L);
        when(mediaRepository.findByIdAndDeckIdAndOwnerId(1L, 8L, owner.getId()))
                .thenReturn(Optional.of(repeatedAsset));
        mockDeck(8L, List.of(repeatedSlide), Map.of(repeatedSlide.getId(), repeatedImages));
        assertThat(snapshotService.capture(owner, 8L).media()).hasSize(1);
    }

    @Test
    void mediaRowThatDisagreesWithScopedDeckIsRejected() {
        PresentationSlide slide = slide(800L, 1);
        PresentationSlideElement image = element(801L, 9L, slide.getId(),
                PresentationElementType.IMAGE, "{}", "{}");
        when(image.getMediaAssetId()).thenReturn(91L);
        mockDeck(9L, List.of(slide), Map.of(slide.getId(), List.of(image)));
        PresentationMediaAsset wrongDeckMedia = media(91L, 100L, 7L);
        when(mediaRepository.findByIdAndDeckIdAndOwnerId(91L, 9L, owner.getId()))
            .thenReturn(Optional.of(wrongDeckMedia));

        assertInvalid(9L);
    }

    private void mockDeck(Long deckId, List<PresentationSlide> slides,
                          Map<Long, List<PresentationSlideElement>> elementsBySlide) {
        PresentationDeck deck = mock(PresentationDeck.class);
        lenient().when(deck.getId()).thenReturn(deckId);
        lenient().when(deck.getTitle()).thenReturn("Export fixture");
        lenient().when(deckRepository.findByIdAndOwnerId(deckId, owner.getId())).thenReturn(Optional.of(deck));
        lenient().when(slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId)).thenReturn(slides);
        for (PresentationSlide slide : slides) {
            Long slideId = slide.getId();
            List<PresentationSlideElement> elements = elementsBySlide.getOrDefault(slideId, List.of());
            lenient().when(elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slideId)).thenReturn(elements);
        }
    }

    private PresentationSlide slide(Long id, int order) {
        PresentationSlide slide = mock(PresentationSlide.class);
        when(slide.getId()).thenReturn(id);
        when(slide.getDeckId()).thenReturn(0L);
        when(slide.getSlideOrder()).thenReturn(order);
        when(slide.getTitle()).thenReturn("Slide " + order);
        when(slide.getLayoutType()).thenReturn(PresentationSlideLayout.BLANK);
        when(slide.getGenerationState()).thenReturn(PresentationSlideGenerationState.GENERATED);
        return slide;
    }

    private PresentationSlideElement element(Long id, Long deckId, Long slideId,
                                              PresentationElementType type, String payload, String style) {
        return element(id, deckId, slideId, type, payload, style, BigDecimal.ZERO);
    }

    private PresentationSlideElement element(Long id, Long deckId, Long slideId,
                                              PresentationElementType type, String payload, String style,
                                              BigDecimal x) {
        PresentationSlideElement element = mock(PresentationSlideElement.class);
        when(element.getId()).thenReturn(id);
        when(element.getDeckId()).thenReturn(deckId);
        when(element.getSlideId()).thenReturn(slideId);
        when(element.getElementType()).thenReturn(type);
        when(element.getZIndex()).thenReturn(0);
        when(element.getX()).thenReturn(x);
        when(element.getY()).thenReturn(BigDecimal.ZERO);
        when(element.getWidth()).thenReturn(BigDecimal.TEN);
        when(element.getHeight()).thenReturn(BigDecimal.TEN);
        when(element.getRotation()).thenReturn(BigDecimal.ZERO);
        when(element.getPayloadJson()).thenReturn(payload);
        when(element.getStyleJson()).thenReturn(style);
        return element;
    }

    private PresentationMediaAsset media(Long id, long sizeBytes, Long deckId) {
        PresentationMediaAsset media = mock(PresentationMediaAsset.class);
        User mediaOwner = new User();
        mediaOwner.setId(owner.getId());
        when(media.getId()).thenReturn(id);
        when(media.getDeckId()).thenReturn(deckId);
        when(media.getOwner()).thenReturn(mediaOwner);
        when(media.getMimeType()).thenReturn("image/png");
        when(media.getSizeBytes()).thenReturn(sizeBytes);
        when(media.getOriginalFileName()).thenReturn("fixture.png");
        return media;
    }

    private void assertInvalid(Long deckId) {
        assertThatThrownBy(() -> snapshotService.capture(owner, deckId))
                .isInstanceOf(PresentationExportStateInvalidException.class);
    }
}
