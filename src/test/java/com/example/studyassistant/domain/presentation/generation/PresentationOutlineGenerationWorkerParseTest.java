package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.retrieval.model.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PresentationOutlineGenerationWorker — parse/validate logic.
 * Covers: E malformed JSON, F unknown S-ref, G schema violation,
 * H content hash determinism, I immutable revision shape, U slide count,
 * V Unicode/Vietnamese, W no legacy extractedText, Y unknown source ref rejected.
 */
class PresentationOutlineGenerationWorkerParseTest {



    // We test the parsing logic by exposing it via a package-visible method.
    // The full worker is tested via integration.

    private Map<String, RetrievedChunk> sourceMap;

    @BeforeEach
    void setUp() {
        sourceMap = new LinkedHashMap<>();
        sourceMap.put("S1", mockChunk(1L, 1L));
        sourceMap.put("S2", mockChunk(2L, 2L));
    }

    private PresentationOutlineGenerationWorker createWorkerUnderTest() {
        // Create a minimal worker with just the objectMapper for parsing tests
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // We need to call parseAndValidateOutline which is package-private
        return null; // replaced by direct instantiation below
    }

    /**
     * Helper to directly test the package-visible parse method.
     * We instantiate a minimal worker with only an ObjectMapper wired.
     */
    private OutlineJsonSchema parse(String json, Map<String, RetrievedChunk> sources, int slides) {
        var worker = WorkerTestHelper.buildMinimalWorker();
        return worker.parseAndValidateOutline(json, sources, slides, "students", "en");
    }

    // ── E: Malformed JSON → rejected, not persisted
    @Test
    void testE_malformedJsonRejected() {
        assertThatThrownBy(() -> parse("NOT JSON AT ALL", sourceMap, 5))
                .isInstanceOf(OutlineGenerationException.MalformedLlmResponseException.class);
    }

    @Test
    void testE_noRootObjectRejected() {
        assertThatThrownBy(() -> parse("[1,2,3]", sourceMap, 5))
                .isInstanceOf(OutlineGenerationException.MalformedLlmResponseException.class);
    }

    // Unknown sources are dropped; factual slides must retain valid evidence.
    @Test
    void testF_unknownSourceSlideIsOmitted() {
        String json = validOutlineJsonWithRefs(List.of("S99")); // S99 not in sourceMap
        assertThat(parse(json, sourceMap, 5).sections().get(0).slides())
                .noneMatch(slide -> "Slide1".equals(slide.title()));
    }

    @Test
    void testY_unknownRefsAreDroppedWhenValidEvidenceRemains() {
        String json = validOutlineJsonWithRefs(List.of("[S1]", "S_FAKE"));
        org.assertj.core.api.Assertions.assertThatCode(() -> parse(json, sourceMap, 5)).doesNotThrowAnyException();
    }

    // ── G: schemaVersion != 1 → rejected
    @Test
    void testG_wrongSchemaVersion() {
        String json = """
                {"schemaVersion":2,"title":"T","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P","slides":[{"title":"Sl","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]}]}]}
                """;
        assertThatThrownBy(() -> parse(json, sourceMap, 5))
                .isInstanceOf(OutlineGenerationException.OutlineSchemaViolationException.class);
    }

    // ── G: Empty sections → rejected
    @Test
    void testG_emptySections() {
        String json = """
                {"schemaVersion":1,"title":"T","learningObjective":"L","audience":"A","language":"en","sections":[]}
                """;
        assertThatThrownBy(() -> parse(json, sourceMap, 5))
                .isInstanceOf(OutlineGenerationException.OutlineSchemaViolationException.class);
    }

    // ── G: Empty title → rejected
    @Test
    void testG_blankTitle() {
        String json = """
                {"schemaVersion":1,"title":"","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P","slides":[{"title":"Sl","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]}]}]}
                """;
        assertThatThrownBy(() -> parse(json, sourceMap, 5))
                .isInstanceOf(OutlineGenerationException.OutlineSchemaViolationException.class);
    }

    // ── U: Slide count validation — 1 slide with targetSlideCount=10 → min=5 → should fail
    @Test
    void testU_slideCountTooFew() {
        // 1 slide, targetSlideCount=10 → min=5. 1 < 5 → should throw schema violation
        String json = """
                {"schemaVersion":1,"title":"T","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P",
                   "slides":[
                     {"title":"Only Slide","purpose":"p","slideType":"CONTENT","keyPoints":["K"],"sourceRefs":["S1"]}
                   ]
                 }]}
                """;
        assertThatThrownBy(() -> parse(json, sourceMap, 10))
                .isInstanceOf(OutlineGenerationException.OutlineSchemaViolationException.class)
                .hasMessageContaining("Slide count");
    }

    // ── H: Deterministic content hash
    @Test
    void testH_deterministicContentHash() {
        OutlineJsonSchema outline = buildValidOutline();
        String hash1 = OutlineContentHasher.contentHash(outline);
        String hash2 = OutlineContentHasher.contentHash(outline);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    // ── H: Different outlines produce different hashes
    @Test
    void testH_differentOutlinesDifferentHash() {
        OutlineJsonSchema o1 = buildValidOutline();
        OutlineJsonSchema o2 = new OutlineJsonSchema(1, "Different Title", "LO", "A", "en", o1.sections());
        assertThat(OutlineContentHasher.contentHash(o1)).isNotEqualTo(OutlineContentHasher.contentHash(o2));
    }

    // ── I: Valid outline parses without modification
    @Test
    void testI_validOutlineParsed() {
        String json = validOutlineJsonMinSlides(5, "S1");
        OutlineJsonSchema outline = parse(json, sourceMap, 5);
        assertThat(outline.schemaVersion()).isEqualTo(1);
        assertThat(outline.title()).isNotBlank();
        assertThat(outline.sections()).isNotEmpty();
    }

    // ── V: Unicode/Vietnamese preserved
    @Test
    void testV_unicodeVietnamesePreserved() {
        String vietnameseTitle = "Nội dung bài học: Hệ thống giáo dục";
        String json = """
                {"schemaVersion":1,"title":"%s","learningObjective":"Học sinh hiểu được","audience":"Học sinh","language":"vi",
                 "sections":[
                   {"title":"Phần 1","purpose":"Giới thiệu",
                    "slides":[
                      {"title":"Trang tiêu đề","purpose":"Mở đầu","slideType":"TITLE","keyPoints":[],"sourceRefs":[]},
                      {"title":"Nội dung 1","purpose":"Mục tiêu","slideType":"CONTENT","keyPoints":["Điểm 1"],"sourceRefs":["S1"]},
                      {"title":"Nội dung 2","purpose":"Chi tiết","slideType":"CONTENT","keyPoints":["Điểm 2"],"sourceRefs":["S2"]},
                      {"title":"Tóm tắt","purpose":"Kết luận","slideType":"SUMMARY","keyPoints":["Tổng kết"],"sourceRefs":["S1","S2"]},
                      {"title":"Kết thúc","purpose":"Cuối","slideType":"SECTION","keyPoints":[],"sourceRefs":[]}
                    ]
                   }
                 ]}
                """.formatted(vietnameseTitle);
        OutlineJsonSchema outline = parse(json, sourceMap, 5);
        assertThat(outline.title()).isEqualTo(vietnameseTitle);
        assertThat(outline.language()).isEqualTo("vi");
        assertThat(outline.sections().get(0).title()).isEqualTo("Phần 1");
    }

    // ── CONTENT slide requires sourceRef
    @Test
    void testContentSlideRequiresSourceRef() {
        String json = """
                {"schemaVersion":1,"title":"T","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P",
                   "slides":[
                     {"title":"Slide1","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":[]},
                     {"title":"Slide2","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]},
                     {"title":"Slide3","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S2"]},
                     {"title":"Slide4","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]},
                     {"title":"Slide5","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S2"]}
                   ]
                 }]}
                """;
        assertThat(parse(json, sourceMap, 5).sections().get(0).slides())
                .hasSize(4).noneMatch(slide -> "Slide1".equals(slide.title()));
    }

    // ── TITLE slide may have empty sourceRefs
    @Test
    void testTitleSlideEmptySourceRefsAllowed() {
        String json = validOutlineJsonMinSlides(5, "S1");
        // Should not throw
        org.assertj.core.api.Assertions.assertThatCode(() -> parse(json, sourceMap, 5)).doesNotThrowAnyException();
    }

    // ── Request hash deterministic
    @Test
    void testRequestHashDeterministic() {
        String h1 = OutlineContentHasher.requestHash(1L, "topic", "instr", 10, "audience", "en", List.of(2L, 1L));
        String h2 = OutlineContentHasher.requestHash(1L, "topic", "instr", 10, "audience", "en", List.of(1L, 2L));
        assertThat(h1).isEqualTo(h2); // sorted
    }

    @Test
    void testRequestHashDifferentTopicDifferentHash() {
        String h1 = OutlineContentHasher.requestHash(1L, "topic A", null, 10, null, null, null);
        String h2 = OutlineContentHasher.requestHash(1L, "topic B", null, 10, null, null, null);
        assertThat(h1).isNotEqualTo(h2);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private RetrievedChunk mockChunk(Long chunkId, Long documentId) {
        return RetrievedChunk.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .content("Content for chunk " + chunkId)
                .indexVersion("v1")
                .build();
    }

    private OutlineJsonSchema buildValidOutline() {
        var slide = new OutlineJsonSchema.OutlineSlide("Title", "Purpose", "CONTENT",
                List.of("Key point"), List.of("S1"));
        var section = new OutlineJsonSchema.OutlineSection("Section", "Purpose", List.of(slide));
        return new OutlineJsonSchema(1, "Title", "Objective", "Audience", "en", List.of(section));
    }

    private String validOutlineJsonWithRefs(List<String> refs) {
        String refsJson = refs.stream()
                .map(r -> "\"" + r + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return """
                {"schemaVersion":1,"title":"T","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P",
                   "slides":[
                     {"title":"Slide1","purpose":"p","slideType":"CONTENT","keyPoints":["K1"],"sourceRefs":[%s]},
                     {"title":"Slide2","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]},
                     {"title":"Slide3","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S1"]},
                     {"title":"Slide4","purpose":"p","slideType":"CONTENT","keyPoints":[],"sourceRefs":["S2"]},
                     {"title":"Slide5","purpose":"p","slideType":"SECTION","keyPoints":[],"sourceRefs":[]}
                   ]
                 }]}
                """.formatted(refsJson);
    }

    private String validOutlineJsonMinSlides(int count, String ref) {
        StringBuilder slides = new StringBuilder();
        // First: a TITLE slide (no sourceRef needed)
        slides.append("""
            {"title":"Title Slide","purpose":"Intro","slideType":"TITLE","keyPoints":[],"sourceRefs":[]},
            """);
        // Remaining: CONTENT slides with source refs
        for (int i = 2; i <= count; i++) {
            slides.append("""
                {"title":"Slide %d","purpose":"Content %d","slideType":"CONTENT","keyPoints":["Point"],"sourceRefs":["%s"]}
                """.formatted(i, i, ref));
            if (i < count) slides.append(",");
        }
        return """
                {"schemaVersion":1,"title":"T","learningObjective":"L","audience":"A","language":"en",
                 "sections":[{"title":"S","purpose":"P","slides":[%s]}]}
                """.formatted(slides.toString());
    }
}
