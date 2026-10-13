package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentIndexingServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private com.example.studyassistant.service.DocumentKnowledgeGraphGenerationService knowledgeGraphGenerationService;

    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private DocumentParsingService documentParsingService;
    @Mock
    private ChunkingService chunkingService;
    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private DocumentIndexingService indexingService;

    private Path tempFile;
    private Document document;
    private String fileHash;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        tempFile = Files.createTempFile("test_doc", ".pdf");
        Files.writeString(tempFile, "dummy content");
        fileHash = (String) ReflectionTestUtils.invokeMethod(indexingService, "computeFileHash", tempFile);

        document = new Document();
        document.setId(100L);
        com.example.studyassistant.entity.User owner = new com.example.studyassistant.entity.User();
        owner.setId(1L);
        document.setUser(owner);
        document.setFilePath(tempFile.toString());
        document.setFileType("PDF");
        document.setStatus("UPLOADING");
        document.setActiveIndexVersion(null);
    }

    private DocumentChunk createMockChunk() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(10L);
        chunk.setChunkIndex(0);
        chunk.setContent("content");
        return chunk;
    }

    @Test
    void testCP4_01_02_03_04_05_FirstIndexingSuccess() throws Exception {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("content").build();
        when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        
        when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        
        when(embeddingProvider.embedBatch(anyList())).thenReturn(Collections.singletonList(Arrays.asList(0.1, 0.2)));
        
        indexingService.indexDocument(100L);
        
        assertThat(document.getStatus()).isEqualTo("READY");
        assertThat(document.getActiveIndexVersion()).isNotNull();
        assertThat(document.getContentHash()).isEqualTo(fileHash);
        verify(knowledgeGraphGenerationService).generate(1L, 100L);
        
        verify(documentChunkRepository).saveAll(anyList());
        
        verify(vectorStore).upsert(argThat(records -> 
            records.size() == 1 && records.get(0).getChunkId() == 10L
        ));
    }

    @Test
    void kgFailureAfterActivationKeepsReadyIndexAndChunks() throws Exception {
        when(knowledgeGraphGenerationService.generate(1L, 100L)).thenAnswer(inv -> {
            assertThat(document.getStatus()).isEqualTo("READY");
            assertThat(document.getActiveIndexVersion()).isNotNull();
            throw new RuntimeException("KG provider unavailable");
        });
        testCP4_01_02_03_04_05_FirstIndexingSuccess();
        verify(documentChunkRepository, never()).deleteByDocumentIdAndIndexVersion(any(), any());
        verify(vectorStore, never()).deleteByDocumentAndVersion(any(), any());
    }

    @Test
    void testCP4_06_EmbeddingFailureFirstIndex() throws Exception {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("content").build();
        when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        
        when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        
        when(embeddingProvider.embedBatch(anyList())).thenThrow(new RuntimeException("API Error"));
        
        assertThatThrownBy(() -> indexingService.indexDocument(100L)).isInstanceOf(RuntimeException.class);
        
        assertThat(document.getStatus()).isEqualTo("FAILED");
        assertThat(document.getActiveIndexVersion()).isNull();
    }

    @Test
    void testCP4_07_QdrantFailureFirstIndex() throws Exception {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        lenient().when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        lenient().when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        lenient().when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("content").build();
        lenient().when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        
        lenient().when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        
        lenient().when(embeddingProvider.embedBatch(anyList())).thenReturn(Collections.singletonList(Arrays.asList(0.1, 0.2)));
        
        doThrow(new RuntimeException("Qdrant offline")).when(vectorStore).upsert(anyList());
        
        assertThatThrownBy(() -> indexingService.indexDocument(100L)).isInstanceOf(RuntimeException.class);
        
        assertThat(document.getStatus()).isEqualTo("FAILED");
        assertThat(document.getActiveIndexVersion()).isNull();
    }

    @Test
    void testCP4_09_10_RetryUsesNewVersion() throws Exception {
        document.setStatus("FAILED");
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("content").build();
        when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        when(embeddingProvider.embedBatch(anyList())).thenReturn(Collections.singletonList(Arrays.asList(0.1, 0.2)));
        
        indexingService.indexDocument(100L);
        
        assertThat(document.getStatus()).isEqualTo("READY");
        assertThat(document.getActiveIndexVersion()).isNotNull();
    }

    @Test
    void testCP4_11_12_13_14_ReindexFailureKeepsOld() throws Exception {
        document.setStatus("READY");
        document.setActiveIndexVersion("v_old");
        document.setContentHash("old_hash");
        document.setEmbeddingModel("old_model");
        
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("new_model");
        when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("content").build();
        when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        when(embeddingProvider.embedBatch(anyList())).thenThrow(new RuntimeException("Fail"));
        
        assertThatThrownBy(() -> indexingService.indexDocument(100L)).isInstanceOf(RuntimeException.class);
        
        assertThat(document.getStatus()).isEqualTo("READY");
        assertThat(document.getActiveIndexVersion()).isEqualTo("v_old");
        assertThat(document.getContentHash()).isEqualTo("old_hash");
        assertThat(document.getEmbeddingModel()).isEqualTo("old_model");
    }

    @Test
    void testCP4_15_16_17_AlreadyIndexedNoOp() {
        document.setStatus("READY");
        document.setActiveIndexVersion("v1");
        document.setContentHash(fileHash);
        document.setEmbeddingModel("test-model");
        
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        
        indexingService.indexDocument(100L);
        
        verify(embeddingProvider, never()).embedBatch(anyList());
        verify(vectorStore, never()).upsert(anyList());
    }

    @Test
    void testCP4_18_ChangedModelTriggersReindex() throws Exception {
        document.setStatus("READY");
        document.setActiveIndexVersion("v1");
        document.setContentHash(fileHash);
        document.setEmbeddingModel("old-model");
        
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("new-model");
        when(embeddingProvider.getDimension()).thenReturn(2);
        
        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("c").build();
        when(chunkingService.chunk(parsed)).thenReturn(Collections.singletonList(draft));
        when(documentChunkRepository.saveAll(anyList())).thenReturn(Collections.singletonList(createMockChunk()));
        when(embeddingProvider.embedBatch(anyList())).thenReturn(Collections.singletonList(Arrays.asList(0.1, 0.2)));
        
        indexingService.indexDocument(100L);
        
        verify(vectorStore).upsert(anyList());
        assertThat(document.getEmbeddingModel()).isEqualTo("new-model");
    }

    @Test
    void testCP4_19_20_21_EmptyDocumentFirstIndex() throws Exception {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        lenient().when(embeddingProvider.getModelIdentifier()).thenReturn("test");
        
        ParsedDocument parsed = new ParsedDocument();
        lenient().when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        lenient().when(chunkingService.chunk(parsed)).thenReturn(Collections.emptyList());
        
        assertThatThrownBy(() -> indexingService.indexDocument(100L)).isInstanceOf(RuntimeException.class);
        
        verify(embeddingProvider, never()).embedBatch(anyList());
        verify(vectorStore, never()).upsert(anyList());
        assertThat(document.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void testCP4_25_26_27_MetadataCopied() throws Exception {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document)); lenient().when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        when(embeddingProvider.getModelIdentifier()).thenReturn("test");
        when(embeddingProvider.getDimension()).thenReturn(2);
        when(documentParsingService.parse(any(), any())).thenReturn(new ParsedDocument());
        
        ChunkDraft draft = ChunkDraft.builder()
                .chunkIndex(0)
                .content("c")
                .contentHash("hash123")
                .pageStart(1)
                .pageEnd(2)
                .sectionTitle("Sec1")
                .build();
        when(chunkingService.chunk(any())).thenReturn(Collections.singletonList(draft));
        
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        when(documentChunkRepository.saveAll(captor.capture())).thenReturn(Collections.singletonList(createMockChunk()));
        when(embeddingProvider.embedBatch(anyList())).thenReturn(Collections.singletonList(Arrays.asList(0.1, 0.2)));
        
        indexingService.indexDocument(100L);
        
        DocumentChunk saved = captor.getValue().get(0);
        assertThat(saved.getContentHash()).isEqualTo("hash123");
        assertThat(saved.getPageStart()).isEqualTo(1);
        assertThat(saved.getPageEnd()).isEqualTo(2);
        assertThat(saved.getSectionTitle()).isEqualTo("Sec1");
    }

    @Test
    void testConcurrencyOldVersionRace() {
        document.setStatus("READY");
        document.setActiveIndexVersion("OLD");
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        
        assertThatThrownBy(() -> 
            indexingService.activateIndex(100L, "B", "hash", "model", "STALE_OLD")
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("Document was updated by another process");
        
        assertThat(document.getActiveIndexVersion()).isEqualTo("OLD"); // Remains unchanged
    }

    @Test
    void testConcurrencyFirstIndexRace() {
        document.setStatus("PROCESSING");
        document.setActiveIndexVersion("A"); // A already activated it
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(document));
        
        // B expected it to be null (first index)
        assertThatThrownBy(() -> 
            indexingService.activateIndex(100L, "B", "hash", "model", null)
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("Document was updated by another process");
        
        assertThat(document.getActiveIndexVersion()).isEqualTo("A"); // Remains unchanged
    }
}
