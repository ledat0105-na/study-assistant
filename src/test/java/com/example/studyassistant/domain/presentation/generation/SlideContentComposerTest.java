package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationSlideLayout;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlideContentComposerTest {

    private static Map<String, Object> content(String title, Object bullets, String notes, List<String> refs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("bullets", bullets);
        m.put("speakerNotes", notes);
        m.put("sourceRefs", refs);
        return m;
    }

    private static boolean hasText(ParsedSlideResult r, String text) {
        return r.elements().stream().anyMatch(e -> "TEXT".equals(e.type()) && text.equals(e.payload().get("text")));
    }

    @Test
    void mapsPlannedTypeToServerOwnedLayout() {
        assertThat(SlideContentComposer.layoutFor("TITLE")).isEqualTo(PresentationSlideLayout.TITLE);
        assertThat(SlideContentComposer.layoutFor("SECTION")).isEqualTo(PresentationSlideLayout.SECTION);
        assertThat(SlideContentComposer.layoutFor("CONTENT")).isEqualTo(PresentationSlideLayout.TITLE_CONTENT);
        assertThat(SlideContentComposer.layoutFor("SUMMARY")).isEqualTo(PresentationSlideLayout.SUMMARY);
    }

    @Test
    void normalizesSourceRefsAndDropsUnknownOnes() {
        ParsedSlideResult r = SlideContentComposer.compose(
                content("Intent", List.of("Intent khởi chạy Activity"), null, List.of("[S1]", " s2 ", "S9", "[S2]", "abc")),
                3, "CONTENT", "x");
        assertThat(r.sourceRefs()).isEqualTo(List.of("S1", "S2"));
    }

    @Test
    void stripsBulletMarkersButNotLeadingDigitsInsideWords() {
        ParsedSlideResult r = SlideContentComposer.compose(
                content("Mạng", List.of("• Mạng 5G", "1. Mục thứ nhất", "- Mục gạch ngang", "2G là mạng cũ", "   "),
                        null, List.of("S1")), 2, "CONTENT", "x");
        assertThat(hasText(r, "Mạng 5G")).isTrue();
        assertThat(hasText(r, "Mục thứ nhất")).isTrue();
        assertThat(hasText(r, "Mục gạch ngang")).isTrue();
        assertThat(hasText(r, "2G là mạng cũ")).isTrue();
    }

    @Test
    void acceptsBulletsReturnedAsOneMultilineString() {
        ParsedSlideResult r = SlideContentComposer.compose(
                content("T", "Ý thứ nhất\nÝ thứ hai", null, List.of("S1")), 1, "CONTENT", "x");
        assertThat(hasText(r, "Ý thứ nhất")).isTrue();
        assertThat(hasText(r, "Ý thứ hai")).isTrue();
    }

    @Test
    void contentSlideStillRequiresValidEvidence() {
        assertThatThrownBy(() -> SlideContentComposer.compose(content("T", List.of("a"), null, List.of()), 5, "CONTENT", "x"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("requires evidence");
        assertThatThrownBy(() -> SlideContentComposer.compose(content("T", List.of("a"), null, List.of("S9", "[S0]")), 5, "CONTENT", "x"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("requires evidence");
        assertThatThrownBy(() -> SlideContentComposer.compose(content("T", List.of(), null, List.of("S1")), 5, "SUMMARY", "x"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("no bullets");
    }

    @Test
    void titleAndSectionSlidesNeedNoEvidence() {
        ParsedSlideResult t = SlideContentComposer.compose(content("Chương 2", List.of("Phụ đề"), null, List.of()), 5, "TITLE", "x");
        ParsedSlideResult s = SlideContentComposer.compose(content("Phần 1", List.of(), null, List.of()), 5, "SECTION", "x");
        assertThat(t.layout()).isEqualTo(PresentationSlideLayout.TITLE);
        assertThat(s.layout()).isEqualTo(PresentationSlideLayout.SECTION);
        assertThat(t.sourceRefs()).isEmpty();
    }

    @Test
    void fallsBackToPlannedTitleAndFailsWhenThereIsNone() {
        ParsedSlideResult r = SlideContentComposer.compose(content("", List.of("a"), null, List.of("S1")), 1, "CONTENT", "Tiêu đề dự phòng");
        assertThat(r.title()).isEqualTo("Tiêu đề dự phòng");
        assertThatThrownBy(() -> SlideContentComposer.compose(content("", List.of("a"), null, List.of("S1")), 1, "CONTENT", " "))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("title is required");
    }

    @Test
    void ignoresUnknownKeysInsteadOfFailingTheWholeSlide() {
        Map<String, Object> m = content("T", List.of("Một ý"), null, List.of("S1"));
        m.put("layout", "FANCY");
        m.put("elements", List.of());
        ParsedSlideResult r = SlideContentComposer.compose(m, 1, "CONTENT", "x");
        assertThat(r.layout()).isEqualTo(PresentationSlideLayout.TITLE_CONTENT);   // layout do server quyết, không theo AI
    }

    @Test
    void slideTitleAlwaysMatchesATextElementSoExporterAddsNoSecondTitle() {
        String worstCase = "WWWW MMMM ".repeat(12).trim();
        for (String type : List.of("TITLE", "SECTION", "CONTENT", "SUMMARY")) {
            ParsedSlideResult r = SlideContentComposer.compose(content(worstCase, List.of("ý"), null, List.of("S1")), 1, type, "x");
            assertThat(hasText(r, r.title().trim())).isTrue();
        }
    }

    @Test
    void overflowingBulletsAreKeptInSpeakerNotes() {
        String big = ("Giáo trình trình bày chi tiết cách phát triển ứng dụng Android bao gồm cấu trúc thư mục dự án. ").repeat(3);
        List<String> bullets = List.of(big, big, big, big, big, big, big, big);
        ParsedSlideResult r = SlideContentComposer.compose(content("Tổng quan", bullets, "Ghi chú gốc", List.of("S1")), 1, "CONTENT", "x");
        assertThat(r.speakerNotes()).contains("Ghi chú gốc");
        assertThat(r.speakerNotes()).contains("Nội dung bổ sung");
    }

    @Test
    void extractJsonStripsCodeFencesAndProse() {
        assertThat(SlideContentComposer.extractJson("Kết quả:\n```json\n{\"title\":\"A\"}\n```")).isEqualTo("{\"title\":\"A\"}");
        assertThat(SlideContentComposer.extractJson("  {\"a\":1}  ")).isEqualTo("{\"a\":1}");
    }

    @Test
    void promptDoesNotAskTheModelForGeometry() {
        String p = SlideContentComposer.systemPrompt();
        assertThat(p).contains("sourceRefs");
        assertThat(p).contains("NEVER output coordinates");
    }
    @Test
    void twoColumnPlanIsHonored() {
        var result = SlideContentComposer.compose(content("Hai cột", List.of("Trái", "Phải"), null, List.of("S1")),
                1, "TWO_COLUMN", "x");
        assertThat(result.layout()).isEqualTo(PresentationSlideLayout.TWO_COLUMN);
    }

    @Test
    void textClippedBeforeLayoutAndBulletsBeyondEightSurviveInNotes() {
        String longBullet = "Giải thích đầy đủ về vòng đời của Activity. ".repeat(10);
        var bullets = new java.util.ArrayList<String>();
        bullets.add(longBullet);
        for (int i = 2; i <= 9; i++) bullets.add("Ý số " + i);
        var result = SlideContentComposer.compose(content("Nội dung", bullets, "Ghi chú gốc", List.of("S1")),
                1, "CONTENT", "x");
        assertThat(result.speakerNotes()).contains(longBullet.trim(), "Ý số 9", "Ghi chú gốc");
    }

    @Test
    void sectionExtraBulletsAndLongNotesArePreserved() {
        String notes = "Ghi chú dài. ".repeat(400);
        var result = SlideContentComposer.compose(content("Chương", List.of("Phụ đề", "Ý bổ sung"), notes, List.of()),
                0, "SECTION", "x");
        assertThat(result.speakerNotes()).contains(notes.trim(), "Ý bổ sung");
    }

}
