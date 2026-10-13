package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.dto.SlideElementCreateRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementDeleteRequest;
import com.example.studyassistant.domain.presentation.dto.SlideElementReadDto;
import com.example.studyassistant.domain.presentation.dto.SlideElementUpdateRequest;
import com.example.studyassistant.domain.presentation.dto.SlidePatchRequest;
import com.example.studyassistant.domain.presentation.dto.SlideReadDto;
import com.example.studyassistant.domain.presentation.dto.SlideSourceReadDto;
import com.example.studyassistant.domain.presentation.generation.SlideGenerationException;
import com.example.studyassistant.domain.presentation.generation.SlideElementValidator;
import com.example.studyassistant.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PresentationSlideEditorService {

    private static final BigDecimal MAX_GEOMETRY = new BigDecimal("99999.99");
    private static final BigDecimal MIN_ROTATION = new BigDecimal("-9999.99");
    private static final BigDecimal MAX_ROTATION = new BigDecimal("9999.99");

    private final PresentationDeckRepository deckRepository;
    private final PresentationSlideRepository slideRepository;
    private final PresentationSlideElementRepository elementRepository;
    private final PresentationSlideSourceRepository sourceRepository;
    private final PresentationMediaAssetRepository mediaAssetRepository;
    private final PresentationOutlineRevisionRepository outlineRepository;
    private final ObjectMapper objectMapper;

    public PresentationSlideEditorService(
            PresentationDeckRepository deckRepository,
            PresentationSlideRepository slideRepository,
            PresentationSlideElementRepository elementRepository,
            PresentationSlideSourceRepository sourceRepository,
            PresentationMediaAssetRepository mediaAssetRepository,
            PresentationOutlineRevisionRepository outlineRepository,
            @Qualifier("legacyJackson2ObjectMapper") ObjectMapper objectMapper) {
        this.deckRepository = deckRepository;
        this.slideRepository = slideRepository;
        this.elementRepository = elementRepository;
        this.sourceRepository = sourceRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.outlineRepository = outlineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SlideReadDto> listSlides(User user, Long deckId) {
        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        List<PresentationSlide> slides = slideRepository.findByDeckIdOrderBySlideOrderAsc(deck.getId());
        List<SlideReadDto> result = new ArrayList<>();
        for (PresentationSlide slide : slides) {
            result.add(toReadDto(slide, deck));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SlideReadDto getSlide(User user, Long deckId, Long slideId) {
        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        PresentationSlide slide = requireOwnedSlide(deck.getId(), slideId);
        return toReadDto(slide, deck);
    }

    @Transactional
    public SlideReadDto patchSlide(User user, Long deckId, Long slideId, SlidePatchRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.expectedSlideVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedSlideVersion is required");
        }
        if (request.title() == null && request.speakerNotes() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one slide field must be provided");
        }

        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        rejectArchived(deck);
        PresentationSlide slide = requireOwnedSlide(deck.getId(), slideId);
        requireCurrentRevision(deck, slide);
        if (!Objects.equals(slide.getVersion(), request.expectedSlideVersion())) {
            throw staleVersion();
        }

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
            }
            if (title.length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title exceeds 255 characters");
            }
            slide.setTitle(title);
        }
        if (request.speakerNotes() != null) {
            if (request.speakerNotes().getBytes(StandardCharsets.UTF_8).length > 65000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerNotes exceeds 65,000 UTF-8 bytes");
            }
            slide.setSpeakerNotes(request.speakerNotes());
        }
        slide.setGenerationState(PresentationSlideGenerationState.EDITED);
        slide.setUpdatedAt(LocalDateTime.now());
        slideRepository.saveAndFlush(slide);
        return toReadDto(slide, deck);
    }

    @Transactional
    public SlideElementReadDto addElement(User user, Long deckId, Long slideId, SlideElementCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.expectedSlideVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedSlideVersion is required");
        }
        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        rejectArchived(deck);
        PresentationSlide slide = requireOwnedSlide(deck.getId(), slideId);
        requireCurrentRevision(deck, slide);
        if (!Objects.equals(slide.getVersion(), request.expectedSlideVersion())) {
            throw staleVersion();
        }

        validateGeometry(request.x(), request.y(), request.width(), request.height(), request.rotation());
        PresentationElementType elementType = parseElementType(request.elementType());
        Map<String, Object> payload = normalizeTypedPayload(elementType, request.payload());
        Map<String, Object> style = normalizeTypedStyle(elementType, request.style());

        Long mediaAssetId = request.mediaAssetId();
        if (elementType == PresentationElementType.IMAGE) {
            if (mediaAssetId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaAssetId is required for IMAGE elements");
            }
            PresentationMediaAsset media = mediaAssetRepository.findByIdAndDeckIdAndOwnerId(mediaAssetId, deckId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
            // final DB safety fence stays same-deck/owner and explicit same-deck relationship
            if (media.getDeckId() == null || !media.getDeckId().equals(deckId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
            }
        }

        PresentationSlideElement element = new PresentationSlideElement();
        element.setDeckId(deckId);
        element.setSlideId(slideId);
        element.setElementType(elementType);
        element.setZIndex(request.zIndex() == null ? 0 : request.zIndex());
        element.setX(request.x());
        element.setY(request.y());
        element.setWidth(request.width());
        element.setHeight(request.height());
        element.setRotation(request.rotation() == null ? BigDecimal.ZERO : request.rotation());
        element.setPayloadJson(toJson(payload == null ? Map.of() : payload));
        element.setStyleJson(toJson(style == null ? Map.of() : style));
        element.setMediaAssetId(mediaAssetId);
        element.setCreatedAt(LocalDateTime.now());
        element.setUpdatedAt(LocalDateTime.now());
        PresentationSlideElement persisted = elementRepository.saveAndFlush(element);

        slide.setGenerationState(PresentationSlideGenerationState.EDITED);
        slide.setUpdatedAt(LocalDateTime.now());
        slideRepository.saveAndFlush(slide);
        return toReadDto(persisted, slide, deck);
    }

    @Transactional
    public SlideElementReadDto updateElement(User user, Long deckId, Long slideId, Long elementId, SlideElementUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.expectedSlideVersion() == null || request.expectedElementVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedSlideVersion and expectedElementVersion are required");
        }
        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        rejectArchived(deck);
        PresentationSlide slide = requireOwnedSlide(deck.getId(), slideId);
        requireCurrentRevision(deck, slide);
        if (!Objects.equals(slide.getVersion(), request.expectedSlideVersion())) {
            throw staleVersion();
        }
        PresentationSlideElement element = requireOwnedElement(deckId, slideId, elementId);
        if (!Objects.equals(element.getVersion(), request.expectedElementVersion())) {
            throw staleVersion();
        }

        PresentationElementType elementType = element.getElementType();
        if (request.elementType() != null && !request.elementType().equalsIgnoreCase(elementType.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Element type cannot change");
        }
        validateGeometry(request.x(), request.y(), request.width(), request.height(), request.rotation());
        Map<String, Object> payload = normalizeTypedPayload(elementType, request.payload() == null ? parseJsonObject(element.getPayloadJson()) : request.payload());
        Map<String, Object> style = normalizeTypedStyle(elementType, request.style() == null ? parseJsonObject(element.getStyleJson()) : request.style());

        Long mediaAssetId = request.mediaAssetId() != null ? request.mediaAssetId() : element.getMediaAssetId();
        if (elementType == PresentationElementType.IMAGE) {
            if (mediaAssetId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaAssetId is required for IMAGE elements");
            }
            PresentationMediaAsset media = mediaAssetRepository.findByIdAndDeckIdAndOwnerId(mediaAssetId, deckId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
            if (media.getDeckId() == null || !media.getDeckId().equals(deckId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
            }
        }

        element.setElementType(elementType);
        element.setZIndex(request.zIndex() == null ? element.getZIndex() : request.zIndex());
        element.setX(request.x() == null ? element.getX() : request.x());
        element.setY(request.y() == null ? element.getY() : request.y());
        element.setWidth(request.width() == null ? element.getWidth() : request.width());
        element.setHeight(request.height() == null ? element.getHeight() : request.height());
        element.setRotation(request.rotation() == null ? element.getRotation() : request.rotation());
        element.setPayloadJson(toJson(payload == null ? Map.of() : payload));
        element.setStyleJson(toJson(style == null ? Map.of() : style));
        element.setMediaAssetId(mediaAssetId);
        element.setUpdatedAt(LocalDateTime.now());
        elementRepository.saveAndFlush(element);

        slide.setGenerationState(PresentationSlideGenerationState.EDITED);
        slide.setUpdatedAt(LocalDateTime.now());
        slideRepository.saveAndFlush(slide);
        return toReadDto(element, slide, deck);
    }

    @Transactional
    public void deleteElement(User user, Long deckId, Long slideId, Long elementId, SlideElementDeleteRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.expectedSlideVersion() == null || request.expectedElementVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedSlideVersion and expectedElementVersion are required");
        }
        PresentationDeck deck = requireOwnedDeck(user.getId(), deckId);
        rejectArchived(deck);
        PresentationSlide slide = requireOwnedSlide(deck.getId(), slideId);
        requireCurrentRevision(deck, slide);
        if (!Objects.equals(slide.getVersion(), request.expectedSlideVersion())) {
            throw staleVersion();
        }
        PresentationSlideElement element = requireOwnedElement(deckId, slideId, elementId);
        if (!Objects.equals(element.getVersion(), request.expectedElementVersion())) {
            throw staleVersion();
        }

        elementRepository.delete(element);
        elementRepository.flush();
        slide.setGenerationState(PresentationSlideGenerationState.EDITED);
        slide.setUpdatedAt(LocalDateTime.now());
        slideRepository.saveAndFlush(slide);
    }

    private PresentationDeck requireOwnedDeck(Long ownerId, Long deckId) {
        return deckRepository.findByIdAndOwnerId(deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
    }

    private PresentationSlide requireOwnedSlide(Long deckId, Long slideId) {
        return slideRepository.findByIdAndDeckId(slideId, deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slide not found"));
    }

    private PresentationSlideElement requireOwnedElement(Long deckId, Long slideId, Long elementId) {
        return elementRepository.findByIdAndDeckIdAndSlideId(elementId, deckId, slideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Element not found"));
    }

    private void rejectArchived(PresentationDeck deck) {
        if (deck.getStatus() == PresentationDeckStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck is archived");
        }
    }

    private void requireCurrentRevision(PresentationDeck deck, PresentationSlide slide) {
        PresentationOutlineRevision approvedRevision = deck.getApprovedOutlineRevision();
        if (approvedRevision == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck has no approved outline");
        }
        if (approvedRevision.getStatus() != PresentationOutlineStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approved outline is not current");
        }
        if (!Objects.equals(slide.getGeneratedFromOutlineRevisionId(), approvedRevision.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slide is not on the current approved revision");
        }
    }

    private ResponseStatusException staleVersion() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Stale optimistic version");
    }

    private PresentationElementType parseElementType(String elementType) {
        if (elementType == null || elementType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "elementType is required");
        }
        try {
            return PresentationElementType.valueOf(elementType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported element type: " + elementType);
        }
    }

    private Map<String, Object> normalizeTypedPayload(PresentationElementType elementType, Map<String, Object> raw) {
        Map<String, Object> payload = raw == null ? Map.of() : raw;
        try {
            switch (elementType) {
                case TEXT -> {
                    SlideElementValidator.validateTextPayload(payload, 0);
                    return payload;
                }
                case SHAPE -> {
                    SlideElementValidator.validateShapePayload(payload, 0);
                    return payload;
                }
                case IMAGE -> {
                    return payload == null ? Map.of() : payload;
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported element type");
            }
        } catch (SlideGenerationException.SlideSchemaViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private Map<String, Object> normalizeTypedStyle(PresentationElementType elementType, Map<String, Object> raw) {
        Map<String, Object> style = raw == null ? Map.of() : raw;
        try {
            switch (elementType) {
                case TEXT -> {
                    SlideElementValidator.validateTextStyle(style, 0);
                    return style;
                }
                case SHAPE -> {
                    SlideElementValidator.validateShapeStyle(style, 0);
                    return style;
                }
                case IMAGE -> {
                    return style == null ? Map.of() : style;
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported element type");
            }
        } catch (SlideGenerationException.SlideSchemaViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void validateGeometry(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, BigDecimal rotation) {
        if (x == null || y == null || width == null || height == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "x, y, width, and height are required");
        }
        if (x.stripTrailingZeros().scale() > 2 || y.stripTrailingZeros().scale() > 2 || width.stripTrailingZeros().scale() > 2 || height.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry values must have at most 2 decimal places");
        }
        if (rotation != null && rotation.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rotation must have at most 2 decimal places");
        }
        if (x.compareTo(BigDecimal.ZERO) < 0 || y.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "x and y must be >= 0");
        }
        if (width.compareTo(BigDecimal.ZERO) <= 0 || height.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "width and height must be > 0");
        }
        if (x.compareTo(MAX_GEOMETRY) > 0 || y.compareTo(MAX_GEOMETRY) > 0 || width.compareTo(MAX_GEOMETRY) > 0 || height.compareTo(MAX_GEOMETRY) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry exceeds maximum DB precision");
        }
        if (rotation != null && (rotation.compareTo(MIN_ROTATION) < 0 || rotation.compareTo(MAX_ROTATION) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rotation must be within -9999.99..9999.99");
        }
    }

    private SlideReadDto toReadDto(PresentationSlide slide, PresentationDeck deck) {
        List<PresentationSlideElement> elements = elementRepository.findBySlideIdOrderByZIndexAscIdAsc(slide.getId());
        List<SlideElementReadDto> elementDtos = new ArrayList<>();
        for (PresentationSlideElement e : elements) {
            elementDtos.add(toReadDto(e, slide, deck));
        }
        List<PresentationSlideSource> sources = sourceRepository.findBySlideIdOrderBySourceOrderAsc(slide.getId());
        List<SlideSourceReadDto> sourceDtos = new ArrayList<>();
        for (PresentationSlideSource source : sources) {
            sourceDtos.add(new SlideSourceReadDto(
                    source.getId(),
                    source.getDocument() != null ? source.getDocument().getId() : null,
                    source.getSourceOrder(),
                    source.getEvidenceExcerpt(),
                    source.getSourceAlias(),
                    source.getPageStart(),
                    source.getPageEnd(),
                    source.getSectionTitle()));
        }
        return new SlideReadDto(
                slide.getId(),
                slide.getDeckId(),
                slide.getSlideOrder(),
                slide.getLayoutType() != null ? slide.getLayoutType().name() : null,
                slide.getTitle(),
                slide.getSpeakerNotes(),
                slide.getGenerationState() != null ? slide.getGenerationState().name() : null,
                slide.getVersion(),
                slide.getGeneratedFromOutlineRevisionId(),
                elementDtos,
                sourceDtos);
    }

    private SlideElementReadDto toReadDto(PresentationSlideElement element, PresentationSlide slide, PresentationDeck deck) {
        Map<String, Object> payload = parseJsonObject(element.getPayloadJson());
        Map<String, Object> style = parseJsonObject(element.getStyleJson());
        Long mediaAssetId = element.getMediaAssetId();
        String originalFilename = null;
        String mimeType = null;
        Integer mediaWidth = null;
        Integer mediaHeight = null;
        String contentUrl = null;
        if (mediaAssetId != null) {
            PresentationMediaAsset media = mediaAssetRepository.findById(mediaAssetId).orElse(null);
            if (media != null && media.getDeckId() != null && media.getDeckId().equals(deck.getId())) {
                originalFilename = media.getOriginalFileName();
                mimeType = media.getMimeType();
                mediaWidth = media.getWidth();
                mediaHeight = media.getHeight();
                contentUrl = "/api/teacher/presentations/" + deck.getId() + "/media/" + media.getId() + "/content";
            }
        }
        return new SlideElementReadDto(
                element.getId(),
                element.getSlideId(),
                element.getElementType().name(),
                element.getZIndex(),
                element.getX(),
                element.getY(),
                element.getWidth(),
                element.getHeight(),
                element.getRotation(),
                element.getVersion(),
                payload,
                style,
                mediaAssetId,
                originalFilename,
                mimeType,
                mediaWidth,
                mediaHeight,
                contentUrl);
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON element payload");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return map == null ? Collections.emptyMap() : map;
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
