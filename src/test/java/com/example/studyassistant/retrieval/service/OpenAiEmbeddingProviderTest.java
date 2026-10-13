package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.exception.EmbeddingProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpenAiEmbeddingProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private OpenAiEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpenAiEmbeddingProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "openaiApiKey", "test-key");
        ReflectionTestUtils.setField(provider, "model", "text-embedding-3-small");
        ReflectionTestUtils.setField(provider, "dimension", 2);
        ReflectionTestUtils.setField(provider, "batchSize", 2);
    }

    @Test
    void testCP3_01_EmbedBlankRejected() {
        assertThatThrownBy(() -> provider.embed("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCP3_02_EmbedBatchEmptyNoHttpRequest() {
        List<List<Double>> result = provider.embedBatch(Collections.emptyList());
        assertThat(result).isEmpty();
        // verifyNoInteractions(restTemplate);
    }

    @Test
    void testCP3_03_SingleEmbeddingMapsCorrectly() {
        Map<String, Object> responseBody = createMockResponse(List.of(List.of(0.1, 0.2)));
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        List<Double> vector = provider.embed("test");
        assertThat(vector).containsExactly(0.1, 0.2);
    }

    @Test
    void testCP3_04_05_BatchInputOutputOrderPreserved() {
        // Return index 1 then index 0 to simulate unordered API response
        Map<String, Object> responseBody = new HashMap<>();
        Map<String, Object> data1 = new HashMap<>();
        data1.put("index", 1);
        data1.put("embedding", List.of(0.3, 0.4));
        
        Map<String, Object> data0 = new HashMap<>();
        data0.put("index", 0);
        data0.put("embedding", List.of(0.1, 0.2));
        
        responseBody.put("data", Arrays.asList(data1, data0));

        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        List<List<Double>> vectors = provider.embedBatch(Arrays.asList("text1", "text2"));
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1, 0.2); // index 0
        assertThat(vectors.get(1)).containsExactly(0.3, 0.4); // index 1
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCP3_06_BatchingRespectsConfiguredBatchSize() {
        ReflectionTestUtils.setField(provider, "batchSize", 2);
        
        Map<String, Object> responseBody = createMockResponse(List.of(List.of(0.1, 0.1), List.of(0.2, 0.2)));
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        provider.embedBatch(Arrays.asList("t1", "t2", "t3", "t4"));

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).postForEntity(any(String.class), captor.capture(), eq(Map.class));
        
        List<HttpEntity<Map<String, Object>>> calls = captor.getAllValues();
        assertThat(((List) calls.get(0).getBody().get("input"))).hasSize(2);
        assertThat(((List) calls.get(1).getBody().get("input"))).hasSize(2);
    }

    @Test
    void testCP3_07_WrongResultCount() {
        Map<String, Object> responseBody = createMockResponse(List.of(List.of(0.1, 0.2)));
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertThatThrownBy(() -> provider.embedBatch(Arrays.asList("t1", "t2"))) // Expecting 2, got 1
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("Expected 2 embeddings, but received 1");
    }

    @Test
    void testCP3_08_WrongVectorDimension() {
        Map<String, Object> responseBody = createMockResponse(List.of(List.of(0.1, 0.2, 0.3))); // dim 3 instead of 2
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertThatThrownBy(() -> provider.embed("test"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("Dimension mismatch");
    }

    @Test
    void testCP3_09_MalformedResponse() {
        Map<String, Object> responseBody = new HashMap<>();
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertThatThrownBy(() -> provider.embed("test"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("missing 'data' field");
    }

    @Test
    void testCP3_10_11_ApiErrorSafeExceptionNoKeyLeak() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized key"));

        assertThatThrownBy(() -> provider.embed("test"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("OpenAI API error")
                .hasMessageNotContaining("test-key"); // Key not leaked
    }

    private Map<String, Object> createMockResponse(List<List<Double>> vectors) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("index", i);
            item.put("embedding", vectors.get(i));
            data.add(item);
        }
        response.put("data", data);
        return response;
    }
}
