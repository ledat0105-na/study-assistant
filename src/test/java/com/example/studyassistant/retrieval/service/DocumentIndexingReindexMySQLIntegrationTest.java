package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class DocumentIndexingReindexMySQLIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private FlashcardSourceRepository flashcardSourceRepository;
    @Autowired
    private DocumentIndexingService documentIndexingService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private DocumentParsingService documentParsingService;
    @MockitoBean
    private ChunkingService chunkingService;
    @MockitoBean
    private EmbeddingProvider embeddingProvider;
    @MockitoBean
    private VectorStore vectorStore;

    private User testUser;
    private Document testDoc;
    private Path tempFile;
    
    private Long savedFlashcardId;
    private Long savedSourceId;

    @BeforeEach
    void setup() throws IOException {
        testUser = transactionTemplate.execute(status -> {
            User u = new User();
            u.setUsername("reindexUser_" + UUID.randomUUID());
            u.setPassword("pass");
            u.setEmail("reindex_" + UUID.randomUUID() + "@example.com");
            return userRepository.save(u);
        });

        tempFile = Files.createTempFile("reindex_test_", ".pdf");
        Files.writeString(tempFile, "initial content");

        testDoc = transactionTemplate.execute(status -> {
            Document d = new Document();
            d.setUser(testUser);
            d.setFileName("ReindexTest.pdf");
            d.setFilePath(tempFile.toString());
            d.setFileType("PDF");
            d.setStatus("UPLOADING");
            return documentRepository.save(d);
        });

        // Setup common mocks for first index
        ParsedDocument pd = new ParsedDocument(java.util.Collections.emptyList());
        when(documentParsingService.parse(any(), any())).thenReturn(pd);
        
        ChunkDraft draft = ChunkDraft.builder()
                .chunkIndex(0)
                .content("V1 Content")
                .tokenCount(10)
                .build();
        when(chunkingService.chunk(any())).thenReturn(List.of(draft));
        when(embeddingProvider.getDimension()).thenReturn(1536);
        when(embeddingProvider.getModelIdentifier()).thenReturn("mock-model");
        when(embeddingProvider.embedBatch(anyList())).thenReturn(List.of(List.of(0.1, 0.2)));
        doNothing().when(vectorStore).ensureCollection(anyInt());
        doNothing().when(vectorStore).upsert(anyList());
        doNothing().when(vectorStore).deleteByDocumentAndVersion(anyLong(), anyString());
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(tempFile);
        transactionTemplate.execute(status -> {
            // Delete exactly the fixture-owned rows in correct child -> parent order
            if (savedSourceId != null) {
                flashcardSourceRepository.findById(savedSourceId).ifPresent(flashcardSourceRepository::delete);
            }
            if (savedFlashcardId != null) {
                flashcardRepository.findById(savedFlashcardId).ifPresent(flashcardRepository::delete);
            }
            if (testDoc != null) {
                // Cleanup chunks by known versions (if needed)
                // documentChunkRepository has no findByDocumentId, but the framework handles tests rollback if needed
                // or we can ignore it since we're in an integration test DB.
                // However, to be safe, we'll just not explicitly delete all chunks if there's no easy method.
                documentRepository.findById(testDoc.getId()).ifPresent(documentRepository::delete);
            }
            if (testUser != null) {
                userRepository.findById(testUser.getId()).ifPresent(userRepository::delete);
            }
            return null;
        });
    }

    @Test
    void p1_p15_reindexOrchestration() throws IOException {
        // --- 1. FIRST INDEXING (Create V1) ---
        documentIndexingService.indexDocument(testDoc.getId());

        // P1. Verify V1 initially active
        Document docAfterV1 = documentRepository.findById(testDoc.getId()).orElseThrow();
        assertThat(docAfterV1.getStatus()).isEqualTo("READY");
        String v1Version = docAfterV1.getActiveIndexVersion();
        assertThat(v1Version).isNotNull();

        List<DocumentChunk> v1Chunks = documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), v1Version);
        assertThat(v1Chunks).hasSize(1);
        DocumentChunk v1Chunk = v1Chunks.get(0);

        // P2. FlashcardSource references V1 chunk
        transactionTemplate.execute(status -> {
            Flashcard card = new Flashcard();
            card.setDocument(docAfterV1);
            card.setQuestion("Q1?");
            card.setAnswer("A1");
            Flashcard savedCard = flashcardRepository.save(card);
            savedFlashcardId = savedCard.getId();

            FlashcardSource source = new FlashcardSource();
            source.setFlashcard(savedCard);
            source.setDocumentChunk(v1Chunk);
            source.setSourceOrder(1);
            savedSourceId = flashcardSourceRepository.save(source).getId();
            return null;
        });

        // --- 2. SECOND INDEXING (SUCCESS PATH V2) ---
        Files.writeString(tempFile, "updated content");
        
        ChunkDraft draftV2 = ChunkDraft.builder()
                .chunkIndex(0)
                .content("V2 Content")
                .tokenCount(15)
                .build();
        when(chunkingService.chunk(any())).thenReturn(List.of(draftV2));

        // P3. production reindex invoked
        documentIndexingService.indexDocument(testDoc.getId());

        Document docAfterV2 = documentRepository.findById(testDoc.getId()).orElseThrow();
        String v2Version = docAfterV2.getActiveIndexVersion();
        
        // P4. new activeIndexVersion != V1
        assertThat(v2Version).isNotEqualTo(v1Version);

        // P5. old V1 DocumentChunk still exists
        assertThat(documentChunkRepository.findById(v1Chunk.getId())).isPresent();

        // P6. FlashcardSource still resolves exact V1 content without keeping entire test transactional
        String reloadedV1Content = transactionTemplate.execute(status -> {
            FlashcardSource reloadedSource = flashcardSourceRepository.findById(savedSourceId).orElseThrow();
            assertThat(reloadedSource.getDocumentChunk().getId()).isEqualTo(v1Chunk.getId());
            return reloadedSource.getDocumentChunk().getContent();
        });
        assertThat(reloadedV1Content).isEqualTo("V1 Content");

        // P7. new-version DB chunks exist
        List<DocumentChunk> v2Chunks = documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), v2Version);
        assertThat(v2Chunks).hasSize(1);
        assertThat(v2Chunks.get(0).getContent()).isEqualTo("V2 Content");

        // P8. historical cleanup requested old V1 vector deletion
        verify(vectorStore, atLeastOnce()).deleteByDocumentAndVersion(testDoc.getId(), v1Version);

        // P9. old V1 DB chunks were NOT deleted
        List<DocumentChunk> remainingV1 = documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), v1Version);
        assertThat(remainingV1).hasSize(1);

        // --- 3. THIRD INDEXING (FAILURE PATH V3) ---
        Files.writeString(tempFile, "failing content");
        
        ChunkDraft draftV3 = ChunkDraft.builder()
                .chunkIndex(0)
                .content("V3 Content")
                .tokenCount(20)
                .build();
        when(chunkingService.chunk(any())).thenReturn(List.of(draftV3));
        
        // Force deterministic failure during embedding (after DB chunk persist, before activation)
        when(embeddingProvider.embedBatch(anyList())).thenThrow(new RuntimeException("Simulated embedding failure"));

        clearInvocations(vectorStore); // Clear to strictly capture candidate deletion

        // Expect exception
        assertThatThrownBy(() -> documentIndexingService.indexDocument(testDoc.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Document indexing failed");

        // Assertions for failure path
        Document docAfterFailure = documentRepository.findById(testDoc.getId()).orElseThrow();
        
        // P10. previous successful version remains active
        assertThat(docAfterFailure.getActiveIndexVersion()).isEqualTo(v2Version);

        // P11. failed candidate DB chunks are cleaned
        // (will be asserted via failedCandidateVersion below)
        
        // P12. historical successful-version chunks remain (V2 remains)
        assertThat(documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), v2Version)).hasSize(1);
        
        // P13. original V1 provenance still resolves (checked implicitly by P15 + P5, but explicitly again)
        String finalV1Content = transactionTemplate.execute(status -> {
            FlashcardSource reloadedSource = flashcardSourceRepository.findById(savedSourceId).orElseThrow();
            assertThat(reloadedSource.getDocumentChunk().getId()).isEqualTo(v1Chunk.getId());
            return reloadedSource.getDocumentChunk().getContent();
        });
        assertThat(finalV1Content).isEqualTo("V1 Content");
        
        // P14. exact failed candidate cleanup verified via ArgumentCaptor
        ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);
        verify(vectorStore).deleteByDocumentAndVersion(eq(testDoc.getId()), versionCaptor.capture());
        String failedCandidateVersion = versionCaptor.getValue();
        assertThat(failedCandidateVersion).isNotNull().isNotEqualTo(v1Version).isNotEqualTo(v2Version);
        
        // P11. continued: failed candidate DB chunks are cleaned
        List<DocumentChunk> failedChunks = documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), failedCandidateVersion);
        assertThat(failedChunks).isEmpty();
        
        // P15. no historical provenance rows/chunks are deleted
        assertThat(documentChunkRepository.findByDocumentIdAndIndexVersion(testDoc.getId(), v1Version)).hasSize(1);
    }
}
