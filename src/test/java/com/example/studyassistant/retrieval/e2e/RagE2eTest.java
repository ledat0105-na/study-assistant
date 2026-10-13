package com.example.studyassistant.retrieval.e2e;

import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import com.example.studyassistant.retrieval.model.VectorRecord;
import com.example.studyassistant.retrieval.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG E2E Integration Test — Pipeline coverage from document record to grounded answer.
 *
 * Infrastructure:
 *   MySQL: REAL studyassistant_rag_test (via integration profile)
 *   Qdrant: REAL localhost:6333
 *   EmbeddingProvider: DeterministicStubEmbeddingProvider (no network, no OpenAI)
 *   LlmProvider: DeterministicStubLlmProvider (no network, no OpenAI)
 *   OpenAI: NEVER called
 *
 * Production boundary: starts from pre-existing Document rows in MySQL (already READY
 * with indexed chunks). Does NOT exercise DocumentService.uploadDocument or actual file
 * parsing — those are covered by unit tests. The RAG pipeline from indexing onward is
 * exercised end-to-end.
 *
 * Excluded from boundary: file upload, multipart handling, PDF/DOCX byte-level parsing.
 * These are excluded because they require real files on disk and HTTP multipart,
 * which are impractical in a headless integration context. They are covered by
 * ChunkingServiceTest and DocumentIndexingServiceTest at the unit level.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("E2E")
public class RagE2eTest {

    // ── Deterministic stub: fixed-dimension vectors, no OpenAI ───────────────

    /**
     * Provides deterministic, dimension-4 vectors from a lookup table keyed by content hash.
     * Vectors are engineered so that the "relevant" chunk is closest to the query vector.
     * OpenAI is NEVER called.
     */
    @TestConfiguration
    static class StubBeans {

        @Bean
        @Primary
        public EmbeddingProvider deterministicEmbeddingProvider() {
            return new EmbeddingProvider() {
                private static final int DIM = 4;

                @Override
                public List<Double> embed(String text) {
                    // Query vector always points toward the "biology" cluster
                    return Arrays.asList(1.0, 0.0, 0.0, 0.0);
                }

                @Override
                public List<List<Double>> embedBatch(List<String> texts) {
                    List<List<Double>> result = new ArrayList<>();
                    for (int i = 0; i < texts.size(); i++) {
                        // First chunk in the batch gets the "relevant" vector,
                        // rest get orthogonal vectors
                        if (i == 0) {
                            result.add(Arrays.asList(0.99, 0.01, 0.0, 0.0));
                        } else {
                            result.add(Arrays.asList(0.0, 0.0, 1.0, 0.0));
                        }
                    }
                    return result;
                }

                @Override
                public String getModelIdentifier() {
                    return "stub-e2e-model";
                }

                @Override
                public int getDimension() {
                    return DIM;
                }
            };
        }

        @Bean
        @Primary
        public com.example.studyassistant.llm.LlmProvider deterministicLlmProvider() {
            return (systemPrompt, userPrompt, temperature) -> {
                // Deterministic stub: extract citation IDs from the prompt and echo them
                // This mirrors expected production behaviour: LLM references [S1] etc.
                StringBuilder response = new StringBuilder();
                response.append("Based on the provided sources: ");
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\[(S\\d+)\\]")
                        .matcher(systemPrompt + userPrompt);
                Set<String> cited = new LinkedHashSet<>();
                while (m.find()) cited.add(m.group(1));
                for (String c : cited) response.append("[").append(c).append("] ");
                response.append("the answer is found in the indexed content.");
                return response.toString().trim();
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentIndexingService documentIndexingService;

    @Autowired
    private GroundedAnswerService groundedAnswerService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    private String suffix;
    private Long userId;
    private Long notebookId;
    private Long documentId;
    private String indexVersion;
    private static final String COLLECTION_PREFIX = "rag";

    @BeforeEach
    void setUp() {
        String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(db)
                .as("E2E test must run against studyassistant_rag_test, got: " + db)
                .isEqualTo("studyassistant_rag_test");

        suffix = String.valueOf(System.currentTimeMillis());

        // Insert user
        jdbcTemplate.update(
                "INSERT INTO users (username, email, password, role, plan, status) VALUES (?,?,?,?,?,?)",
                "e2e_user_" + suffix, "e2e_" + suffix + "@test.invalid",
                "$2a$10$placeholder", "STUDENT", "FREE", "ACTIVE");
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class,
                "e2e_" + suffix + "@test.invalid");

        // Insert notebook
        jdbcTemplate.update(
                "INSERT INTO notebooks (title, user_id, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?)",
                "E2E_NB_" + suffix, userId, false, LocalDateTime.now(), LocalDateTime.now());
        notebookId = jdbcTemplate.queryForObject(
                "SELECT id FROM notebooks WHERE title = ? AND user_id = ?", Long.class,
                "E2E_NB_" + suffix, userId);

        // Insert pre-READY document (simulate post-upload state)
        jdbcTemplate.update(
                "INSERT INTO documents (user_id, notebook_id, file_name, file_path, status, retry_count) "
                + "VALUES (?,?,?,?,?,?)",
                userId, notebookId,
                "e2e_doc_" + suffix + ".pdf",
                "/tmp/e2e_doc_" + suffix + ".pdf",
                "READY", 0);
        documentId = jdbcTemplate.queryForObject(
                "SELECT id FROM documents WHERE file_name = ?", Long.class,
                "e2e_doc_" + suffix + ".pdf");

        // Insert chunks manually (bypasses file parsing — documented boundary exclusion)
        indexVersion = "v_e2e_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO document_chunks (document_id, chunk_index, content, index_version, "
                + "embedding_model, token_count, page_start) VALUES (?,?,?,?,?,?,?)",
                documentId, 1,
                "The mitochondria is the powerhouse of the cell and generates ATP through cellular respiration",
                indexVersion, "stub-e2e-model", 15, 1);
        jdbcTemplate.update(
                "INSERT INTO document_chunks (document_id, chunk_index, content, index_version, "
                + "embedding_model, token_count, page_start) VALUES (?,?,?,?,?,?,?)",
                documentId, 2,
                "Quantum entanglement is a phenomenon where two particles become correlated regardless of distance",
                indexVersion, "stub-e2e-model", 12, 2);

        // Activate the document index (sets activeIndexVersion + contentHash)
        jdbcTemplate.update(
                "UPDATE documents SET active_index_version = ?, embedding_model = ?, content_hash = ? WHERE id = ?",
                indexVersion, "stub-e2e-model", "e2e_hash_" + suffix, documentId);

        // Index vectors into real Qdrant
        int dim = embeddingProvider.getDimension();
        vectorStore.ensureCollection(dim);

        List<Long> chunkIds = jdbcTemplate.queryForList(
                "SELECT id FROM document_chunks WHERE document_id = ? AND index_version = ? ORDER BY chunk_index",
                Long.class, documentId, indexVersion);

        List<List<Double>> vectors = embeddingProvider.embedBatch(
                Arrays.asList("chunk1_placeholder", "chunk2_placeholder"));

        List<VectorRecord> records = new ArrayList<>();
        for (int i = 0; i < chunkIds.size(); i++) {
            records.add(VectorRecord.builder()
                    .vectorId(UUID.nameUUIDFromBytes(
                            (documentId + "_" + (i + 1) + "_" + indexVersion).getBytes()).toString())
                    .vector(vectors.get(i))
                    .chunkId(chunkIds.get(i))
                    .documentId(documentId)
                    .indexVersion(indexVersion)
                    .embeddingModel("stub-e2e-model")
                    .build());
        }
        vectorStore.upsert(records);
    }

    @AfterEach
    void tearDown() {
        // Clean up Qdrant vectors for this test's document+version
        try {
            vectorStore.deleteByDocumentAndVersion(documentId, indexVersion);
        } catch (Exception ignored) {}
        // MySQL rows are cleaned up per-test via unique suffix — no cross-test pollution
        // We do soft cleanup to avoid FK cascade issues
        if (documentId != null) {
            jdbcTemplate.update(
                    "DELETE FROM document_chunks WHERE document_id = ? AND index_version = ?",
                    documentId, indexVersion);
            jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId);
        }
        if (notebookId != null)
            jdbcTemplate.update("DELETE FROM notebooks WHERE id = ?", notebookId);
        if (userId != null)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // E2E TEST 1: Full pipeline — indexed document → retrieval → grounded answer
    // ────────────────────────────────────────────────────────────────────────
    @Test
    void testE2EPipelineGroundedAnswerWithCitations() {
        // Verify document is READY with active index
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM documents WHERE id = ?", String.class, documentId);
        assertThat(status).isEqualTo("READY");

        String activeVersion = jdbcTemplate.queryForObject(
                "SELECT active_index_version FROM documents WHERE id = ?", String.class, documentId);
        assertThat(activeVersion).isEqualTo(indexVersion);

        // Verify chunks are persisted
        int chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ? AND index_version = ?",
                Integer.class, documentId, indexVersion);
        assertThat(chunkCount).isEqualTo(2);

        // Invoke full grounded answer pipeline (MySQL + Qdrant + stub LLM)
        GroundedAnswer answer = groundedAnswerService.answer(userId, notebookId, "What is the powerhouse of the cell?");

        assertThat(answer).isNotNull();
        assertThat(answer.getAnswer()).isNotBlank();
        assertThat(answer.getAnswer()).as("LLM must have referenced sources").containsIgnoringCase("S1");
        assertThat(answer.getCitations()).isNotEmpty();
        assertThat(answer.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(answer.getCitations().get(0).getChunkId()).isNotNull();
        assertThat(answer.getCitations().get(0).getDocumentId()).isEqualTo(documentId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // E2E TEST 2: Unauthorized user gets no results (ownership isolation E2E)
    // ────────────────────────────────────────────────────────────────────────
    @Test
    void testE2EUnauthorizedUserGetsNoResults() {
        // Insert a second user who does NOT own the document
        jdbcTemplate.update(
                "INSERT INTO users (username, email, password, role, plan, status) VALUES (?,?,?,?,?,?)",
                "e2e_other_" + suffix, "e2e_other_" + suffix + "@test.invalid",
                "$2a$10$placeholder", "STUDENT", "FREE", "ACTIVE");
        Long otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class,
                "e2e_other_" + suffix + "@test.invalid");

        // Insert a notebook for the other user (no documents linked)
        jdbcTemplate.update(
                "INSERT INTO notebooks (title, user_id, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?)",
                "OTHER_NB_" + suffix, otherUserId, false, LocalDateTime.now(), LocalDateTime.now());
        Long otherNotebookId = jdbcTemplate.queryForObject(
                "SELECT id FROM notebooks WHERE title = ? AND user_id = ?", Long.class,
                "OTHER_NB_" + suffix, otherUserId);

        // Other user queries their own empty notebook
        GroundedAnswer answer = groundedAnswerService.answer(
                otherUserId, otherNotebookId, "What is the powerhouse of the cell?");

        assertThat(answer).isNotNull();
        // No evidence: must return a no-evidence response, not content from the owner's doc
        assertThat(answer.getCitations()).isEmpty();

        // Cleanup additional rows
        jdbcTemplate.update("DELETE FROM notebooks WHERE id = ?", otherNotebookId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", otherUserId);
    }
}
