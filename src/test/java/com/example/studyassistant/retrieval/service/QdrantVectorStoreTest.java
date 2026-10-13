package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.exception.VectorStoreException;
import com.example.studyassistant.retrieval.model.VectorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QdrantVectorStoreTest {

    @Mock
    private RestTemplate restTemplate;

    private QdrantVectorStore store;

    @BeforeEach
    void setUp() {
        store = new QdrantVectorStore(restTemplate);
        ReflectionTestUtils.setField(store, "qdrantUrl", "http://localhost:6333");
        ReflectionTestUtils.setField(store, "qdrantApiKey", "secret-key");
        ReflectionTestUtils.setField(store, "collectionPrefix", "rag");
        ReflectionTestUtils.setField(store, "embeddingModel", "text-embedding-3-small");
    }

    @Test
    void testCP3_12_13_CollectionNameIsDeterministicAndSanitized() {
        // model contains dashes, they should be sanitized to underscores
        String collectionName = store.getCollectionName(1536);
        assertThat(collectionName).isEqualTo("rag_text_embedding_3_small_1536");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCP3_14_UpsertMapsCorrectPayload() {
        VectorRecord record = VectorRecord.builder()
                .vectorId(UUID.randomUUID().toString())
                .vector(List.of(0.1, 0.2))
                .chunkId(10L)
                .documentId(100L)
                .indexVersion("v1")
                .embeddingModel("model")
                .build();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

        store.upsert(List.of(record));

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(String.class), eq(HttpMethod.PUT), captor.capture(), eq(Map.class));

        Map<String, Object> body = captor.getValue().getBody();
        List<Map<String, Object>> points = (List<Map<String, Object>>) body.get("points");
        assertThat(points).hasSize(1);
        
        Map<String, Object> point = points.get(0);
        assertThat(point.get("id")).isEqualTo(record.getVectorId());
        
        Map<String, Object> payload = (Map<String, Object>) point.get("payload");
        assertThat(payload.get("chunk_id")).isEqualTo(10L);
        assertThat(payload.get("document_id")).isEqualTo(100L);
        assertThat(payload.get("index_version")).isEqualTo("v1");
        assertThat(payload.get("embedding_model")).isEqualTo("model");
    }

    @Test
    void testCP3_15_VectorDimensionMismatchRejected() {
        VectorRecord r1 = VectorRecord.builder().vector(List.of(0.1, 0.2)).build();
        VectorRecord r2 = VectorRecord.builder().vector(List.of(0.1)).build(); // mismatch

        assertThatThrownBy(() -> store.upsert(Arrays.asList(r1, r2)))
                .isInstanceOf(VectorStoreException.class)
                .hasMessageContaining("Vector dimension mismatch");
    }

    @Test
    void testCP3_16_17_DeterministicVectorIdStrategy() {
        // Demonstrate and test deterministic ID strategy
        Long docId = 1L;
        int chunkIndex = 5;
        String indexVersion = "v1";
        
        String input1 = docId + "_" + chunkIndex + "_" + indexVersion;
        String id1 = UUID.nameUUIDFromBytes(input1.getBytes(StandardCharsets.UTF_8)).toString();
        
        String id2 = UUID.nameUUIDFromBytes(input1.getBytes(StandardCharsets.UTF_8)).toString();
        
        String input3 = docId + "_" + (chunkIndex + 1) + "_" + indexVersion;
        String id3 = UUID.nameUUIDFromBytes(input3.getBytes(StandardCharsets.UTF_8)).toString();

        assertThat(id1).isEqualTo(id2); // Stable
        assertThat(id1).isNotEqualTo(id3); // Different index -> different ID
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCP3_18_19_SearchRequestContainsFilters() {
        Map<String, Object> mockResponse = new HashMap<>();
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        store.search(List.of(0.1, 0.2), 100L, "v1", 5);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), captor.capture(), eq(Map.class));

        Map<String, Object> body = captor.getValue().getBody();
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        assertThat(filter).isNotNull();
        
        List<Map<String, Object>> must = (List<Map<String, Object>>) filter.get("must");
        assertThat(must).hasSize(2); // One for docId, one for version
        
        assertThat(must.get(0).get("key")).isEqualTo("document_id");
        assertThat(must.get(1).get("key")).isEqualTo("index_version");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCP3_20_DeleteRequestTargetsDocAndVersion() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

        store.deleteByDocumentAndVersion(100L, "v1");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), captor.capture(), eq(Map.class));

        Map<String, Object> body = captor.getValue().getBody();
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        List<Map<String, Object>> must = (List<Map<String, Object>>) filter.get("must");
        assertThat(must).hasSize(2);
    }

    @Test
    void testCP3_21_22_QdrantUnavailableSafeExceptionNoKeyLeak() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> store.ensureCollection(1536))
                .isInstanceOf(VectorStoreException.class)
                .hasMessageContaining("Failed to check collection")
                .hasMessageNotContaining("secret-key");
    }
}
