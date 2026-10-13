package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PresentationSlideGenerationProcessorParseTest {

    private PresentationSlideGenerationProcessor processor;
    private ObjectMapper objectMapper;
    private List<RetrievedChunk> evidence;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new PresentationSlideGenerationProcessor(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, objectMapper
        );

        RetrievedChunk mockChunk = RetrievedChunk.builder()
                .chunkId(100L)
                .documentId(10L)
                .content("Evidence for S1")
                .indexVersion("v1")
                .build();
        evidence = List.of(mockChunk);
    }

    @Test
    void bracketedSourcesAreNormalizedAndUnknownSourcesDropped() throws Exception {
        String json = """
            {"title":"T","layout":"TITLE_CONTENT","elements":[],"sourceRefs":[" [S1] ","S99","INVALID","S1"]}
            """;
        assertThat(processor.parseAndValidateSlideJson(json,evidence,"CONTENT").sourceRefs()).containsExactly("S1");
        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(json,List.of(),"CONTENT"))
            .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class);
    }

    @Test
    @DisplayName("D3: Prompt contains exact allowed top-level contract and explicitly forbids 'content', 'body', 'slides', 'data'")
    void testD3_SystemPromptContainsContractRules() {
        String prompt = processor.buildSystemPrompt();

        assertThat(prompt).contains("title");
        assertThat(prompt).contains("layout");
        assertThat(prompt).contains("speakerNotes");
        assertThat(prompt).contains("elements");
        assertThat(prompt).contains("sourceRefs");

        assertThat(prompt).contains("content");
        assertThat(prompt).contains("body");
        assertThat(prompt).contains("slides");
        assertThat(prompt).contains("data");
        assertThat(prompt).contains("Forbidden top-level keys");
    }

    @Test
    @DisplayName("D1: Provider response with top-level 'content' -> rejected with SlideSchemaViolationException")
    void testD1_TopLevelContentFieldRejected() {
        String jsonWithContent = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "speakerNotes": "Notes",
              "content": "This is extra content field",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Hello" },
                  "style": { "fontSizePt": 16 },
                  "x": 10, "y": 10, "width": 100, "height": 50
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonWithContent, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("Unknown top-level field: content");
    }

    @Test
    @DisplayName("D2: Canonical response -> accepted successfully")
    void testD2_CanonicalResponseAccepted() throws Exception {
        String canonicalJson = """
            {
              "title": "Introduction to RAG",
              "layout": "TITLE_CONTENT",
              "speakerNotes": "Overview notes",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Introduction to RAG" },
                  "style": { "fontSizePt": 24, "bold": true, "color": "#1E293B" },
                  "x": 60,
                  "y": 40,
                  "width": 840,
                  "height": 60
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;

        ParsedSlideResult result = processor.parseAndValidateSlideJson(canonicalJson, evidence, "TITLE_CONTENT");
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Introduction to RAG");
        assertThat(result.layout()).isEqualTo(com.example.studyassistant.domain.presentation.PresentationSlideLayout.TITLE_CONTENT);
        assertThat(result.elements()).hasSize(1);
        assertThat(result.sourceRefs()).containsExactly("S1");
    }

    @Test
    @DisplayName("D2 (fenced): Markdown code fences stripped and valid JSON parsed")
    void testD2_MarkdownCodeFencesStripped() throws Exception {
        String fencedJson = """
            ```json
            {
              "title": "Fenced Title",
              "layout": "TITLE_CONTENT",
              "speakerNotes": "Notes",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Fenced text" },
                  "style": { "fontSizePt": 14 },
                  "x": 10, "y": 10, "width": 200, "height": 100
                }
              ],
              "sourceRefs": ["S1"]
            }
            ```
            """;

        ParsedSlideResult result = processor.parseAndValidateSlideJson(fencedJson, evidence, "TITLE_CONTENT");
        assertThat(result.title()).isEqualTo("Fenced Title");
    }

    @Test
    @DisplayName("D4: sourceRefs out of bounds -> rejected (fail closed)")
    void testD4_SourceRefOutOfBoundsRejected() {
        String jsonWithInvalidRef = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Hello" },
                  "style": { "fontSizePt": 16 },
                  "x": 10, "y": 10, "width": 100, "height": 50
                }
              ],
              "sourceRefs": ["S99"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonWithInvalidRef, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("Content requires evidence");
    }

    @Test
    @DisplayName("D4: sourceRefs invalid format -> rejected (fail closed)")
    void testD4_InvalidSourceRefFormatRejected() {
        String jsonWithBadFormatRef = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Hello" },
                  "style": { "fontSizePt": 16 },
                  "x": 10, "y": 10, "width": 100, "height": 50
                }
              ],
              "sourceRefs": ["INVALID_REF"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonWithBadFormatRef, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("Content requires evidence");
    }

    @Test
    @DisplayName("D5: Invalid element geometry (out of bounds canvas) -> rejected")
    void testD5_InvalidElementGeometryRejected() {
        String jsonOutOfBounds = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Hello" },
                  "style": { "fontSizePt": 16 },
                  "x": 900, "y": 10, "width": 200, "height": 50
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonOutOfBounds, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class);
    }

    @Test
    @DisplayName("D5: Invalid element payload (missing text for TEXT type) -> rejected")
    void testD5_MissingTextPayloadRejected() {
        String jsonMissingText = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": {},
                  "style": { "fontSizePt": 16 },
                  "x": 10, "y": 10, "width": 100, "height": 50
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonMissingText, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class);
    }

    @Test
    @DisplayName("D5: Unknown element type -> rejected")
    void testD5_UnknownElementTypeRejected() {
        String jsonUnknownType = """
            {
              "title": "Title",
              "layout": "TITLE_CONTENT",
              "elements": [
                {
                  "type": "IMAGE",
                  "payload": { "url": "http://example.com/img.png" },
                  "style": {},
                  "x": 10, "y": 10, "width": 100, "height": 50
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;

        assertThatThrownBy(() -> processor.parseAndValidateSlideJson(jsonUnknownType, evidence, "TITLE_CONTENT"))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("Unknown element type");
    }
}
