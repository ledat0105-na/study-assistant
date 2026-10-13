package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.config.AiChatProperties;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import com.example.studyassistant.retrieval.model.VectorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Tag("Integration")
@ActiveProfiles("integration")
public class GlobalBudgetIntegrationTest {

    @Autowired
    private GroundedAnswerService groundedAnswerService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private AiChatProperties aiChatProperties;

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    @MockitoBean
    private LlmProvider llmProvider;

    private User user;
    private Notebook notebook;
    private Document doc;

    @BeforeEach
    void setup() {
        documentChunkRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        notebookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        user = new User();
        user.setEmail("budget@example.com");
        user.setPassword("pass");
        user.setRole("STUDENT");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Budget Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        doc = new Document();
        doc.setNotebook(notebook);
        doc.setUser(user);
        doc.setFileName("budget.pdf");
        doc.setFilePath("/tmp");
        doc.setStatus("READY");
        doc.setActiveIndexVersion("v1");
        doc = documentRepository.saveAndFlush(doc);

        // Adjust budget dynamically for test via Reflection or just rely on chunk sizes
        // Context window is usually 8000. Let's make it small for subset tests.
        ReflectionTestUtils.setField(aiChatProperties, "contextWindowTokens", 500);
        ReflectionTestUtils.setField(aiChatProperties, "maxOutputTokens", 100);
        ReflectionTestUtils.setField(aiChatProperties, "inputSafetyMarginTokens", 50);
        
        when(embeddingProvider.embed(anyString())).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    @Transactional
    void testNonPrefixSubset_RealE2E() {
        // Create 3 chunks
        DocumentChunk chunkA = new DocumentChunk();
        chunkA.setDocument(doc);
        chunkA.setChunkIndex(0);
        chunkA.setContent("A ".repeat(200)); // ~266 tokens
        chunkA.setIndexVersion("v1");
        chunkA.setTokenCount(266);
        chunkA = documentChunkRepository.saveAndFlush(chunkA);

        DocumentChunk chunkB = new DocumentChunk();
        chunkB.setDocument(doc);
        chunkB.setChunkIndex(1);
        chunkB.setContent("B ".repeat(20)); // ~26 tokens
        chunkB.setIndexVersion("v1");
        chunkB.setTokenCount(26);
        chunkB = documentChunkRepository.saveAndFlush(chunkB);

        DocumentChunk chunkC = new DocumentChunk();
        chunkC.setDocument(doc);
        chunkC.setChunkIndex(2);
        chunkC.setContent("C ".repeat(20)); // ~26 tokens
        chunkC.setIndexVersion("v1");
        chunkC.setTokenCount(26);
        chunkC = documentChunkRepository.saveAndFlush(chunkC);

        // Mock Qdrant retrieval to return A, B, C with specific scores
        // A and B insufficient (< 0.3 threshold), C sufficient (>= 0.3)
        VectorRecord recA = VectorRecord.builder().vectorId(chunkA.getId().toString()).chunkId(chunkA.getId())
                .documentId(doc.getId()).indexVersion(doc.getActiveIndexVersion()).score(0.2).build();
        VectorRecord recB = VectorRecord.builder().vectorId(chunkB.getId().toString()).chunkId(chunkB.getId())
                .documentId(doc.getId()).indexVersion(doc.getActiveIndexVersion()).score(0.2).build();
        VectorRecord recC = VectorRecord.builder().vectorId(chunkC.getId().toString()).chunkId(chunkC.getId())
                .documentId(doc.getId()).indexVersion(doc.getActiveIndexVersion()).score(0.9).build();

        when(vectorStore.search(anyList(), anyList(), anyInt()))
                .thenReturn(Arrays.asList(recA, recB, recC));
                
        // Real Evidence Policy Assertions
        com.example.studyassistant.retrieval.model.RetrievedChunk rcA = new com.example.studyassistant.retrieval.model.RetrievedChunk();
        rcA.setChunkId(chunkA.getId()); rcA.setDenseScore(0.2);
        
        com.example.studyassistant.retrieval.model.RetrievedChunk rcB = new com.example.studyassistant.retrieval.model.RetrievedChunk();
        rcB.setChunkId(chunkB.getId()); rcB.setDenseScore(0.2);
        
        com.example.studyassistant.retrieval.model.RetrievedChunk rcC = new com.example.studyassistant.retrieval.model.RetrievedChunk();
        rcC.setChunkId(chunkC.getId()); rcC.setDenseScore(0.9);
        
        assertThat(retrievalService.evaluateEvidencePolicy(Arrays.asList(rcA))).isFalse();
        assertThat(retrievalService.evaluateEvidencePolicy(Arrays.asList(rcB))).isFalse();
        assertThat(retrievalService.evaluateEvidencePolicy(Arrays.asList(rcC))).isTrue();
        assertThat(retrievalService.evaluateEvidencePolicy(Arrays.asList(rcA, rcB))).isFalse();
        assertThat(retrievalService.evaluateEvidencePolicy(Arrays.asList(rcB, rcC))).isTrue();
                
        // Mock LLM
        when(llmProvider.generate(anyString(), anyString(), anyDouble()))
                .thenReturn("Answer derived from [S1] and [S2] and fake [S3] and fake [G1].");

        GroundedAnswer answer = groundedAnswerService.answer(user.getId(), notebook.getId(), "Test query");

        assertThat(answer.isHasSufficientEvidence()).isTrue();
        // A is dropped due to size. B and C fit.
        // B (index 1) -> S1, C (index 2) -> S2
        assertThat(answer.getCitations()).hasSize(2);
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo(chunkB.getId());
        assertThat(answer.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(answer.getCitations().get(1).getChunkId()).isEqualTo(chunkC.getId());
        assertThat(answer.getCitations().get(1).getCitationId()).isEqualTo("S2");

        // Verify LLM called exactly once
        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());
    }

    @Test
    @Transactional
    void testNoValidSubset_RealE2E() {
        DocumentChunk chunkA = new DocumentChunk();
        chunkA.setDocument(doc);
        chunkA.setChunkIndex(0);
        chunkA.setContent("A ".repeat(400)); // ~533 tokens (too big for budget 350)
        chunkA.setIndexVersion("v1");
        chunkA.setTokenCount(533);
        chunkA = documentChunkRepository.saveAndFlush(chunkA);

        VectorRecord recA = VectorRecord.builder().vectorId(chunkA.getId().toString()).chunkId(chunkA.getId())
                .documentId(doc.getId()).indexVersion(doc.getActiveIndexVersion()).score(0.9).build();
        when(vectorStore.search(anyList(), anyList(), anyInt()))
                .thenReturn(Arrays.asList(recA));

        GroundedAnswer answer = groundedAnswerService.answer(user.getId(), notebook.getId(), "Test query");

        assertThat(answer.isHasSufficientEvidence()).isFalse();
        verify(llmProvider, never()).generate(anyString(), anyString(), anyDouble());
    }

    @Test
    @Transactional
    void testOriginalNoEvidence_RealE2E() {
        DocumentChunk chunkA = new DocumentChunk();
        chunkA.setDocument(doc);
        chunkA.setChunkIndex(0);
        chunkA.setContent("A ".repeat(10));
        chunkA.setIndexVersion("v1");
        chunkA.setTokenCount(13);
        chunkA = documentChunkRepository.saveAndFlush(chunkA);

        VectorRecord recA = VectorRecord.builder().vectorId(chunkA.getId().toString()).chunkId(chunkA.getId())
                .documentId(doc.getId()).indexVersion(doc.getActiveIndexVersion()).score(0.1).build();
        when(vectorStore.search(anyList(), anyList(), anyInt()))
                .thenReturn(Arrays.asList(recA));

        GroundedAnswer answer = groundedAnswerService.answer(user.getId(), notebook.getId(), "Test query");

        assertThat(answer.isHasSufficientEvidence()).isFalse();
        verify(llmProvider, never()).generate(anyString(), anyString(), anyDouble());
    }
}
