package com.example.studyassistant.domain.presentation.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationScopeParserTest {

    private final GenerationScopeParser parser = new GenerationScopeParser(new ObjectMapper());

    @Test
    void resolvesFrozenDocumentsFromHistoricalSourcesWhenRequestFilterWasOmitted() {
        GenerationScopeParser.ParsedScope scope = parser.parse("""
                {
                  "notebookId": 3479,
                  "sources": [
                    {"alias":"S1","documentId":3704,"chunkId":5138,"indexVersion":"v1791196642687"},
                    {"alias":"S2","documentId":3705,"chunkId":5173,"indexVersion":"v1791374656301"},
                    {"alias":"S3","documentId":3705,"chunkId":5175,"indexVersion":"v1791374656301"}
                  ]
                }
                """);

        assertThat(scope.notebookId()).isEqualTo(3479L);
        assertThat(scope.documentIds()).containsExactly(3704L, 3705L);
    }

    @Test
    void explicitRequestedDocumentScopeTakesPrecedenceOverSourceChunkProvenance() {
        GenerationScopeParser.ParsedScope scope = parser.parse("""
                {
                  "notebookId": 3479,
                  "requestedDocumentIds": [3704, 3705],
                  "sources": [
                    {"alias":"S1","documentId":3704,"chunkId":5138,"indexVersion":"old-version"}
                  ]
                }
                """);

        assertThat(scope.documentIds()).containsExactly(3704L, 3705L);
    }

    @Test
    void rejectsSnapshotWithoutRecoverableFrozenDocumentScope() {
        assertThatThrownBy(() -> parser.parse("{\"notebookId\":3479,\"requestedDocumentIds\":[]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no frozen document scope");
    }

    @Test
    void rejectsHistoricalSourceOutsideExplicitRequestedScope() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "notebookId": 3479,
                  "requestedDocumentIds": [3704],
                  "sources": [
                    {"alias":"S1","documentId":3705,"chunkId":5173,"indexVersion":"v1"}
                  ]
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside requested document scope");
    }
}
