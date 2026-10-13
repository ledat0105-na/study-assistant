package com.example.studyassistant.domain.presentation.generation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates LLM-provided slide element data.
 *
 * Canvas: 960 x 540
 * Supported element types: TEXT, SHAPE
 * Server assigns rotation=0, zIndex=array-position
 * LLM must NOT provide rotation or zIndex.
 */
public final class SlideElementValidator {

    public static final BigDecimal CANVAS_WIDTH = new BigDecimal("960");
    public static final BigDecimal CANVAS_HEIGHT = new BigDecimal("540");
    public static final int MAX_ELEMENTS_PER_SLIDE = 50;
    public static final int MAX_TEXT_LENGTH = 2000;
    public static final int MAX_SHAPE_TEXT_LENGTH = 500;
    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 144;
    public static final int MAX_STROKE_WIDTH = 20;

    public static final Set<String> ALLOWED_TEXT_ALIGN = Set.of("LEFT", "CENTER", "RIGHT");
    public static final Set<String> ALLOWED_SHAPE_PRIMITIVES = Set.of(
        "RECTANGLE", "ROUNDED_RECTANGLE", "ELLIPSE", "LINE", "ARROW"
    );
    public static final Set<String> ALLOWED_TEXT_STYLE_KEYS = Set.of(
        "fontSizePt", "bold", "italic", "textAlign", "color"
    );
    public static final Set<String> ALLOWED_SHAPE_STYLE_KEYS = Set.of(
        "fillColor", "strokeColor", "strokeWidthPt"
    );
    public static final Set<String> ALLOWED_TEXT_PAYLOAD_KEYS = Set.of("text");
    public static final Set<String> ALLOWED_SHAPE_PAYLOAD_KEYS = Set.of("primitive", "text");
    // Server-owned fields that must NOT appear in LLM element JSON
    public static final Set<String> FORBIDDEN_ELEMENT_FIELDS = Set.of(
        "deckId", "slideId", "slideOrder", "outlineRevisionId", "generationKey",
        "generationState", "version", "mediaAssetId", "rotation", "zIndex",
        "id", "createdAt", "updatedAt"
    );

    private SlideElementValidator() {}

    /**
     * Validates geometry. Throws SlideSchemaViolationException on any violation.
     */
    public static void validateGeometry(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, int elementIndex) {
        String prefix = "Element[" + elementIndex + "]: ";
        if (x.compareTo(BigDecimal.ZERO) < 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "x must be >= 0");
        if (y.compareTo(BigDecimal.ZERO) < 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "y must be >= 0");
        if (width.compareTo(BigDecimal.ZERO) <= 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "width must be > 0");
        if (height.compareTo(BigDecimal.ZERO) <= 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "height must be > 0");
        if (x.add(width).compareTo(CANVAS_WIDTH) > 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "x + width exceeds canvas width 960");
        if (y.add(height).compareTo(CANVAS_HEIGHT) > 0) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "y + height exceeds canvas height 540");
    }

    /**
     * Validates TEXT element payload. Returns validated text string.
     */
    public static String validateTextPayload(Map<String, Object> payload, int elementIndex) {
        String prefix = "Element[" + elementIndex + "] TEXT payload: ";
        if (payload == null) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "missing payload");
        for (String key : payload.keySet()) {
            if (!ALLOWED_TEXT_PAYLOAD_KEYS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "unknown payload field '" + key + "'");
            }
        }
        Object text = payload.get("text");
        if (!(text instanceof String t) || t.isBlank()) {
            throw new SlideGenerationException.SlideSchemaViolationException(prefix + "text must be a non-blank string");
        }
        if (t.length() > MAX_TEXT_LENGTH) {
            throw new SlideGenerationException.SlideSchemaViolationException(prefix + "text exceeds max length " + MAX_TEXT_LENGTH);
        }
        return t;
    }

    /**
     * Validates TEXT element style. Returns validated style map (same reference, validated).
     */
    public static Map<String, Object> validateTextStyle(Map<String, Object> style, int elementIndex) {
        String prefix = "Element[" + elementIndex + "] TEXT style: ";
        if (style == null) return Map.of();
        for (String key : style.keySet()) {
            if (!ALLOWED_TEXT_STYLE_KEYS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "unknown style field '" + key + "'");
            }
        }
        if (style.containsKey("fontSizePt")) {
            Object v = style.get("fontSizePt");
            if (!(v instanceof Number n) || n.intValue() < MIN_FONT_SIZE || n.intValue() > MAX_FONT_SIZE) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "fontSizePt must be integer " + MIN_FONT_SIZE + ".." + MAX_FONT_SIZE);
            }
        }
        if (style.containsKey("bold") && !(style.get("bold") instanceof Boolean)) {
            throw new SlideGenerationException.SlideSchemaViolationException(prefix + "bold must be boolean");
        }
        if (style.containsKey("italic") && !(style.get("italic") instanceof Boolean)) {
            throw new SlideGenerationException.SlideSchemaViolationException(prefix + "italic must be boolean");
        }
        if (style.containsKey("textAlign")) {
            Object v = style.get("textAlign");
            if (!(v instanceof String s) || !ALLOWED_TEXT_ALIGN.contains(s)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "textAlign must be one of " + ALLOWED_TEXT_ALIGN);
            }
        }
        if (style.containsKey("color")) {
            validateHexColor((String) style.get("color"), prefix + "color");
        }
        return style;
    }

    /**
     * Validates SHAPE element payload. Returns primitive string.
     */
    public static String validateShapePayload(Map<String, Object> payload, int elementIndex) {
        String prefix = "Element[" + elementIndex + "] SHAPE payload: ";
        if (payload == null) throw new SlideGenerationException.SlideSchemaViolationException(prefix + "missing payload");
        for (String key : payload.keySet()) {
            if (!ALLOWED_SHAPE_PAYLOAD_KEYS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "unknown payload field '" + key + "'");
            }
        }
        Object prim = payload.get("primitive");
        if (!(prim instanceof String s) || !ALLOWED_SHAPE_PRIMITIVES.contains(s)) {
            throw new SlideGenerationException.SlideSchemaViolationException(prefix + "primitive must be one of " + ALLOWED_SHAPE_PRIMITIVES);
        }
        if (payload.containsKey("text")) {
            Object t = payload.get("text");
            if (t != null && (!(t instanceof String ts) || ts.length() > MAX_SHAPE_TEXT_LENGTH)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "text exceeds max length " + MAX_SHAPE_TEXT_LENGTH);
            }
        }
        return s;
    }

    /**
     * Validates SHAPE element style.
     */
    public static Map<String, Object> validateShapeStyle(Map<String, Object> style, int elementIndex) {
        String prefix = "Element[" + elementIndex + "] SHAPE style: ";
        if (style == null) return Map.of();
        for (String key : style.keySet()) {
            if (!ALLOWED_SHAPE_STYLE_KEYS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "unknown style field '" + key + "'");
            }
        }
        if (style.containsKey("fillColor")) validateHexColor((String) style.get("fillColor"), prefix + "fillColor");
        if (style.containsKey("strokeColor")) validateHexColor((String) style.get("strokeColor"), prefix + "strokeColor");
        if (style.containsKey("strokeWidthPt")) {
            Object v = style.get("strokeWidthPt");
            if (!(v instanceof Number n) || n.intValue() < 0 || n.intValue() > MAX_STROKE_WIDTH) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "strokeWidthPt must be integer 0.." + MAX_STROKE_WIDTH);
            }
        }
        return style;
    }

    private static void validateHexColor(Object value, String fieldPath) {
        if (!(value instanceof String s) || !s.matches("#[0-9A-Fa-f]{6}")) {
            throw new SlideGenerationException.SlideSchemaViolationException(fieldPath + " must be #RRGGBB hex color");
        }
    }

    /**
     * Check that the element map does not contain server-owned fields.
     */
    public static void rejectServerOwnedFields(Map<String, Object> elementMap, int elementIndex) {
        String prefix = "Element[" + elementIndex + "]: ";
        for (String key : elementMap.keySet()) {
            if (FORBIDDEN_ELEMENT_FIELDS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException(prefix + "server-owned field '" + key + "' must not be provided by LLM");
            }
        }
    }
}
