package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 1: DocumentChunk entity + repository integration tests.
 *
 * Requires a live MySQL connection (same as SecurityIntegrationTest — no H2 in this project).
 *
 * FULLTEXT index limitation:
 *   MySQL FULLTEXT index creation is a DDL operation outside JPA/Hibernate's
 *   standard annotation support. The FULLTEXT(content) index will be applied
 *   via the SQL migration script (V1__rag_foundation.sql) and verified at
 *   application startup. It cannot be tested here in a @Transactional context.
 *
 * Duplicate constraint test (CP1-04a) is handled separately in
 * DocumentChunkDuplicateTest (non-transactional) because a DataIntegrityViolationException
 * from saveAndFlush invalidates the current transaction, preventing further assertions.
 */
@SpringBootTest
@Transactional
@org.junit.jupiter.api.Tag("Integration")
public class DocumentChunkRepositoryTest {

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Document savedDoc;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.currentTimeMillis());

        User user = new User();
        user.setEmail("chunk_test_" + unique + "@example.com");
        user.setPassword("$2a$10$hashedplaceholder");
        user.setFullName("Chunk Test User");
        User savedUser = userRepository.save(user);

        Document doc = new Document();
        doc.setUser(savedUser);
        doc.setFileName("Test Document");
        doc.setFilePath("/tmp/test_" + unique + ".pdf");
        doc.setFileType("PDF");
        // contentHash, activeIndexVersion, embeddingModel intentionally null — backward compat
        savedDoc = documentRepository.save(doc);
    }

    @Test
    @DisplayName("CP1-01: DocumentChunk can be persisted")
    void testPersistDocumentChunk() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(savedDoc);
        chunk.setChunkIndex(1);
        chunk.setContent("Sample content for RAG chunk");
        chunk.setTokenCount(10);
        chunk.setIndexVersion("v-cp1-test");

        DocumentChunk saved = documentChunkRepository.saveAndFlush(chunk);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("Sample content for RAG chunk");
        assertThat(saved.getDocument().getId()).isEqualTo(savedDoc.getId());
    }

    @Test
    @DisplayName("CP1-02: pageStart, pageEnd, sectionTitle are nullable")
    void testNullableFieldsWorkCorrectly() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(savedDoc);
        chunk.setChunkIndex(1);
        chunk.setContent("Nullable fields test");
        chunk.setTokenCount(15);
        chunk.setIndexVersion("v-cp1-nullable");

        DocumentChunk saved = documentChunkRepository.saveAndFlush(chunk);

        assertThat(saved.getPageStart()).isNull();
        assertThat(saved.getPageEnd()).isNull();
        assertThat(saved.getSectionTitle()).isNull();
    }

    @Test
    @DisplayName("CP1-03: Multiple chunks for same document/indexVersion persist correctly")
    void testMultipleChunksSameDocumentSameVersion() {
        DocumentChunk chunk1 = new DocumentChunk();
        chunk1.setDocument(savedDoc);
        chunk1.setChunkIndex(1);
        chunk1.setContent("Chunk 1 content");
        chunk1.setTokenCount(5);
        chunk1.setIndexVersion("v-cp1-multi");

        DocumentChunk chunk2 = new DocumentChunk();
        chunk2.setDocument(savedDoc);
        chunk2.setChunkIndex(2);
        chunk2.setContent("Chunk 2 content");
        chunk2.setTokenCount(5);
        chunk2.setIndexVersion("v-cp1-multi");

        documentChunkRepository.save(chunk1);
        documentChunkRepository.save(chunk2);
        documentChunkRepository.flush();

        List<DocumentChunk> chunks = documentChunkRepository
                .findByDocumentIdAndIndexVersion(savedDoc.getId(), "v-cp1-multi");
        assertThat(chunks).hasSize(2);
    }

    @Test
    @DisplayName("CP1-04: Same chunkIndex under DIFFERENT indexVersion is allowed")
    void testSameChunkIndexUnderDifferentVersionAllowed() {
        DocumentChunk chunk1 = new DocumentChunk();
        chunk1.setDocument(savedDoc);
        chunk1.setChunkIndex(1);
        chunk1.setContent("Chunk 1 from version A");
        chunk1.setTokenCount(5);
        chunk1.setIndexVersion("v-cp1-verA");
        documentChunkRepository.saveAndFlush(chunk1);

        DocumentChunk chunk2 = new DocumentChunk();
        chunk2.setDocument(savedDoc);
        chunk2.setChunkIndex(1); // Same chunkIndex
        chunk2.setContent("Chunk 1 from version B");
        chunk2.setTokenCount(5);
        chunk2.setIndexVersion("v-cp1-verB"); // Different version — must NOT throw
        documentChunkRepository.saveAndFlush(chunk2);

        List<DocumentChunk> vA = documentChunkRepository
                .findByDocumentIdAndIndexVersion(savedDoc.getId(), "v-cp1-verA");
        List<DocumentChunk> vB = documentChunkRepository
                .findByDocumentIdAndIndexVersion(savedDoc.getId(), "v-cp1-verB");
        assertThat(vA).hasSize(1);
        assertThat(vB).hasSize(1);
    }

    @Test
    @DisplayName("CP1-05: Existing Document has null RAG fields (backward compatibility)")
    void testExistingDocumentHasNullRagFields() {
        Document reloaded = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(reloaded.getContentHash()).isNull();
        assertThat(reloaded.getActiveIndexVersion()).isNull();
        assertThat(reloaded.getEmbeddingModel()).isNull();
        // status and other existing fields still work normally
        assertThat(reloaded.getStatus()).isEqualTo("UPLOADING");
        assertThat(reloaded.getFileName()).isEqualTo("Test Document");
    }

    @Test
    @DisplayName("CP1-06: Repository can retrieve chunks by document + indexVersion")
    void testFindByDocumentIdAndIndexVersion() {
        DocumentChunk c1 = new DocumentChunk();
        c1.setDocument(savedDoc);
        c1.setChunkIndex(1);
        c1.setContent("Retrieval test chunk content");
        c1.setTokenCount(8);
        c1.setIndexVersion("v-cp1-retrieve");
        documentChunkRepository.saveAndFlush(c1);

        List<DocumentChunk> result = documentChunkRepository
                .findByDocumentIdAndIndexVersion(savedDoc.getId(), "v-cp1-retrieve");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Retrieval test chunk content");

        // Wrong version must return empty
        List<DocumentChunk> wrong = documentChunkRepository
                .findByDocumentIdAndIndexVersion(savedDoc.getId(), "v-NONEXISTENT");
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("CP1-07: countByDocumentIdAndIndexVersion returns correct count")
    void testCountByDocumentIdAndIndexVersion() {
        for (int i = 1; i <= 3; i++) {
            DocumentChunk c = new DocumentChunk();
            c.setDocument(savedDoc);
            c.setChunkIndex(i);
            c.setContent("Count test chunk " + i);
            c.setTokenCount(4);
            c.setIndexVersion("v-cp1-count");
            documentChunkRepository.save(c);
        }
        documentChunkRepository.flush();

        long count = documentChunkRepository
                .countByDocumentIdAndIndexVersion(savedDoc.getId(), "v-cp1-count");
        assertThat(count).isEqualTo(3);

        // Different version returns 0
        long countOther = documentChunkRepository
                .countByDocumentIdAndIndexVersion(savedDoc.getId(), "v-OTHER");
        assertThat(countOther).isEqualTo(0);
    }

    /**
     * CP1-08: Regression guard for LazyInitializationException.
     *
     * Proves that findById eagerly fetches Document (via @EntityGraph) so that
     * chunk.getDocument().getFileName() is accessible after the persistence
     * context is explicitly cleared — exactly as RetrievalService.selectContext()
     * does after its repository call returns.
     *
     * Without @EntityGraph on findById, this test would throw
     * LazyInitializationException on entityManager.clear() + getFileName().
     */
    @Test
    @DisplayName("CP1-08: findById fetches Document eagerly — getFileName() survives persistence context clear")
    void testFindByIdEagerlyFetchesDocument() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(savedDoc);
        chunk.setChunkIndex(1);
        chunk.setContent("Eager fetch regression test");
        chunk.setTokenCount(5);
        chunk.setIndexVersion("v-cp1-eager");
        DocumentChunk saved = documentChunkRepository.saveAndFlush(chunk);
        Long chunkId = saved.getId();

        // Load via the @EntityGraph-annotated findById (Document is JOIN-fetched eagerly).
        DocumentChunk loaded = documentChunkRepository.findById(chunkId).orElseThrow();

        // Detach the entity from the persistence context — simulates the state
        // RetrievalService.selectContext() is in when it calls getDocument().getFileName().
        // @Transactional on this test class does NOT protect this assertion:
        // entityManager.clear() evicts the managed entity, making loaded detached.
        entityManager.clear();

        // Must NOT throw LazyInitializationException.
        // If findById still used the default (no @EntityGraph), this would fail.
        String fileName = loaded.getDocument().getFileName();
        assertThat(fileName).isEqualTo("Test Document");
    }
}
