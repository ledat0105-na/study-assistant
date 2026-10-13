package com.example.studyassistant.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import com.example.studyassistant.retrieval.service.ChunkingService;
import com.example.studyassistant.retrieval.service.DocumentIndexingService;
import com.example.studyassistant.retrieval.service.DocumentParsingService;
import com.example.studyassistant.retrieval.service.EmbeddingProvider;
import com.example.studyassistant.retrieval.service.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentIndexingIntegrationFlowTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private com.example.studyassistant.repository.ConceptRepository conceptRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotebookRepository notebookRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private AiService aiService;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private DocumentParsingService documentParsingService;
    @Mock private ChunkingService chunkingService;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private VectorStore vectorStore;
    @Mock private QuotaService quotaService;
    @Mock private DocumentKnowledgeGraphGenerationService knowledgeGraphGenerationService;

    @InjectMocks private DocumentIndexingService documentIndexingService;
    @InjectMocks private DocumentService documentService;

    private User owner;
    private User foreignUser;
    private Notebook ownerNotebook;
    private Notebook foreignNotebook;
    private Document ownerDoc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentService, "documentIndexingService", documentIndexingService);
        ReflectionTestUtils.setField(documentService, "documentParsingService", documentParsingService);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        owner = new User();
        owner.setId(1L);
        owner.setPlan("FREE");

        foreignUser = new User();
        foreignUser.setId(2L);
        foreignUser.setPlan("FREE");

        ownerNotebook = new Notebook();
        ownerNotebook.setId(10L);
        ownerNotebook.setUser(owner);
        ownerNotebook.setIsDeleted(false);

        foreignNotebook = new Notebook();
        foreignNotebook.setId(20L);
        foreignNotebook.setUser(foreignUser);
        foreignNotebook.setIsDeleted(false);

        ownerDoc = new Document();
        ownerDoc.setId(100L);
        ownerDoc.setUser(owner);
        ownerDoc.setFileName("test.pdf");
        ownerDoc.setFileType("PDF");
        ownerDoc.setStatus("UPLOADING");

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(foreignUser));
    }

    // 1. Owner uploads document with notebook -> indexing orchestration is invoked & activeIndexVersion set
    @Test
    void test1_OwnerUploadsDocumentWithNotebook_TriggersIndexingAndCommit() throws Exception {
        Path tempFile = Files.createTempFile("test_upload", ".pdf");
        Files.writeString(tempFile, "Dummy pdf text content");
        ownerDoc.setFilePath(tempFile.toString());
        ownerDoc.setExtractedText("Dummy pdf text content");

        lenient().when(documentRepository.countByUserId(1L)).thenReturn(0L);
        when(notebookRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(Optional.of(ownerNotebook));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            ownerDoc.setStatus(d.getStatus());
            ownerDoc.setNotebook(d.getNotebook());
            ownerDoc.setFilePath(d.getFilePath());
            return ownerDoc;
        });
        when(documentRepository.findById(100L)).thenReturn(Optional.of(ownerDoc));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));

        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("Content chunk").build();
        when(chunkingService.chunk(parsed)).thenReturn(List.of(draft));

        DocumentChunk mockChunk = new DocumentChunk();
        mockChunk.setId(500L);
        mockChunk.setChunkIndex(0);
        mockChunk.setContent("Content chunk");
        when(documentChunkRepository.saveAll(anyList())).thenReturn(List.of(mockChunk));

        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(1536);
        when(embeddingProvider.embedBatch(anyList())).thenReturn(List.of(List.of(0.1, 0.2)));

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "Dummy pdf text content".getBytes());
        Document result = documentService.uploadDocument(1L, 10L, file);

        verify(vectorStore).upsert(anyList());
        assertThat(result.getActiveIndexVersion()).isNotNull().startsWith("v");
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getNotebook()).isEqualTo(ownerNotebook);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadWithoutNotebookActivatesIndexOnlyAfterVectorUpsert() throws Exception {
        Path tempFile = Files.createTempFile("test_upload", ".pdf");
        Files.writeString(tempFile, "Dummy pdf text content");
        ownerDoc.setFilePath(tempFile.toString());
        ownerDoc.setExtractedText("Dummy pdf text content");

        lenient().when(documentRepository.countByUserId(1L)).thenReturn(0L);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            ownerDoc.setStatus(d.getStatus());
            ownerDoc.setNotebook(d.getNotebook());
            ownerDoc.setFilePath(d.getFilePath());
            return ownerDoc;
        });
        when(documentRepository.findById(100L)).thenReturn(Optional.of(ownerDoc));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));

        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("Content chunk").build();
        when(chunkingService.chunk(parsed)).thenReturn(List.of(draft));

        DocumentChunk mockChunk = new DocumentChunk();
        mockChunk.setId(500L);
        mockChunk.setChunkIndex(0);
        mockChunk.setContent("Content chunk");
        when(documentChunkRepository.saveAll(anyList())).thenReturn(List.of(mockChunk));

        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(1536);
        when(embeddingProvider.embedBatch(anyList())).thenReturn(List.of(List.of(0.1, 0.2)));

        doAnswer(invocation -> {
            assertThat(ownerDoc.getStatus()).isEqualTo("PROCESSING");
            assertThat(ownerDoc.getActiveIndexVersion()).isNull();
            return null;
        }).when(vectorStore).upsert(anyList());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "Dummy pdf text content".getBytes());
        Document result = documentService.uploadDocument(1L, null, file);

        verify(vectorStore).upsert(anyList());
        assertThat(result.getActiveIndexVersion()).isNotNull().startsWith("v");
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getNotebook()).isNull();
        verifyNoInteractions(notebookRepository);

        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(Path.of(result.getFilePath()));
    }

    // 2 & 3 & 4. Assigning notebook to document triggers indexing and commits activeIndexVersion after commit
    @Test
    void statusPollingIncludesActiveVersionOnlyAfterActivation() {
        when(documentRepository.findByIdAndUserId(100L,1L)).thenReturn(Optional.of(ownerDoc));
        ownerDoc.setStatus("PROCESSING");
        var processing = documentService.getDocumentStatus(1L,100L);
        assertThat(processing).containsKey("activeIndexVersion");
        assertThat(processing.get("activeIndexVersion")).isNull();
        ownerDoc.setStatus("READY"); ownerDoc.setActiveIndexVersion("v2");
        assertThat(documentService.getDocumentStatus(1L,100L)).containsEntry("activeIndexVersion","v2");
    }

    @Test
    void test2_AssignNotebook_TriggersIndexingAndProducesChunks() throws Exception {
        Path tempFile = Files.createTempFile("test_assign", ".pdf");
        Files.writeString(tempFile, "sample text content");
        ownerDoc.setFilePath(tempFile.toString());
        ownerDoc.setExtractedText("sample text content");

        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(ownerDoc));
        when(notebookRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(Optional.of(ownerNotebook));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(ownerDoc));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));

        ParsedDocument parsed = new ParsedDocument();
        when(documentParsingService.parse(any(), any())).thenReturn(parsed);
        ChunkDraft draft = ChunkDraft.builder().chunkIndex(0).content("sample text content").build();
        when(chunkingService.chunk(parsed)).thenReturn(List.of(draft));

        DocumentChunk mockChunk = new DocumentChunk();
        mockChunk.setId(501L);
        mockChunk.setChunkIndex(0);
        mockChunk.setContent("sample text content");
        when(documentChunkRepository.saveAll(anyList())).thenReturn(List.of(mockChunk));

        when(embeddingProvider.getModelIdentifier()).thenReturn("test-model");
        when(embeddingProvider.getDimension()).thenReturn(1536);
        when(embeddingProvider.embedBatch(anyList())).thenReturn(List.of(List.of(0.1, 0.2)));

        Document updated = documentService.assignNotebook(1L, 100L, 10L);

        assertThat(updated.getNotebook()).isEqualTo(ownerNotebook);
        assertThat(updated.getActiveIndexVersion()).isNotNull().startsWith("v");
        assertThat(updated.getStatus()).isEqualTo("READY");
        verify(documentChunkRepository).saveAll(anyList());
        verify(vectorStore).upsert(anyList());

        Files.deleteIfExists(tempFile);
    }

    // 5. Foreign notebook assignment is rejected
    @Test
    void test5_ForeignNotebookAssignment_Rejected() {
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(ownerDoc));
        when(notebookRepository.findByIdAndUserIdAndIsDeletedFalse(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.assignNotebook(1L, 100L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Notebook không tồn tại hoặc bạn không có quyền truy cập");
    }

    // 6. Foreign document assignment is rejected
    @Test
    void test6_ForeignDocumentAssignment_Rejected() {
        when(documentRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.assignNotebook(2L, 100L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tài liệu không tồn tại hoặc bạn không có quyền truy cập");
    }

    // 7. Failure during indexing does not leave document as READY
    @Test
    void test7_IndexingFailure_LeavesDocumentInFailedState() throws Exception {
        Path tempFile = Files.createTempFile("test_fail", ".pdf");
        Files.writeString(tempFile, "failing content");
        ownerDoc.setFilePath(tempFile.toString());
        ownerDoc.setExtractedText("failing content");

        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(ownerDoc));
        when(notebookRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(Optional.of(ownerNotebook));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(ownerDoc));

        // Simulate exception during parsing
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));
        when(documentParsingService.parse(any(), any())).thenThrow(new RuntimeException("Parsing failed test"));

        Document updated = documentService.assignNotebook(1L, 100L, 10L);

        assertThat(updated.getStatus()).isEqualTo("FAILED");
        assertThat(updated.getActiveIndexVersion()).isNull();
        assertThat(updated.getErrorMessage()).contains("Parsing failed test");

        Files.deleteIfExists(tempFile);
    }

    // 8. Existing delete/cleanup behavior remains intact
    @Test
    void test8_DeleteDocument_CleansUpVectorStoreAndDatabase() {
        ownerDoc.setActiveIndexVersion("v12345");
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(ownerDoc));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));

        documentService.deleteDocument(1L, 100L);

        verify(vectorStore).deleteByDocumentAndVersion(100L, "v12345");
        verify(documentRepository).saveAndFlush(ownerDoc);
        assertThat(ownerDoc.getStatus()).isEqualTo("DELETED");
    }

    @Test
    void documentScopedKgDoesNotBlockSoftDelete() {
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(ownerDoc));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));
        documentService.deleteDocument(1L, 100L);
        assertThat(ownerDoc.getStatus()).isEqualTo("DELETED");
        verify(documentRepository).saveAndFlush(ownerDoc);
        verifyNoInteractions(conceptRepository, topicRepository);
        verifyNoInteractions(vectorStore);
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test void deletedDocumentCannotBeReactivatedByIndexWorker() {
        ownerDoc.setStatus("DELETED");
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(ownerDoc));
        documentIndexingService.updateStatusInNewTransaction(100L, "PROCESSING");
        documentIndexingService.updateStatusAndError(100L, "FAILED", "late failure");
        assertThatThrownBy(() -> documentIndexingService.activateIndex(100L, "v2", "hash", "model", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ownerDoc.getStatus()).isEqualTo("DELETED");
        verify(documentRepository, never()).save(any());
    }
}
