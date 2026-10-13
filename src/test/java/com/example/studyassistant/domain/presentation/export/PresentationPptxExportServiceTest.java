package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.PresentationElementType;
import com.example.studyassistant.domain.presentation.PresentationSlideGenerationState;
import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.ElementSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.ExportSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.MediaSnapshot;
import com.example.studyassistant.domain.presentation.export.PresentationPptxExportSnapshotService.SlideSnapshot;
import com.example.studyassistant.domain.presentation.media.PresentationMediaContent;
import com.example.studyassistant.domain.presentation.media.PresentationMediaService;
import com.example.studyassistant.entity.User;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresentationPptxExportServiceTest {

        @Mock private PresentationPptxExportSnapshotService snapshotService;
    @Mock private PresentationMediaService mediaService;

    @TempDir Path spoolRoot;

    private PresentationPptxExportService exportService;
    private User owner;

    @BeforeEach
    void setUp() {
        exportService = new PresentationPptxExportService(snapshotService, mediaService, spoolRoot.toString());
        owner = new User();
        owner.setId(41L);
    }

    @Test
    void textStyleGeometryRotationTitleAndSpeakerNotesSurvivePptxRoundTrip() throws Exception {
        ElementSnapshot text = element(7L, PresentationElementType.TEXT, 0, 12, 24, 280, 68, -30,
                Map.of("text", "Round-trip text"),
                Map.of("fontSizePt", 24, "bold", true, "italic", true, "textAlign", "CENTER", "color", "#123456"));
        ElementSnapshot titleText = element(8L, PresentationElementType.TEXT, 1, 50, 90, 200, 30, 0,
                Map.of("text", "  Deck title  "), Map.of());
        SlideSnapshot slideSnapshot = slide(4L, 1, "Deck title", "Presenter notes", List.of(text, titleText));
        mockDeck(10L, "deck", List.of(slideSnapshot), Map.of());

        try (PptxExportArtifact artifact = exportService.export(owner, 10L);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            XSLFSlide slide = reopened.getSlides().get(0);
            List<XSLFTextShape> textShapes = slide.getShapes().stream()
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .toList();
            assertThat(textShapes).hasSize(2);
            assertThat(textShapes).extracting(XSLFTextShape::getText)
                    .containsExactly("Round-trip text", "  Deck title  ");
            XSLFTextShape rendered = textShapes.get(0);
            assertThat(rendered.getAnchor().getX()).isEqualTo(12.0);
            assertThat(rendered.getAnchor().getY()).isEqualTo(24.0);
            assertThat(rendered.getAnchor().getWidth()).isEqualTo(280.0);
            assertThat(rendered.getAnchor().getHeight()).isEqualTo(68.0);
            assertThat(rendered.getRotation()).isEqualTo(330.0);
            var run = rendered.getTextParagraphs().get(0).getTextRuns().get(0);
            assertThat(run.getFontFamily()).isEqualTo("Arial");
            assertThat(run.getFontSize()).isEqualTo(24.0);
            assertThat(run.isBold()).isTrue();
            assertThat(run.isItalic()).isTrue();
            assertThat(((PaintStyle.SolidPaint) run.getFontColor()).getSolidColor().getColor())
                    .isEqualTo(Color.decode("#123456"));
            assertThat(rendered.getTextParagraphs().get(0).getTextAlign().name()).isEqualTo("CENTER");

            XSLFNotes notes = reopened.getNotesSlide(slide);
            String notesText = notes.getTextParagraphs().stream()
                    .flatMap(List::stream)
                    .map(XSLFTextParagraph::getText)
                    .reduce("", String::concat);
            assertThat(notesText).contains("Presenter notes");
        }
    }

    @Test
    void slidesAreOrderedTitleFallbackAndBlankSlideRoundTrip() throws Exception {
        SlideSnapshot later = slide(202L, 2, "Second title", null, List.of());
        SlideSnapshot earlier = slide(101L, 1, "First title", null, List.of());
        mockDeck(10L, "deck", List.of(later, earlier), Map.of());

        try (PptxExportArtifact artifact = exportService.export(owner, 10L);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getPageSize().getWidth()).isEqualTo(960.0);
            assertThat(reopened.getPageSize().getHeight()).isEqualTo(540.0);
            assertThat(reopened.getSlides()).hasSize(2);
            assertThat(reopened.getSlides().get(0).getShapes()).hasSize(1);
            assertThat(((XSLFTextShape) reopened.getSlides().get(0).getShapes().get(0)).getText())
                    .isEqualTo("First title");
            assertThat(((XSLFTextShape) reopened.getSlides().get(1).getShapes().get(0)).getAnchor().getX())
                    .isEqualTo(48.0);
        }

        mockDeck(11L, "blank", List.of(slide(303L, 1, "", null, List.of())), Map.of());
        try (PptxExportArtifact artifact = exportService.export(owner, 11L);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides()).hasSize(1);
            assertThat(reopened.getSlides().get(0).getShapes()).isEmpty();
        }
    }

    @Test
    void allCertifiedShapePrimitivesAndDuplicateZOrderRoundTrip() throws Exception {
        List<ElementSnapshot> elements = List.of(
                shape(10L, 2, "RECTANGLE", "rect", "#ff0000"),
                shape(11L, 2, "ROUNDED_RECTANGLE", "round", "#00ff00"),
                shape(12L, 3, "ELLIPSE", "ellipse", "#0000ff"),
                shape(13L, 4, "LINE", "line", "#111111"),
                shape(14L, 5, "ARROW", "arrow", "#222222"));
        mockDeck(10L, "deck", List.of(slide(1L, 1, null, null, elements)), Map.of());

        try (PptxExportArtifact artifact = exportService.export(owner, 10L);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            XSLFSlide slide = reopened.getSlides().get(0);
            List<XSLFAutoShape> shapes = slide.getShapes().stream()
                    .map(XSLFAutoShape.class::cast)
                    .toList();
            assertThat(shapes).extracting(XSLFAutoShape::getShapeType)
                    .containsExactly(ShapeType.RECT, ShapeType.ROUND_RECT, ShapeType.ELLIPSE,
                            ShapeType.LINE, ShapeType.RIGHT_ARROW);
            assertThat(shapes.get(0).getText()).isEqualTo("rect");
            assertThat(shapes.get(1).getFillColor()).isEqualTo(Color.GREEN);
        }
    }

    @Test
    void pngJpegAndWebpAreEmbeddedAndRepeatedAssetIsCached() throws Exception {
        byte[] png = imageBytes("png");
        byte[] jpeg = imageBytes("jpeg");
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        MediaSnapshot pngMedia = media(21L, "image/png", png);
        MediaSnapshot jpegMedia = media(22L, "image/jpeg", jpeg);
        MediaSnapshot webpMedia = media(23L, "image/webp", webp);
        Map<Long, MediaSnapshot> mediaById = Map.of(21L, pngMedia, 22L, jpegMedia, 23L, webpMedia);
        Map<Long, byte[]> contents = Map.of(21L, png, 22L, jpeg, 23L, webp);
        when(mediaService.openContent(eq(owner), eq(10L), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(2);
            byte[] bytes = contents.get(id);
            MediaSnapshot asset = mediaById.get(id);
            return new PresentationMediaContent(asset.mimeType(), bytes.length,
                    asset.originalFilename(),
                    new ByteArrayInputStream(bytes));
        });
        List<ElementSnapshot> elements = List.of(
                image(1L, 0, pngMedia), image(2L, 1, pngMedia),
                image(3L, 2, jpegMedia), image(4L, 3, webpMedia));
        mockDeck(10L, "deck", List.of(slide(1L, 1, null, null, elements)),
                Map.of(21L, pngMedia, 22L, jpegMedia, 23L, webpMedia));

        try (PptxExportArtifact artifact = exportService.export(owner, 10L);
             InputStream input = artifact.openStream();
             XMLSlideShow reopened = new XMLSlideShow(input)) {
            assertThat(reopened.getSlides().get(0).getShapes())
                    .filteredOn(shape -> shape instanceof org.apache.poi.xslf.usermodel.XSLFPictureShape)
                    .hasSize(4);
            assertThat(reopened.getPictureData()).hasSize(3);
            assertThat(reopened.getPictureData()).extracting(data -> data.getType())
                    .containsExactly(PictureData.PictureType.PNG, PictureData.PictureType.JPEG,
                            PictureData.PictureType.PNG);
            XSLFPictureShape picture = (XSLFPictureShape) reopened.getSlides().get(0).getShapes().get(0);
            assertThat(picture.getAnchor().getX()).isEqualTo(10.0);
            assertThat(picture.getAnchor().getHeight()).isEqualTo(50.0);
        }
    }

    @Test
    void artifactCloseRemovesPrivateSpoolAndFilenameIsSafe() throws Exception {
        mockDeck(10L, "CON", List.of(slide(1L, 1, "x", null, List.of())), Map.of());
        PptxExportArtifact artifact = exportService.export(owner, 10L);
        Path spool = artifact.spoolPath();
        assertThat(Files.exists(spool)).isTrue();
        assertThat(artifact.filename()).isEqualTo("presentation.pptx");
        artifact.close();
        assertThat(Files.exists(spool)).isFalse();

        mockDeck(11L, "../folder\\evil: name?.pptx.pptx", List.of(slide(2L, 1, "x", null, List.of())), Map.of());
        try (PptxExportArtifact sanitized = exportService.export(owner, 11L)) {
            assertThat(sanitized.filename()).matches("[A-Za-z0-9 _.-]+\\.pptx");
            assertThat(sanitized.filename()).doesNotContain("/", "\\", ":", "?", "*.pptx");
        }
    }

    @Test
    void missingPhysicalMediaAbortsExportWithInvalidState409() {
        MediaSnapshot media = new MediaSnapshot(31L, 10L, owner.getId(), "image/png", 100L, "missing.png");
        mockDeck(10L, "deck", List.of(slide(1L, 1, null, null, List.of(image(1L, 0, media)))),
                Map.of(31L, media));
        when(mediaService.openContent(eq(owner), eq(10L), eq(31L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "physical path must not leak"));

        assertThatThrownBy(() -> exportService.export(owner, 10L))
                .isInstanceOf(PresentationExportStateInvalidException.class)
                .hasMessageNotContaining("physical path");
    }

    @Test
    void fixedLayoutsSurviveRealExporterAndRenderWithoutDuplicateTitlesOrOverlap() throws Exception {
        var snapshots = new java.util.ArrayList<SlideSnapshot>();
        var planned = new java.util.ArrayList<com.example.studyassistant.domain.presentation.generation.ParsedSlideResult>();
        int order = 0;
        for (String type : List.of("TITLE", "SECTION", "CONTENT", "SUMMARY", "TWO_COLUMN")) {
            var parsed = com.example.studyassistant.domain.presentation.generation.SlideContentComposer.compose(
                    Map.of("title", "Vòng đời ứng dụng Android", "bullets", List.of(
                            "Activity hiển thị một màn hình và nhận tương tác của người dùng.",
                            "onCreate() khởi tạo giao diện và các dữ liệu cần thiết.",
                            "onPause() tạm dừng tương tác khi ứng dụng chuyển sang nền.",
                            "onDestroy() giải phóng tài nguyên khi Activity bị hủy."),
                            "speakerNotes", "Giải thích bằng tiếng Việt cho người học.", "sourceRefs", List.of("S1")),
                    1, type, "Android");
            planned.add(parsed);
            var elements = new java.util.ArrayList<ElementSnapshot>();
            int z = 0;
            for (var e : parsed.elements()) {
                elements.add(new ElementSnapshot((long) z + 1, PresentationElementType.valueOf(e.type()), z++,
                        e.x(), e.y(), e.width(), e.height(), BigDecimal.ZERO, e.payload(), e.style(), null));
            }
            snapshots.add(new SlideSnapshot((long) ++order, order, parsed.title(), parsed.speakerNotes(),
                    parsed.layout(), PresentationSlideGenerationState.GENERATED, elements));
        }
        mockDeck(10L, "fixed-layout-preview", snapshots, Map.of());
        Path preview = Path.of("build", "slide-layout-verification");
        Files.createDirectories(preview);
        try (PptxExportArtifact artifact = exportService.export(owner, 10L)) {
            try (var in = artifact.openStream()) {
                Files.copy(in, preview.resolve("fixed-layout-preview.pptx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream input = artifact.openStream(); XMLSlideShow reopened = new XMLSlideShow(input)) {
                assertThat(reopened.getSlides()).hasSize(5);
                BufferedImage contact = new BufferedImage(960, 540 * 5, BufferedImage.TYPE_INT_RGB);
                for (int i = 0; i < 5; i++) {
                    var slide = reopened.getSlides().get(i);
                    var text = slide.getShapes().stream().filter(XSLFTextShape.class::isInstance)
                            .map(XSLFTextShape.class::cast).filter(t -> !t.getText().isBlank()).toList();
                    String title = planned.get(i).title();
                    assertThat(text.stream().filter(t -> t.getText().equals(title)).count()).isEqualTo(1);
                    for (int a = 0; a < text.size(); a++) {
                        var box = text.get(a);
                        assertThat(box.getAnchor().getMaxX()).isLessThanOrEqualTo(960);
                        assertThat(box.getAnchor().getMaxY()).isLessThanOrEqualTo(540);
                        assertThat(box.getTextHeight()).isLessThanOrEqualTo(box.getAnchor().getHeight());
                        for (int b = a + 1; b < text.size(); b++) {
                            assertThat(box.getAnchor().intersects(text.get(b).getAnchor())).isFalse();
                        }
                    }
                    String notes = reopened.getNotesSlide(slide).getTextParagraphs().stream().flatMap(List::stream)
                            .map(XSLFTextParagraph::getText).reduce("", String::concat);
                    assertThat(notes).contains("Giải thích bằng tiếng Việt");
                    if (i < 2) assertThat(notes).contains("onDestroy()");
                    var graphics = contact.createGraphics();
                    graphics.translate(0, i * 540);
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 960, 540);
                    slide.draw(graphics); graphics.dispose();
                }
                ImageIO.write(contact, "png", preview.resolve("fixed-layout-preview.png").toFile());
            }
        }
    }

    private void mockDeck(Long deckId, String title, List<SlideSnapshot> slides, Map<Long, MediaSnapshot> media) {
        when(snapshotService.capture(owner, deckId)).thenReturn(
                new ExportSnapshot(deckId, title, owner.getId(), slides, media));
    }

    private SlideSnapshot slide(Long id, int order, String title, String notes, List<ElementSnapshot> elements) {
        return new SlideSnapshot(id, order, title, notes, PresentationSlideLayout.BLANK,
                PresentationSlideGenerationState.GENERATED, elements);
    }

    private ElementSnapshot element(Long id, PresentationElementType type, int z, double x, double y,
                                    double width, double height, double rotation,
                                    Map<String, Object> payload, Map<String, Object> style) {
        return new ElementSnapshot(id, type, z, BigDecimal.valueOf(x), BigDecimal.valueOf(y),
                BigDecimal.valueOf(width), BigDecimal.valueOf(height), BigDecimal.valueOf(rotation),
                payload, style, null);
    }

    private ElementSnapshot shape(Long id, int z, String primitive, String text, String fill) {
        return element(id, PresentationElementType.SHAPE, z, 20 + z, 30, 90, 50, 0,
                Map.of("primitive", primitive, "text", text), Map.of("fillColor", fill));
    }

    private ElementSnapshot image(Long id, int z, MediaSnapshot media) {
        return new ElementSnapshot(id, PresentationElementType.IMAGE, z, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.ZERO, Map.of(), Map.of(), media);
    }

    private MediaSnapshot media(Long id, String mimeType, byte[] bytes) {
        return new MediaSnapshot(id, 10L, owner.getId(), mimeType, bytes.length, "fixture." + mimeType.substring(6));
    }

    private MediaSnapshot media(Long id) {
        return switch (id.intValue()) {
            case 21 -> new MediaSnapshot(21L, 10L, owner.getId(), "image/png", 1L, "fixture.png");
            case 22 -> new MediaSnapshot(22L, 10L, owner.getId(), "image/jpeg", 1L, "fixture.jpeg");
            default -> new MediaSnapshot(23L, 10L, owner.getId(), "image/webp", 1L, "fixture.webp");
        };
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
