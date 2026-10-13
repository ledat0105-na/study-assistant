package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationElementType;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationSlide;
import com.example.studyassistant.domain.presentation.PresentationSlideElement;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideGenerationState;
import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import com.example.studyassistant.domain.presentation.PresentationSlideRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideEditorService;
import com.example.studyassistant.domain.presentation.generation.SlideElementValidator;
import com.example.studyassistant.domain.presentation.generation.SlideGenerationException;
import com.example.studyassistant.domain.presentation.media.PresentationImageValidator;
import com.example.studyassistant.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PresentationPptxExportSnapshotService {

    public static final int MAX_SLIDES = 30;
    public static final int MAX_ELEMENTS_PER_SLIDE = 50;
    public static final int MAX_TOTAL_ELEMENTS = 1500;
    public static final long MAX_REFERENCED_MEDIA_BYTES = 104_857_600L;

    private static final BigDecimal MAX_GEOMETRY = new BigDecimal("99999.99");
    private static final BigDecimal MIN_ROTATION = new BigDecimal("-9999.99");
    private static final BigDecimal MAX_ROTATION = new BigDecimal("9999.99");
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final PresentationDeckRepository deckRepository;
    private final PresentationSlideRepository slideRepository;
    private final PresentationSlideElementRepository elementRepository;
    private final PresentationMediaAssetRepository mediaRepository;
    private final ObjectMapper objectMapper;

    public PresentationPptxExportSnapshotService(
            PresentationDeckRepository deckRepository,
            PresentationSlideRepository slideRepository,
            PresentationSlideElementRepository elementRepository,
            PresentationMediaAssetRepository mediaRepository,
            ObjectMapper objectMapper) {
        this.deckRepository = deckRepository;
        this.slideRepository = slideRepository;
        this.elementRepository = elementRepository;
        this.mediaRepository = mediaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportSnapshot capture(User owner, Long deckId) {
        PresentationDeck deck = deckRepository.findByIdAndOwnerId(deckId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

        List<PresentationSlide> slides = new ArrayList<>(slideRepository.findByDeckIdOrderBySlideOrderAsc(deckId));
        slides.sort(Comparator.comparing(PresentationSlide::getSlideOrder)
                .thenComparing(PresentationSlide::getId));
        if (slides.size() > MAX_SLIDES) {
            throw invalid("Slide count exceeds export limit");
        }

        List<SlideSnapshot> slideSnapshots = new ArrayList<>(slides.size());
        Map<Long, MediaSnapshot> mediaSnapshots = new LinkedHashMap<>();
        int totalElements = 0;
        long totalMediaBytes = 0L;

        for (PresentationSlide slide : slides) {
            if (slide.getGenerationState() != PresentationSlideGenerationState.GENERATED
                    && slide.getGenerationState() != PresentationSlideGenerationState.EDITED) {
                throw invalid("Slide " + slide.getSlideOrder() + " chưa hoàn tất. Hãy tạo lại hoặc chỉnh sửa trước khi xuất PPTX.");
            }
            List<PresentationSlideElement> elements = elementRepository
                    .findBySlideIdOrderByZIndexAscIdAsc(slide.getId());
            if (elements.size() > MAX_ELEMENTS_PER_SLIDE) {
                throw invalid("Element count exceeds per-slide export limit");
            }
            totalElements += elements.size();
            if (totalElements > MAX_TOTAL_ELEMENTS) {
                throw invalid("Element count exceeds deck export limit");
            }

            List<ElementSnapshot> elementSnapshots = new ArrayList<>(elements.size());
            for (PresentationSlideElement element : elements) {
                if (!Objects.equals(element.getDeckId(), deckId) ||
                        !Objects.equals(element.getSlideId(), slide.getId())) {
                    throw invalid("Element is inconsistent with its slide");
                }
                validateGeometry(element);
                PresentationElementType type = element.getElementType();
                Map<String, Object> payload = parseObject(element.getPayloadJson(), "payload");
                Map<String, Object> style = parseObject(element.getStyleJson(), "style");
                validateTypedContent(type, payload, style);

                MediaSnapshot media = null;
                if (type == PresentationElementType.IMAGE) {
                    if (element.getMediaAssetId() == null) {
                        throw invalid("Image element has no media asset");
                    }
                    media = mediaSnapshots.get(element.getMediaAssetId());
                    if (media == null) {
                        PresentationMediaAsset asset = mediaRepository
                                .findByIdAndDeckIdAndOwnerId(element.getMediaAssetId(), deckId, owner.getId())
                                .orElseThrow(() -> invalid("Referenced media is missing"));
                        media = toMediaSnapshot(asset, deckId, owner.getId(), element.getMediaAssetId());
                        mediaSnapshots.put(media.id(), media);
                        try {
                            totalMediaBytes = Math.addExact(totalMediaBytes, media.sizeBytes());
                        } catch (ArithmeticException ex) {
                            throw invalid("Referenced media size overflow");
                        }
                        if (totalMediaBytes > MAX_REFERENCED_MEDIA_BYTES) {
                            throw invalid("Referenced media exceeds export byte limit");
                        }
                    }
                } else if (element.getMediaAssetId() != null) {
                    throw invalid("Non-image element references media");
                }

                elementSnapshots.add(new ElementSnapshot(
                        element.getId(),
                        type,
                        element.getZIndex(),
                        element.getX(),
                        element.getY(),
                        element.getWidth(),
                        element.getHeight(),
                        element.getRotation(),
                        payload,
                        style,
                        media));
            }

            slideSnapshots.add(new SlideSnapshot(
                    slide.getId(),
                    slide.getSlideOrder(),
                    slide.getTitle(),
                    slide.getSpeakerNotes(),
                    slide.getLayoutType(),
                    slide.getGenerationState(),
                    List.copyOf(elementSnapshots)));
        }

        return new ExportSnapshot(deck.getId(), deck.getTitle(), owner.getId(),
                List.copyOf(slideSnapshots), Map.copyOf(mediaSnapshots));
    }

    private MediaSnapshot toMediaSnapshot(PresentationMediaAsset asset, Long deckId, Long ownerId, Long mediaId) {
        if (!Objects.equals(asset.getId(), mediaId) || !Objects.equals(asset.getDeckId(), deckId) ||
                asset.getOwner() == null || !Objects.equals(asset.getOwner().getId(), ownerId)) {
            throw invalid("Referenced media is inconsistent with its deck owner");
        }
        if (asset.getSizeBytes() == null || asset.getSizeBytes() <= 0 ||
                asset.getSizeBytes() > PresentationImageValidator.MAX_FILE_BYTES) {
            throw invalid("Referenced media has invalid size");
        }
        String mimeType = asset.getMimeType() == null ? "" : asset.getMimeType().toLowerCase(Locale.ROOT);
        if (!mimeType.equals("image/png") && !mimeType.equals("image/jpeg") && !mimeType.equals("image/webp")) {
            throw invalid("Referenced media has unsupported MIME type");
        }
        return new MediaSnapshot(asset.getId(), asset.getDeckId(), asset.getOwner().getId(),
                mimeType, asset.getSizeBytes(), asset.getOriginalFileName());
    }

    private void validateTypedContent(PresentationElementType type, Map<String, Object> payload, Map<String, Object> style) {
        try {
            switch (type) {
                case TEXT -> {
                    SlideElementValidator.validateTextPayload(payload, 0);
                    SlideElementValidator.validateTextStyle(style, 0);
                    Object fontSize = style.get("fontSizePt");
                    if (fontSize != null) {
                        if (!(fontSize instanceof Number number) ||
                                new BigDecimal(number.toString()).stripTrailingZeros().scale() > 0) {
                            throw invalid("fontSizePt must be an integer");
                        }
                    }
                }
                case SHAPE -> {
                    SlideElementValidator.validateShapePayload(payload, 0);
                    SlideElementValidator.validateShapeStyle(style, 0);
                }
                case IMAGE -> {
                    // V1 has no additional persisted IMAGE payload/style schema.
                }
                default -> throw invalid("Unsupported element type");
            }
        } catch (SlideGenerationException.SlideSchemaViolationException | ClassCastException | NumberFormatException ex) {
            throw invalid("Persisted element content is invalid");
        }
    }

    private void validateGeometry(PresentationSlideElement element) {
        BigDecimal x = element.getX();
        BigDecimal y = element.getY();
        BigDecimal width = element.getWidth();
        BigDecimal height = element.getHeight();
        BigDecimal rotation = element.getRotation();
        if (x == null || y == null || width == null || height == null || rotation == null ||
                x.stripTrailingZeros().scale() > 2 || y.stripTrailingZeros().scale() > 2 ||
                width.stripTrailingZeros().scale() > 2 || height.stripTrailingZeros().scale() > 2 ||
                rotation.stripTrailingZeros().scale() > 2 ||
                x.signum() < 0 || y.signum() < 0 || width.signum() <= 0 || height.signum() <= 0 ||
                x.compareTo(MAX_GEOMETRY) > 0 || y.compareTo(MAX_GEOMETRY) > 0 ||
                width.compareTo(MAX_GEOMETRY) > 0 || height.compareTo(MAX_GEOMETRY) > 0 ||
                rotation.compareTo(MIN_ROTATION) < 0 || rotation.compareTo(MAX_ROTATION) > 0) {
            throw invalid("Persisted element geometry is invalid");
        }
    }

    private Map<String, Object> parseObject(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            throw invalid("Persisted element " + fieldName + " is missing");
        }
        try {
            LinkedHashMap<String, Object> value = objectMapper.readValue(json, OBJECT_MAP);
            if (value == null) {
                throw invalid("Persisted element " + fieldName + " must be an object");
            }
            return Collections.unmodifiableMap(value);
        } catch (JsonProcessingException ex) {
            throw invalid("Persisted element " + fieldName + " is malformed");
        }
    }

    private static PresentationExportStateInvalidException invalid(String message) {
        return new PresentationExportStateInvalidException(message);
    }

    public record ExportSnapshot(
            Long deckId,
            String deckTitle,
            Long ownerId,
            List<SlideSnapshot> slides,
            Map<Long, MediaSnapshot> media) {}

    public record SlideSnapshot(
            Long id,
            Integer slideOrder,
            String title,
            String speakerNotes,
            PresentationSlideLayout layoutType,
            PresentationSlideGenerationState generationState,
            List<ElementSnapshot> elements) {}

    public record ElementSnapshot(
            Long id,
            PresentationElementType type,
            Integer zIndex,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            BigDecimal rotation,
            Map<String, Object> payload,
            Map<String, Object> style,
            MediaSnapshot media) {}

    public record MediaSnapshot(Long id, Long deckId, Long ownerId, String mimeType,
                                long sizeBytes, String originalFilename) {}
}
