package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.DocumentVersionPair;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.model.VectorRecord;
import com.example.studyassistant.retrieval.repository.DocumentChunkSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetrievalServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private DocumentChunkSearchRepository documentChunkSearchRepository;
    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retrievalService, "denseTopK", 30);
        ReflectionTestUtils.setField(retrievalService, "keywordTopK", 30);
        ReflectionTestUtils.setField(retrievalService, "candidateTopK", 15);
        ReflectionTestUtils.setField(retrievalService, "contextTopK", 6);
        ReflectionTestUtils.setField(retrievalService, "contextMaxTokens", 6000);
        ReflectionTestUtils.setField(retrievalService, "rrfK", 60);
    }

    private Document createReadyDoc(Long id, String version) {
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus("READY");
        doc.setActiveIndexVersion(version);
        doc.setFileName("Doc" + id);
        return doc;
    }

    private DocumentChunk createMockChunk(Long id, Long docId, Integer index, int tokens, String version) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setChunkIndex(index);
        chunk.setTokenCount(tokens);
        chunk.setIndexVersion(version);
        chunk.setContent("Content of " + id);
        
        Document doc = new Document();
        doc.setId(docId);
        doc.setFileName("Doc" + docId);
        chunk.setDocument(doc);
        return chunk;
    }

    @Test
    void testCP5_01_04_05_06_07_08_09_OwnershipAndStatusFilters() {
        Document d1 = createReadyDoc(1L, "v1"); // Valid
        
        Document d2 = new Document(); // FAILED
        d2.setId(2L);
        d2.setStatus("FAILED");
        d2.setActiveIndexVersion("v1");
        
        Document d3 = new Document(); // UPLOADING
        d3.setId(3L);
        d3.setStatus("UPLOADING");
        
        Document d4 = new Document(); // READY but null version
        d4.setId(4L);
        d4.setStatus("READY");
        
        Document d5 = createReadyDoc(5L, "v2"); // Valid, different version

        when(documentRepository.findByUserIdAndNotebookId(10L, 20L))
                .thenReturn(Arrays.asList(d1, d2, d3, d4, d5));
                
        when(embeddingProvider.embed(anyString())).thenReturn(Arrays.asList(0.1));
        
        retrievalService.search(10L, 20L, "query");

        // Verify only d1 and d5 were sent to VectorStore and Keyword search
        ArgumentCaptor<List<DocumentVersionPair>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).search(anyList(), captor.capture(), anyInt());
        
        List<DocumentVersionPair> pairs = captor.getValue();
        assertThat(pairs).hasSize(2);
        assertThat(pairs.stream().map(DocumentVersionPair::getDocumentId)).containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    void testCP5_10_11_12_EmptyAuthorizedSet() {
        when(documentRepository.findByUserIdAndNotebookId(10L, 20L)).thenReturn(Collections.emptyList());

        RetrievalResult result = retrievalService.search(10L, 20L, "query");
        
        assertThat(result.getChunks()).isEmpty();
        verifyNoInteractions(embeddingProvider);
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(documentChunkSearchRepository);
    }

    @Test
    void testCP5_13_BlankQueryRejected() {
        assertThatThrownBy(() -> retrievalService.search(10L, 20L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCP5_20_21_DefenseInDepthAndStaleChunkSkipped() {
        Document d1 = createReadyDoc(1L, "v1");
        when(documentRepository.findByUserIdAndNotebookId(10L, 20L)).thenReturn(Collections.singletonList(d1));
        when(embeddingProvider.embed(anyString())).thenReturn(Arrays.asList(0.1));
        
        // Qdrant returns an unauthorized vector (doc 2) and a stale vector (chunk missing in DB)
        VectorRecord r1 = VectorRecord.builder().chunkId(100L).documentId(2L).indexVersion("v1").build(); // Unauthorized
        VectorRecord r2 = VectorRecord.builder().chunkId(101L).documentId(1L).indexVersion("v1").build(); // Stale in DB
        
        when(vectorStore.search(anyList(), anyList(), anyInt())).thenReturn(Arrays.asList(r1, r2));
        
        when(documentChunkRepository.findById(101L)).thenReturn(Optional.empty()); // DB missing it
        
        RetrievalResult result = retrievalService.search(10L, 20L, "query");
        
        assertThat(result.getChunks()).isEmpty(); // Neither chunk was hydrated
    }

    @Test
    void testCP5_22_23_24_25_26_RrfLogicAndTieBreaking() {
        Document d1 = createReadyDoc(1L, "v1");
        when(documentRepository.findByUserIdAndNotebookId(1L, 1L)).thenReturn(Collections.singletonList(d1));
        when(embeddingProvider.embed(anyString())).thenReturn(Arrays.asList(0.1));
        
        // chunk1: dense rank 1, keyword rank 2
        // chunk2: dense rank 2, no keyword
        // chunk3: no dense, keyword rank 1
        
        VectorRecord r1 = VectorRecord.builder().chunkId(10L).documentId(1L).indexVersion("v1").build();
        VectorRecord r2 = VectorRecord.builder().chunkId(20L).documentId(1L).indexVersion("v1").build();
        when(vectorStore.search(anyList(), anyList(), anyInt())).thenReturn(Arrays.asList(r1, r2));
        
        DocumentChunkSearchRepository.KeywordSearchResult k3 = new DocumentChunkSearchRepository.KeywordSearchResult();
        k3.chunkId = 30L; k3.documentId = 1L; k3.indexVersion = "v1"; k3.score = 5.0;
        
        DocumentChunkSearchRepository.KeywordSearchResult k1 = new DocumentChunkSearchRepository.KeywordSearchResult();
        k1.chunkId = 10L; k1.documentId = 1L; k1.indexVersion = "v1"; k1.score = 2.0;
        
        when(documentChunkSearchRepository.searchByKeyword(anyString(), anyList(), anyInt())).thenReturn(Arrays.asList(k3, k1));
        
        when(documentChunkRepository.findById(10L)).thenReturn(Optional.of(createMockChunk(10L, 1L, 1, 100, "v1")));
        when(documentChunkRepository.findById(20L)).thenReturn(Optional.of(createMockChunk(20L, 1L, 4, 100, "v1")));
        when(documentChunkRepository.findById(30L)).thenReturn(Optional.of(createMockChunk(30L, 1L, 7, 100, "v1")));

        RetrievalResult result = retrievalService.search(1L, 1L, "query");
        
        // Expected RRF:
        // chunk1: 1/(60+1) + 1/(60+2) = 1/61 + 1/62 = 0.01639 + 0.01612 = 0.0325
        // chunk3: 1/(60+1) = 0.01639
        // chunk2: 1/(60+2) = 0.01612
        
        List<RetrievedChunk> chunks = result.getChunks();
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getChunkId()).isEqualTo(10L);
        assertThat(chunks.get(1).getChunkId()).isEqualTo(30L);
        assertThat(chunks.get(2).getChunkId()).isEqualTo(20L);
    }

    @Test
    void testCP5_29_30_31_ContextSelectionAndOverlapSuppression() {
        Document d1 = createReadyDoc(1L, "v1");
        when(documentRepository.findByUserIdAndNotebookId(1L, 1L)).thenReturn(Collections.singletonList(d1));
        when(embeddingProvider.embed(anyString())).thenReturn(Arrays.asList(0.1));
        
        // We will return chunks 1, 2, 3 in order of rank. 
        // Chunk 2 is adjacent to chunk 1 (index 1 vs 2), so it should be suppressed.
        // Chunk 3 is index 4, so it's not adjacent, it should be kept.
        
        VectorRecord r1 = VectorRecord.builder().chunkId(10L).documentId(1L).indexVersion("v1").build();
        VectorRecord r2 = VectorRecord.builder().chunkId(20L).documentId(1L).indexVersion("v1").build();
        VectorRecord r3 = VectorRecord.builder().chunkId(30L).documentId(1L).indexVersion("v1").build();
        
        when(vectorStore.search(anyList(), anyList(), anyInt())).thenReturn(Arrays.asList(r1, r2, r3));
        
        String baseContent = "This is a very long base content string that is used to test the overlap suppression logic. It must be longer than 50 characters to trigger the heuristic. ";
        when(documentChunkRepository.findById(10L)).thenReturn(Optional.of(createMockChunkWithContent(10L, 1L, 1, 100, "v1", baseContent + " Part A")));
        when(documentChunkRepository.findById(20L)).thenReturn(Optional.of(createMockChunkWithContent(20L, 1L, 2, 100, "v1", baseContent + " Part B"))); // Adjacent and overlapping!
        when(documentChunkRepository.findById(30L)).thenReturn(Optional.of(createMockChunkWithContent(30L, 1L, 4, 100, "v1", "Completely different content that is also long enough but won't overlap with the base string at all."))); // Gap!
        
        RetrievalResult result = retrievalService.search(1L, 1L, "query");
        
        List<RetrievedChunk> chunks = result.getChunks();
        assertThat(chunks).hasSize(2); // Chunk 2 is skipped
        assertThat(chunks.get(0).getChunkId()).isEqualTo(10L);
        assertThat(chunks.get(1).getChunkId()).isEqualTo(30L);
    }

    private DocumentChunk createMockChunkWithContent(Long id, Long docId, Integer index, int tokens, String version, String content) {
        DocumentChunk chunk = createMockChunk(id, docId, index, tokens, version);
        chunk.setContent(content);
        return chunk;
    }

    @Test
    void testCP5_36_37_38_BranchOutageResilience() {
        Document d1 = createReadyDoc(1L, "v1");
        when(documentRepository.findByUserIdAndNotebookId(1L, 1L)).thenReturn(Collections.singletonList(d1));
        when(embeddingProvider.embed(anyString())).thenReturn(Arrays.asList(0.1));
        
        // Qdrant fails!
        when(vectorStore.search(anyList(), anyList(), anyInt())).thenThrow(new RuntimeException("Qdrant Down"));
        
        // Keyword succeeds!
        DocumentChunkSearchRepository.KeywordSearchResult k1 = new DocumentChunkSearchRepository.KeywordSearchResult();
        k1.chunkId = 10L; k1.documentId = 1L; k1.indexVersion = "v1"; k1.score = 2.0;
        when(documentChunkSearchRepository.searchByKeyword(anyString(), anyList(), anyInt())).thenReturn(Collections.singletonList(k1));
        
        when(documentChunkRepository.findById(10L)).thenReturn(Optional.of(createMockChunk(10L, 1L, 1, 100, "v1")));

        // Retrieval should gracefully return keyword result
        RetrievalResult result = retrievalService.search(1L, 1L, "query");
        assertThat(result.getChunks()).hasSize(1);
        assertThat(result.getChunks().get(0).getChunkId()).isEqualTo(10L);
    }
}
