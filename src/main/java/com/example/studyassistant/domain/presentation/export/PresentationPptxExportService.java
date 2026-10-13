package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.PresentationElementType;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.ElementSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.ExportSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.MediaSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.SlideSnapshot;
import com.example.studyassistant.domain.presentation.media.PresentationMediaContent;
import com.example.studyassistant.domain.presentation.media.PresentationMediaService;
import com.example.studyassistant.domain.presentation.media.PresentationImageValidator;
import com.example.studyassistant.entity.User;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PresentationPptxExportService {

    private static final int PAGE_WIDTH_POINTS = 960;
    private static final int PAGE_HEIGHT_POINTS = 540;
    private static final int TITLE_X = 48;
    private static final int TITLE_Y = 18;
    private static final int TITLE_WIDTH = 864;
    private static final int TITLE_HEIGHT = 54;
    private static final String DEFAULT_FONT = "Arial";
    private static final Color BLACK = Color.BLACK;

    private final PresentationPptxExportSnapshotService snapshotService;
    private final PresentationMediaService mediaService;
    private final Path spoolRoot;

    public PresentationPptxExportService(
            PresentationPptxExportSnapshotService snapshotService,
            PresentationMediaService mediaService,
            @Value("${presentation.export.spool-root:${java.io.tmpdir}}") String spoolRoot) {
        this.snapshotService = snapshotService;
        this.mediaService = mediaService;
        this.spoolRoot = Path.of(spoolRoot).toAbsolutePath().normalize();
    }

    public PptxExportArtifact export(User owner, Long deckId) {
        ExportSnapshot snapshot = snapshotService.capture(owner, deckId);
        Path spool = null;
        try {
            spool = Files.createTempFile(spoolRoot, "presentation-export-", ".pptx");
            try (XMLSlideShow presentation = new XMLSlideShow();
                 OutputStream output = Files.newOutputStream(spool)) {
                presentation.setPageSize(new Dimension(PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS));
                Map<Long, XSLFPictureData> pictureCache = new HashMap<>();
                List<SlideSnapshot> orderedSlides = new ArrayList<>(snapshot.slides());
                orderedSlides.sort(Comparator.comparing(SlideSnapshot::slideOrder)
                    .thenComparing(SlideSnapshot::id));
                for (SlideSnapshot slideSnapshot : orderedSlides) {
                    XSLFSlide slide = presentation.createSlide();
                    renderSlide(presentation, slide, snapshot, slideSnapshot, owner, pictureCache);
                }
                presentation.write(output);
            }
            return new PptxExportArtifact(spool, Files.size(spool), safeFilename(snapshot.deckTitle()));
        } catch (PresentationExportStateInvalidException ex) {
            deleteSpool(spool);
            throw ex;
        } catch (RuntimeException ex) {
            deleteSpool(spool);
            if (ex instanceof ResponseStatusException responseStatusException &&
                    responseStatusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new PresentationExportStateInvalidException("Referenced media is unavailable");
            }
            throw new PptxRenderingException(ex);
        } catch (IOException ex) {
            deleteSpool(spool);
            throw new PptxRenderingException(ex);
        }
    }

    private void renderSlide(XMLSlideShow presentation, XSLFSlide slide, ExportSnapshot deck,
                             SlideSnapshot slideSnapshot, User owner,
                             Map<Long, XSLFPictureData> pictureCache) throws IOException {
        if (hasTitle(slideSnapshot)) {
            addTitle(slide, slideSnapshot.title());
        }

        List<ElementSnapshot> elements = new ArrayList<>(slideSnapshot.elements());
        elements.sort(Comparator.comparing(ElementSnapshot::zIndex)
                .thenComparing(ElementSnapshot::id));
        for (ElementSnapshot element : elements) {
            switch (element.type()) {
                case TEXT -> addText(slide, element);
                case SHAPE -> addShape(slide, element);
                case IMAGE -> addImage(presentation, slide, deck, element, owner, pictureCache);
            }
        }

        if (slideSnapshot.speakerNotes() != null && !slideSnapshot.speakerNotes().isBlank()) {
            addSpeakerNotes(presentation, slide, slideSnapshot.speakerNotes());
        }
    }

    private boolean hasTitle(SlideSnapshot slide) {
        if (slide.title() == null || slide.title().isBlank()) {
            return false;
        }
        String normalizedTitle = slide.title().trim();
        return slide.elements().stream()
                .filter(element -> element.type() == PresentationElementType.TEXT)
                .map(element -> element.payload().get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .noneMatch(text -> text.trim().equals(normalizedTitle));
    }

    private void addTitle(XSLFSlide slide, String title) {
        XSLFTextShape textBox = slide.createTextBox();
        textBox.setAnchor(new Rectangle2D.Double(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT));
        configureTextBox(textBox);
        XSLFTextRun run = textBox.setText(title);
        setRunDefaults(run, 28.0, true, false, BLACK);
        firstParagraph(textBox).setTextAlign(TextParagraph.TextAlign.LEFT);
    }

    private void addText(XSLFSlide slide, ElementSnapshot element) {
        XSLFTextShape textBox = slide.createTextBox();
        textBox.setAnchor(anchor(element));
        configureTextBox(textBox);
        XSLFTextRun run = textBox.setText((String) element.payload().get("text"));
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(fontSize(element.style()));
        run.setBold(booleanStyle(element.style(), "bold", false));
        run.setItalic(booleanStyle(element.style(), "italic", false));
        run.setFontColor(color(element.style().get("color"), BLACK));
        firstParagraph(textBox).setTextAlign(textAlign(element.style().get("textAlign")));
        textBox.setRotation(normalizeRotation(element.rotation()));
    }

    private void addShape(XSLFSlide slide, ElementSnapshot element) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(shapeType(element));
        shape.setAnchor(anchor(element));
        shape.setRotation(normalizeRotation(element.rotation()));
        shape.setFillColor(color(element.style().get("fillColor"), null));
        Color stroke = color(element.style().get("strokeColor"), BLACK);
        shape.setLineColor(stroke);
        shape.setLineWidth(numberStyle(element.style(), "strokeWidthPt", 1.0));

        Object text = element.payload().get("text");
        if (text instanceof String shapeText && !shapeText.isEmpty()) {
            XSLFTextRun run = shape.setText(shapeText);
            setRunDefaults(run, 18.0, false, false, BLACK);
            shape.setVerticalAlignment(VerticalAlignment.MIDDLE);
            firstParagraph(shape).setTextAlign(TextParagraph.TextAlign.CENTER);
        }
    }

    private ShapeType shapeType(ElementSnapshot element) {
        String primitive = (String) element.payload().get("primitive");
        return switch (primitive) {
            case "RECTANGLE" -> ShapeType.RECT;
            case "ROUNDED_RECTANGLE" -> ShapeType.ROUND_RECT;
            case "ELLIPSE" -> ShapeType.ELLIPSE;
            case "LINE" -> ShapeType.LINE;
            case "ARROW" -> ShapeType.RIGHT_ARROW;
            default -> throw new PresentationExportStateInvalidException("Unsupported shape primitive");
        };
    }

    private void addImage(XMLSlideShow presentation, XSLFSlide slide, ExportSnapshot deck,
                          ElementSnapshot element, User owner,
                          Map<Long, XSLFPictureData> pictureCache) throws IOException {
        MediaSnapshot media = element.media();
        if (media == null || !media.deckId().equals(deck.deckId()) || !media.ownerId().equals(owner.getId())) {
            throw new PresentationExportStateInvalidException("Image media is inconsistent with its deck");
        }
        XSLFPictureData pictureData = pictureCache.get(media.id());
        if (pictureData == null) {
            byte[] bytes;
            try {
                bytes = readMediaBytes(deck, media, owner);
            } catch (PresentationExportStateInvalidException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new PresentationExportStateInvalidException("Referenced media is unavailable");
            }
            PictureData.PictureType pictureType;
            if ("image/png".equals(media.mimeType())) {
                pictureType = PictureData.PictureType.PNG;
            } else if ("image/jpeg".equals(media.mimeType())) {
                pictureType = PictureData.PictureType.JPEG;
            } else if ("image/webp".equals(media.mimeType())) {
                try {
                    bytes = convertWebpToPng(bytes);
                } catch (IOException ex) {
                    throw new PresentationExportStateInvalidException("Referenced WebP media is invalid");
                }
                pictureType = PictureData.PictureType.PNG;
            } else {
                throw new PresentationExportStateInvalidException("Unsupported image MIME type");
            }
            pictureData = presentation.addPicture(bytes, pictureType);
            pictureCache.put(media.id(), pictureData);
        }
        XSLFPictureShape picture = slide.createPicture(pictureData);
        picture.setAnchor(anchor(element));
        picture.setRotation(normalizeRotation(element.rotation()));
    }

    private byte[] readMediaBytes(ExportSnapshot deck, MediaSnapshot media, User owner) throws IOException {
        PresentationMediaContent content;
        try {
            content = mediaService.openContent(owner, deck.deckId(), media.id());
        } catch (ResponseStatusException ex) {
            throw new PresentationExportStateInvalidException("Referenced media is unavailable");
        }
        if (content == null || content.inputStream() == null ||
                !media.mimeType().equalsIgnoreCase(content.mimeType()) ||
                content.contentLength() != media.sizeBytes()) {
            closeQuietly(content == null ? null : content.inputStream());
            throw new PresentationExportStateInvalidException("Referenced media metadata changed");
        }

        try (InputStream input = content.inputStream()) {
            byte[] bytes = input.readNBytes((int) media.sizeBytes() + 1);
            if (bytes.length != media.sizeBytes() || input.read() != -1) {
                throw new PresentationExportStateInvalidException("Referenced media size changed");
            }
            validateImage(bytes, media.mimeType());
            return bytes;
        }
    }

    private byte[] convertWebpToPng(byte[] webpBytes) throws IOException {
        BufferedImage image = decodeImage(webpBytes, "image/webp");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG encoder unavailable");
            }
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private void validateImage(byte[] bytes, String mimeType) throws IOException {
        BufferedImage image = decodeImage(bytes, mimeType);
        image.flush();
    }

    private BufferedImage decodeImage(byte[] bytes, String mimeType) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInput == null) {
                throw new PresentationExportStateInvalidException("Referenced image is unreadable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new PresentationExportStateInvalidException("Referenced image is unreadable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                String expected = switch (mimeType) {
                    case "image/png" -> "PNG";
                    case "image/jpeg" -> "JPEG";
                    case "image/webp" -> "WEBP";
                    default -> throw new PresentationExportStateInvalidException("Unsupported image MIME type");
                };
                if (!expected.equals(format) && !(expected.equals("JPEG") && format.equals("JPG"))) {
                    throw new PresentationExportStateInvalidException("Image content does not match stored MIME type");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > PresentationImageValidator.MAX_WIDTH ||
                        height > PresentationImageValidator.MAX_HEIGHT ||
                        (long) width * height > PresentationImageValidator.MAX_PIXELS) {
                    throw new PresentationExportStateInvalidException("Referenced image dimensions exceed limits");
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) {
                    if (image != null) image.flush();
                    throw new PresentationExportStateInvalidException("Referenced image could not be decoded");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private void addSpeakerNotes(XMLSlideShow presentation, XSLFSlide slide, String notesText) {
        XSLFNotes notes = presentation.getNotesSlide(slide);
        XSLFTextShape body = null;
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getPlaceholder() == Placeholder.BODY) {
                body = textShape;
                break;
            }
        }
        if (body == null) {
            throw new PresentationExportStateInvalidException("Speaker notes body is unavailable");
        }
        body.clearText();
        body.setText(notesText);
    }

    private void configureTextBox(XSLFTextShape textBox) {
        textBox.setFillColor(null);
        textBox.setLineColor(null);
        textBox.setLineWidth(0.0);
        textBox.setVerticalAlignment(VerticalAlignment.TOP);
        textBox.setWordWrap(true);
        textBox.setLeftInset(0.0);
        textBox.setRightInset(0.0);
        textBox.setTopInset(0.0);
        textBox.setBottomInset(0.0);
    }

    private XSLFTextParagraph firstParagraph(XSLFTextShape shape) {
        List<XSLFTextParagraph> paragraphs = shape.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            return shape.addNewTextParagraph();
        }
        return paragraphs.get(0);
    }

    private void setRunDefaults(XSLFTextRun run, double size, boolean bold, boolean italic, Color color) {
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setItalic(italic);
        run.setFontColor(color);
    }

    private Rectangle2D anchor(ElementSnapshot element) {
        return new Rectangle2D.Double(element.x().doubleValue(), element.y().doubleValue(),
                element.width().doubleValue(), element.height().doubleValue());
    }

    private double normalizeRotation(BigDecimal rotation) {
        return rotation.remainder(BigDecimal.valueOf(360)).add(BigDecimal.valueOf(360))
                .remainder(BigDecimal.valueOf(360)).doubleValue();
    }

    private double fontSize(Map<String, Object> style) {
        Object value = style.get("fontSizePt");
        return value instanceof Number number ? number.doubleValue() : 18.0;
    }

    private boolean booleanStyle(Map<String, Object> style, String key, boolean fallback) {
        Object value = style.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private double numberStyle(Map<String, Object> style, String key, double fallback) {
        Object value = style.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private TextParagraph.TextAlign textAlign(Object value) {
        if (!(value instanceof String align)) {
            return TextParagraph.TextAlign.LEFT;
        }
        return switch (align) {
            case "CENTER" -> TextParagraph.TextAlign.CENTER;
            case "RIGHT" -> TextParagraph.TextAlign.RIGHT;
            default -> TextParagraph.TextAlign.LEFT;
        };
    }

    private Color color(Object value, Color fallback) {
        if (!(value instanceof String hex)) {
            return fallback;
        }
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    private String safeFilename(String title) {
        String base = title == null ? "" : title.trim();
        StringBuilder sanitized = new StringBuilder(base.length());
        for (char character : base.toCharArray()) {
            if (Character.isISOControl(character) || "<>:\"/\\|?*".indexOf(character) >= 0) {
                sanitized.append('_');
            } else {
                sanitized.append(character);
            }
        }
        base = sanitized.toString().replaceAll("\\.{2,}", "_")
                .replaceAll("[ .]+$", "").trim();
        if (base.length() > 180) {
            base = base.substring(0, 180).replaceAll("[ .]+$", "");
        }
        String deviceStem = (base.contains(".") ? base.substring(0, base.indexOf('.')) : base).trim();
        if (base.isBlank() || deviceStem.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            base = "presentation";
        }
        while (base.toLowerCase(Locale.ROOT).endsWith(".pptx")) {
            base = base.substring(0, base.length() - 5).replaceAll("[ .]+$", "");
        }
        if (base.isBlank()) {
            base = "presentation";
        }
        return base + ".pptx";
    }

    private void deleteSpool(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    private void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
