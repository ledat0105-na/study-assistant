package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.VectorRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Manual integration test requiring local Qdrant on port 6333")
@org.junit.jupiter.api.Tag("Integration")
public class QdrantIntegrationTest {

    @Test
    void testQdrantLifecycle() {
        QdrantVectorStore store = new QdrantVectorStore(new RestTemplate());
        ReflectionTestUtils.setField(store, "qdrantUrl", "http://localhost:6333");
        ReflectionTestUtils.setField(store, "collectionPrefix", "rag_test");
        ReflectionTestUtils.setField(store, "embeddingModel", "integration_test_model");

        int dimension = 4;
        
        // 1. Ensure collection
        store.ensureCollection(dimension);

        // 2. Upsert vectors
        VectorRecord r1 = VectorRecord.builder()
                .vectorId(UUID.randomUUID().toString())
                .vector(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                .documentId(1L)
                .indexVersion("v1")
                .chunkId(1L)
                .embeddingModel("integration")
                .build();
                
        VectorRecord r2 = VectorRecord.builder()
                .vectorId(UUID.randomUUID().toString())
                .vector(Arrays.asList(0.5, 0.6, 0.7, 0.8))
                .documentId(2L) // Different doc
                .indexVersion("v1")
                .chunkId(2L)
                .embeddingModel("integration")
                .build();
                
        store.upsert(Arrays.asList(r1, r2));

        // 3. Search and filter
        List<VectorRecord> results = store.search(Arrays.asList(0.1, 0.2, 0.3, 0.4), 1L, "v1", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentId()).isEqualTo(1L);

        // 4. Delete
        store.deleteByDocumentAndVersion(1L, "v1");
        
        // 5. Verify removed
        List<VectorRecord> afterDelete = store.search(Arrays.asList(0.1, 0.2, 0.3, 0.4), 1L, "v1", 10);
        assertThat(afterDelete).isEmpty();
    }
}
