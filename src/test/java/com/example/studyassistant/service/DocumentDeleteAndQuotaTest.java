package com.example.studyassistant.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.service.DocumentIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentDeleteAndQuotaTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private com.example.studyassistant.repository.ConceptRepository conceptRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private DocumentIndexingService documentIndexingService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private DocumentService documentService;

    private User testUser;
    private User foreignUser;
    private Notebook testNotebook;
    private Document indexedDocument;
    private Document unindexedDocument;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("teacher1");
        testUser.setPlan("FREE");

        foreignUser = new User();
        foreignUser.setId(99L);
        foreignUser.setUsername("other_user");

        testNotebook = new Notebook();
        testNotebook.setId(10L);
        testNotebook.setUser(testUser);
        testNotebook.setTitle("Test Notebook");

        indexedDocument = new Document();
        indexedDocument.setId(100L);
        indexedDocument.setUser(testUser);
        indexedDocument.setNotebook(testNotebook);
        indexedDocument.setFileName("Indexed Doc");
        indexedDocument.setFileType("PDF");
        indexedDocument.setStatus("READY");
        indexedDocument.setActiveIndexVersion("v1_hash123");
        indexedDocument.setFilePath("storage_private/uploads/test_indexed.pdf");

        unindexedDocument = new Document();
        unindexedDocument.setId(101L);
        unindexedDocument.setUser(testUser);
        unindexedDocument.setFileName("Unindexed Doc");
        unindexedDocument.setFileType("PDF");
        unindexedDocument.setStatus("READY");
        unindexedDocument.setFilePath("storage_private/uploads/test_unindexed.pdf");
    }

    @Test
    @DisplayName("Delete unindexed document - removes DB row, no Qdrant call")
    void testDeleteUnindexedDocument() {
        when(documentRepository.findByIdAndUserId(101L, 1L)).thenReturn(Optional.of(unindexedDocument));
        when(documentRepository.findByIdWithPessimisticLock(101L)).thenReturn(Optional.of(unindexedDocument));

        documentService.deleteDocument(1L, 101L);

        verify(documentIndexingService, never()).cleanupVersion(anyLong(), anyString());
        verify(documentRepository).saveAndFlush(unindexedDocument);
        assertEquals("DELETED", unindexedDocument.getStatus());

    }

    @Test
    @DisplayName("Delete indexed document with active index version - triggers Qdrant cleanup and DB delete")
    void testDeleteIndexedDocument() {
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(indexedDocument));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(indexedDocument));

        documentService.deleteDocument(1L, 100L);

        verify(documentIndexingService).cleanupVersion(100L, "v1_hash123");
        verify(documentRepository).saveAndFlush(indexedDocument);
        assertEquals("DELETED", indexedDocument.getStatus());
        assertNull(indexedDocument.getActiveIndexVersion());

    }

    @Test
    @DisplayName("Delete document linked to notebook - document deleted, notebook remains intact")
    void testDeleteDocumentLinkedToNotebook() {
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(indexedDocument));
        when(documentRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(indexedDocument));

        documentService.deleteDocument(1L, 100L);

        verify(documentRepository).saveAndFlush(indexedDocument);
        assertEquals("DELETED", indexedDocument.getStatus());
        assertNull(indexedDocument.getActiveIndexVersion());
        // Notebook is a separate entity and should NOT be deleted
        verify(notebookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Foreign user cannot delete document - throws IllegalArgumentException")
    void testDeleteForeignDocumentRejected() {
        when(documentRepository.findByIdAndUserId(100L, 99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            documentService.deleteDocument(99L, 100L);
        });

        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Upload blocked when quota reached, allowed again after quota reclamation")
    void testQuotaBlockAndReclamationFlow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "sample text content".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        doThrow(new IllegalStateException("Bạn đã đạt giới hạn 3 tài liệu upload của gói FREE."))
                .when(quotaService).checkStorageAndUploadQuota(eq(1L), eq("FREE"), anyLong());

        // 1. Upload attempt when full -> throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            documentService.uploadDocument(1L, null, file);
        });
        assertTrue(ex.getMessage().contains("giới hạn"));

        // 2. Simulate deletion reclaiming quota: now checkStorageAndUploadQuota succeeds
        doNothing().when(quotaService).checkStorageAndUploadQuota(eq(1L), eq("FREE"), anyLong());
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(200L);
            return doc;
        });

        // 3. Upload attempt after delete -> succeeds!
        Document uploaded = documentService.uploadDocument(1L, null, file);
        assertNotNull(uploaded);
        assertEquals(200L, uploaded.getId());
    }
}
