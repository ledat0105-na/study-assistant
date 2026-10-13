package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlideCitationRevalidationTest {

    private static final long OWNER_ID = 27L;
    private static final long NOTEBOOK_ID = 3479L;
    private static final long DOCUMENT_ID = 3704L;
    private static final String ACTIVE_VERSION = "v2";

    @Test
    void freshDifferentChunkFromFrozenDocumentAndActiveVersionPasses() {
        List<Long> frozenDocumentIds = frozenScope(9001L, "v2");
        PresentationSlideGenerationProcessor.ResolvedSource source =
                resolve("S1", 9002L, DOCUMENT_ID, ACTIVE_VERSION);

        assertThat(source.chunkId()).isEqualTo(9002L);
        assertThatCode(() -> revalidate(source, activeChunk(9002L, DOCUMENT_ID, ACTIVE_VERSION),
                activeDocument(DOCUMENT_ID, ACTIVE_VERSION), frozenDocumentIds))
                .doesNotThrowAnyException();
    }

    @Test
    void freshChunkOutsideFrozenDocumentScopeFails() {
        PresentationSlideGenerationProcessor.ResolvedSource source =
                resolve("S1", 9101L, 9999L, ACTIVE_VERSION);

        assertStale(source, activeChunk(9101L, 9999L, ACTIVE_VERSION),
                activeDocument(9999L, ACTIVE_VERSION), List.of(DOCUMENT_ID));
    }

    @Test
    void chunkWithStaleIndexVersionFails() {
        PresentationSlideGenerationProcessor.ResolvedSource source =
                resolve("S1", 9201L, DOCUMENT_ID, "v1");

        assertStale(source, activeChunk(9201L, DOCUMENT_ID, "v1"),
                activeDocument(DOCUMENT_ID, ACTIVE_VERSION), List.of(DOCUMENT_ID));
    }

    @Test
    void nonReadyDocumentFails() {
        PresentationSlideGenerationProcessor.ResolvedSource source =
                resolve("S1", 9301L, DOCUMENT_ID, ACTIVE_VERSION);
        Document document = activeDocument(DOCUMENT_ID, ACTIVE_VERSION);
        document.setStatus("ARCHIVED");

        assertStale(source, activeChunk(9301L, DOCUMENT_ID, ACTIVE_VERSION), document, List.of(DOCUMENT_ID));
    }

    @Test
    void unknownFreshAliasFails() {
        assertThatThrownBy(() -> PresentationSlideGenerationProcessor.resolveFreshSources(
                List.of("S2"), List.of(retrievedChunk(9401L, DOCUMENT_ID, ACTIVE_VERSION))))
                .isInstanceOf(SlideGenerationException.SlideSchemaViolationException.class)
                .hasMessageContaining("out of bounds");
    }

    @Test
    void freshS1ResolvesToFreshChunkNotHistoricalS1Chunk() {
        long historicalChunkId = 9501L;
        long freshChunkId = 9502L;
        List<Long> frozenDocumentIds = frozenScope(historicalChunkId, "v1");

        PresentationSlideGenerationProcessor.ResolvedSource freshSource =
                resolve("S1", freshChunkId, DOCUMENT_ID, ACTIVE_VERSION);

        assertThat(freshSource.alias()).isEqualTo("S1");
        assertThat(freshSource.chunkId()).isNotEqualTo(historicalChunkId);
        assertThat(freshSource.chunkId()).isEqualTo(freshChunkId);
        assertThatCode(() -> revalidate(freshSource,
                activeChunk(freshChunkId, DOCUMENT_ID, ACTIVE_VERSION),
                activeDocument(DOCUMENT_ID, ACTIVE_VERSION), frozenDocumentIds))
                .doesNotThrowAnyException();
    }

    private List<Long> frozenScope(long historicalChunkId, String historicalVersion) {
        GenerationScopeParser parser = new GenerationScopeParser(new ObjectMapper());
        return parser.parse("""
                {
                  "notebookId": 3479,
                  "sources": [
                    {"alias":"S1","documentId":3704,"chunkId":%d,"indexVersion":"%s"}
                  ]
                }
                """.formatted(historicalChunkId, historicalVersion)).documentIds();
    }

    private PresentationSlideGenerationProcessor.ResolvedSource resolve(
            String alias, long chunkId, long documentId, String indexVersion) {
        return PresentationSlideGenerationProcessor.resolveFreshSources(
                List.of(alias), List.of(retrievedChunk(chunkId, documentId, indexVersion))).get(0);
    }

    private RetrievedChunk retrievedChunk(long chunkId, long documentId, String indexVersion) {
        return RetrievedChunk.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .content("Fresh evidence")
                .indexVersion(indexVersion)
                .build();
    }

    private DocumentChunk activeChunk(long chunkId, long documentId, String indexVersion) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(chunkId);
        chunk.setIndexVersion(indexVersion);
        chunk.setDocument(activeDocument(documentId, indexVersion));
        return chunk;
    }

    private Document activeDocument(long documentId, String indexVersion) {
        User owner = new User();
        owner.setId(OWNER_ID);
        Notebook notebook = new Notebook();
        notebook.setId(NOTEBOOK_ID);
        Document document = new Document();
        document.setId(documentId);
        document.setUser(owner);
        document.setNotebook(notebook);
        document.setStatus("READY");
        document.setActiveIndexVersion(indexVersion);
        return document;
    }

    private void revalidate(
            PresentationSlideGenerationProcessor.ResolvedSource source,
            DocumentChunk chunk,
            Document document,
            List<Long> frozenDocumentIds) {
        PresentationSlideGenerationProcessor.validateActiveCitation(
                source, chunk, document, OWNER_ID, NOTEBOOK_ID, frozenDocumentIds);
    }

    private void assertStale(
            PresentationSlideGenerationProcessor.ResolvedSource source,
            DocumentChunk chunk,
            Document document,
            List<Long> frozenDocumentIds) {
        assertThatThrownBy(() -> revalidate(source, chunk, document, frozenDocumentIds))
                .isInstanceOf(SlideGenerationException.SlideJobFailedFastException.class)
                .satisfies(error -> assertThat(
                        ((SlideGenerationException.SlideJobFailedFastException) error).getErrorCode())
                        .isEqualTo("STALE_SOURCE"));
    }
}
