package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ConceptSource;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.service.dto.ConceptExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AIConceptExtractorTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private ConceptService conceptService;

    @Mock
    private ConceptNameNormalizer normalizer;

    @InjectMocks
    private AIConceptExtractor extractor;

    private Notebook notebook;
    private DocumentChunk chunk;

    @BeforeEach
    void setUp() {
        notebook = new Notebook();
        notebook.setId(1L);

        chunk = new DocumentChunk();
        chunk.setId(10L);
        com.example.studyassistant.entity.User owner = new com.example.studyassistant.entity.User(); owner.setId(1L);
        notebook.setUser(owner);
        com.example.studyassistant.entity.Document doc = new com.example.studyassistant.entity.Document();
        doc.setId(1L); doc.setUser(owner); doc.setNotebook(notebook); doc.setStatus("READY"); doc.setActiveIndexVersion("v1");
        chunk.setDocument(doc); chunk.setIndexVersion("v1");

        chunk.setContent("Machine learning is a field of study. Supervised learning is a common method. C and C++ are languages. Học Máy is vietnamese.");
    }

    @Test
    void descriptionsUseVietnameseWhileEvidenceAndIdentifiersRemainExact() {
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("{\"concepts\":[]}");
        extractor.extractConcepts(notebook, chunk);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).generate(prompt.capture(), anyString(), anyDouble());
        assertThat(prompt.getValue()).contains("Write every description in clear Vietnamese")
                .contains("do not translate code identifiers")
                .contains("evidenceText must be an exact substring");
    }

    @Test
    void extractConcepts_MultipleConcepts() { // A, B, D, E
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Machine learning",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 0.9
                    },
                    {
                      "name": "Supervised learning",
                      "evidenceText": "Supervised learning is a common method.",
                      "confidence": 0.8
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Machine learning")).thenReturn("machine learning");
        when(normalizer.normalize("Supervised learning")).thenReturn("supervised learning");
        
        ConceptSource src1 = new ConceptSource(); src1.setId(1L);
        ConceptSource src2 = new ConceptSource(); src2.setId(2L);
        
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Machine learning"), any(), any(), eq(chunk), any(), any())).thenReturn(src1);
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Supervised learning"), any(), any(), eq(chunk), any(), any())).thenReturn(src2);

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAcceptedConcepts()).hasSize(2);
        assertThat(result.getRejectedCount()).isEqualTo(0);
    }

    @Test
    void extractConcepts_NormalizedDuplicateCandidates() { // C, L
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Machine learning",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 0.9
                    },
                    {
                      "name": "MACHINE LEARNING",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 0.8
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Machine learning")).thenReturn("machine learning");
        when(normalizer.normalize("MACHINE LEARNING")).thenReturn("machine learning");
        
        ConceptSource src1 = new ConceptSource(); src1.setId(1L);
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Machine learning"), any(), any(), eq(chunk), any(), any())).thenReturn(src1);

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAcceptedConcepts()).hasSize(1);
        // The second one is deduplicated in memory, so it counts as rejected/filtered (or ignored).
        assertThat(result.getRejectedCount()).isEqualTo(1);
        
        verify(conceptService, times(1)).getOrCreateWithSource(any(KnowledgeScope.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void extractConcepts_RejectsHallucinatedEvidence() { // F
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Deep learning",
                      "evidenceText": "Deep learning is great",
                      "confidence": 0.9
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Deep learning")).thenReturn("deep learning");

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).isEmpty();
        assertThat(result.getRejectedCount()).isEqualTo(1);
    }

    @Test
    void extractConcepts_BlankNameRejection() { // G
        String json = """
                {
                  "concepts": [
                    {
                      "name": "   ",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 0.9
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).isEmpty();
        assertThat(result.getRejectedCount()).isEqualTo(1);
    }

    @Test
    void extractConcepts_InvalidConfidenceRejection() { // H
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Machine learning",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 1.5
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Machine learning")).thenReturn("machine learning");
        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).isEmpty();
        assertThat(result.getRejectedCount()).isEqualTo(1);
    }

    @Test
    void extractConcepts_ConfidenceExact0And1() { // I
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Machine learning",
                      "evidenceText": "Machine learning is a field of study",
                      "confidence": 0.0
                    },
                    {
                      "name": "Supervised learning",
                      "evidenceText": "Supervised learning is a common method.",
                      "confidence": 1.0
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Machine learning")).thenReturn("machine learning");
        when(normalizer.normalize("Supervised learning")).thenReturn("supervised learning");
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Machine learning"), any(), any(), any(), any(), any())).thenReturn(new ConceptSource());
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Supervised learning"), any(), any(), any(), any(), any())).thenReturn(new ConceptSource());

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).hasSize(2);
    }

    @Test
    void extractConcepts_PunctuationDistinct() { // J
        String json = """
                {
                  "concepts": [
                    {
                      "name": "C",
                      "evidenceText": "C and C++ are languages",
                      "confidence": 0.9
                    },
                    {
                      "name": "C++",
                      "evidenceText": "C and C++ are languages",
                      "confidence": 0.9
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("C")).thenReturn("c");
        when(normalizer.normalize("C++")).thenReturn("c++");
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("C"), any(), any(), any(), any(), any())).thenReturn(new ConceptSource());
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("C++"), any(), any(), any(), any(), any())).thenReturn(new ConceptSource());

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).hasSize(2);
    }
    
    @Test
    void extractConcepts_VietnamesePreserved() { // K
        String json = """
                {
                  "concepts": [
                    {
                      "name": "Học Máy",
                      "evidenceText": "Học Máy is vietnamese",
                      "confidence": 0.9
                    }
                  ]
                }
                """;

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        when(normalizer.normalize("Học Máy")).thenReturn("học máy");
        when(conceptService.getOrCreateWithSource(any(KnowledgeScope.class), eq("Học Máy"), any(), any(), any(), any(), any())).thenReturn(new ConceptSource());

        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getAcceptedConcepts()).hasSize(1);
    }

    @Test
    void extractConcepts_MalformedJson() { // N
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("Not json");
        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getStatus()).isEqualTo("PARSE_FAILURE");
    }

    @Test
    void extractConcepts_ProviderFailure() { // O
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("API down"));
        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getStatus()).isEqualTo("PROVIDER_FAILURE");
    }

    @Test
    void extractConcepts_PromptInjectionBoundary() { // P
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("{}");
        extractor.extractConcepts(notebook, chunk);
        
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).generate(anyString(), promptCaptor.capture(), anyDouble());
        
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("=== SOURCE DATA ===");
        assertThat(prompt).contains(chunk.getContent());
    }
    
    @Test
    void extractConcepts_MaxLimitExceeded() { // Max concept limit
        StringBuilder conceptsJson = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            conceptsJson.append(String.format("{\"name\":\"C%d\",\"evidenceText\":\"evidence\"}", i));
            if (i < 50) conceptsJson.append(",");
        }
        String json = "{\"concepts\":[" + conceptsJson.toString() + "]}";
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        
        ConceptExtractionResult result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getStatus()).isEqualTo("PARSE_FAILURE");
        assertThat(result.getRejectedCount()).isEqualTo(51);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"concepts\":null}", "{\"concepts\":{}}", "{\"concepts\":[],\"unsafe\":[]}", "[]", "null", "{\"concepts\":[]} trailing", "{\"concepts\":[],\"concepts\":[]}", "{\"concepts\":[null]}", "{\"concepts\":[{\"evidenceText\":42}]}", "{\"concepts\":[{\"unknown\":true}]}"})
    void invalidEnvelopeFailsClosed(String json) {
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(json);
        var result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getStatus()).isEqualTo("PARSE_FAILURE");
        assertThat(result.getAcceptedConcepts()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(conceptService);
    }
    @Test void explicitlyEmptyArrayIsValid() {
        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn("{\"concepts\":[]}");
        var result = extractor.extractConcepts(notebook, chunk);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAcceptedConcepts()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(conceptService);
    }
}
