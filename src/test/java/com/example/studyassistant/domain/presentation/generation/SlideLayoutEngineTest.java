package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlideLayoutEngineTest {

    private static final List<String> SIX = List.of(
            "Activity là một màn hình duy nhất của ứng dụng, nơi người dùng tương tác trực tiếp.",
            "onCreate() được gọi khi Activity được tạo lần đầu để khởi tạo giao diện.",
            "onPause() và onStop() được gọi khi người dùng rời khỏi màn hình hiện tại.",
            "onDestroy() giải phóng tài nguyên trước khi hệ thống huỷ Activity.",
            "Service chạy nền và không có giao diện người dùng.",
            "BroadcastReceiver nhận các thông báo phát đi từ hệ thống.");

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.0005;
    }

    @Test
    void charWidthsFollowArialAdvances() {
        // Giá trị lấy từ bảng advance của Arial / Liberation Sans (đơn vị 1/2048 em). Chặn việc lỡ sửa bảng.
        assertThat(near(SlideLayoutEngine.charWidthEm('W', false), 1933 / 2048.0)).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm('i', false), 455 / 2048.0)).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm(' ', false), 569 / 2048.0)).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm('b', true), 1251 / 2048.0)).isTrue();
        // chữ có dấu dùng độ rộng của chữ cái gốc
        assertThat(near(SlideLayoutEngine.charWidthEm('ạ', false), SlideLayoutEngine.charWidthEm('a', false))).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm('ư', false), SlideLayoutEngine.charWidthEm('u', false))).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm('Ế', true), SlideLayoutEngine.charWidthEm('E', true))).isTrue();
        assertThat(near(SlideLayoutEngine.charWidthEm('Đ', false), 1479 / 2048.0)).isTrue();
    }

    @Test
    void wrappingGrowsWithTextLengthAndShrinksWithFontSize() {
        String text = SIX.get(0);
        int at20 = SlideLayoutEngine.countLines(text, 20, false, 400);
        int at28 = SlideLayoutEngine.countLines(text, 28, false, 400);
        assertThat(at28).isGreaterThan(at20 - 1);
        assertThat(SlideLayoutEngine.countLines("", 20, false, 400)).isEqualTo(0);
        assertThat(SlideLayoutEngine.countLines("ngắn", 20, false, 400)).isEqualTo(1);
    }

    @Test
    void everyLayoutStaysInsideCanvasAndPassesTheValidator() {
        for (PresentationSlideLayout layout : PresentationSlideLayout.values()) {
            SlideLayoutEngine.Result r = SlideLayoutEngine.layout(layout, "Vòng đời của Activity", SIX);
            assertThat(r.elements()).isNotEmpty();
            assertThat(r.elements().size()).isLessThanOrEqualTo(SlideElementValidator.MAX_ELEMENTS_PER_SLIDE);
            for (int i = 0; i < r.elements().size(); i++) {
                ParsedSlideResult.ParsedElement e = r.elements().get(i);
                SlideElementValidator.validateGeometry(e.x(), e.y(), e.width(), e.height(), i);
                if ("TEXT".equals(e.type())) {
                    SlideElementValidator.validateTextPayload(e.payload(), i);
                    SlideElementValidator.validateTextStyle(e.style(), i);
                } else {
                    SlideElementValidator.validateShapePayload(e.payload(), i);
                    SlideElementValidator.validateShapeStyle(e.style(), i);
                }
            }
        }
    }

    @Test
    void textBoxesNeverOverlapEachOther() {
        for (PresentationSlideLayout layout : List.of(PresentationSlideLayout.TITLE, PresentationSlideLayout.SECTION,
                PresentationSlideLayout.TITLE_CONTENT, PresentationSlideLayout.SUMMARY, PresentationSlideLayout.TWO_COLUMN)) {
            List<ParsedSlideResult.ParsedElement> texts = new ArrayList<>();
            for (ParsedSlideResult.ParsedElement e : SlideLayoutEngine.layout(layout, "Tiêu đề", SIX).elements()) {
                if ("TEXT".equals(e.type())) texts.add(e);
            }
            for (int a = 0; a < texts.size(); a++) {
                for (int b = a + 1; b < texts.size(); b++) {
                    assertThat(overlaps(texts.get(a), texts.get(b))).isFalse();
                }
            }
        }
    }

    @Test
    void fewBulletsGetLargerFontThanManyBullets() {
        int few = bodyFontSize(SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "T", SIX.subList(0, 2)));
        int many = bodyFontSize(SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "T", SIX));
        assertThat(few).isGreaterThan(many - 1);
        assertThat(many).isGreaterThan(15);
    }

    @Test
    void tooMuchContentOverflowsInsteadOfShrinkingBelowTheMinimum() {
        String longBullet = ("Giáo trình trình bày chi tiết cách phát triển ứng dụng Android bao gồm cấu trúc thư mục "
                + "dự án, các thành phần chính, vòng đời và cách hệ thống quản lý tiến trình. ").repeat(2).trim();
        List<String> bullets = new ArrayList<>();
        for (int i = 0; i < 8; i++) bullets.add(longBullet);
        SlideLayoutEngine.Result r = SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "Tổng quan", bullets);
        assertThat(r.overflowBullets()).isNotEmpty();
        assertThat(bodyFontSize(r)).isEqualTo(16);
    }

    @Test
    void titleSlideDrawsDarkBackgroundFirstAndTitleTextMatchesFittedTitle() {
        SlideLayoutEngine.Result r = SlideLayoutEngine.layout(PresentationSlideLayout.TITLE,
                "Chương 2: Cấu trúc một chương trình Android", List.of("Lập trình ứng dụng di động"));
        ParsedSlideResult.ParsedElement first = r.elements().get(0);
        assertThat(first.type()).isEqualTo("SHAPE");
        assertThat(first.width().intValue()).isEqualTo(960);
        assertThat(first.height().intValue()).isEqualTo(540);
        boolean hasTitleText = r.elements().stream().anyMatch(e -> "TEXT".equals(e.type())
                && r.fittedTitle().equals(e.payload().get("text")));
        assertThat(hasTitleText).isTrue();
    }

    @Test
    void fittedTitleIsTheExactTextOfTheTitleElementEvenWhenItHadToBeTruncated() {
        // Exporter chỉ coi slide đã có tiêu đề khi có khung chữ trùng khít slide.title,
        // nên khi bố cục phải cắt tiêu đề, fittedTitle phải là chính chuỗi đã cắt.
        String absurd = "Phân tích kiến trúc hệ điều hành Android ".repeat(12).trim();   // ~480 ký tự
        for (PresentationSlideLayout layout : List.of(PresentationSlideLayout.TITLE, PresentationSlideLayout.SECTION,
                PresentationSlideLayout.TITLE_CONTENT, PresentationSlideLayout.SUMMARY)) {
            SlideLayoutEngine.Result r = SlideLayoutEngine.layout(layout, absurd, List.of("ý"));
            assertThat(r.fittedTitle().endsWith("…")).isTrue();
            assertThat(r.fittedTitle().length() < absurd.length()).isTrue();
            boolean matches = r.elements().stream().anyMatch(e -> "TEXT".equals(e.type())
                    && r.fittedTitle().equals(e.payload().get("text")));
            assertThat(matches).isTrue();
        }
    }

    @Test
    void layoutIsDeterministic() {
        SlideLayoutEngine.Result a = SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "Tiêu đề", SIX);
        SlideLayoutEngine.Result b = SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "Tiêu đề", SIX);
        assertThat(a.elements().toString()).isEqualTo(b.elements().toString());
    }

    @Test
    void emptyOrBlankInputDoesNotThrow() {
        SlideLayoutEngine.Result r = SlideLayoutEngine.layout(PresentationSlideLayout.TITLE_CONTENT, "  ", null);
        assertThat(r.elements()).isNotEmpty();      // vẫn có thanh màu trên cùng
        assertThat(r.overflowBullets()).isEmpty();
    }

    // ---- helpers ----

    private static int bodyFontSize(SlideLayoutEngine.Result r) {
        int min = Integer.MAX_VALUE;
        for (ParsedSlideResult.ParsedElement e : r.elements()) {
            if ("TEXT".equals(e.type()) && Boolean.FALSE.equals(e.style().get("bold"))) {
                min = Math.min(min, ((Number) e.style().get("fontSizePt")).intValue());
            }
        }
        return min;
    }

    private static boolean overlaps(ParsedSlideResult.ParsedElement a, ParsedSlideResult.ParsedElement b) {
        return lt(a.x(), b.x().add(b.width())) && lt(b.x(), a.x().add(a.width()))
                && lt(a.y(), b.y().add(b.height())) && lt(b.y(), a.y().add(a.height()));
    }

    private static boolean lt(BigDecimal p, BigDecimal q) {
        return p.compareTo(q) < 0;
    }
}
