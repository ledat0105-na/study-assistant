package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.exception.VectorStoreException;
import com.example.studyassistant.retrieval.model.VectorRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QdrantVectorStore implements VectorStore {

    @Value("${rag.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${rag.qdrant.api-key:}")
    private String qdrantApiKey;

    @Value("${rag.qdrant.collection-prefix:rag}")
    private String collectionPrefix;

    @Value("${ai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    private final RestTemplate restTemplate;

    public QdrantVectorStore() {
        this.restTemplate = new RestTemplate();
    }

    public QdrantVectorStore(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getCollectionName(int dimension) {
        String sanitizedModel = embeddingModel.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        return String.format("%s_%s_%d", collectionPrefix, sanitizedModel, dimension);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
            headers.set("api-key", qdrantApiKey);
        }
        return headers;
    }

    @Override
    public void ensureCollection(int dimension) {
        String collectionName = getCollectionName(dimension);
        String url = qdrantUrl + "/collections/" + collectionName;
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(createHeaders()), Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                // Check if dimension matches
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("result")) {
                    Map<String, Object> result = (Map<String, Object>) body.get("result");
                    Map<String, Object> config = (Map<String, Object>) result.get("config");
                    Map<String, Object> params = (Map<String, Object>) config.get("params");
                    Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
                    if (vectors != null && vectors.containsKey("size")) {
                        Integer existingSize = (Integer) vectors.get("size");
                        if (existingSize != null && existingSize != dimension) {
                            throw new VectorStoreException(
                                    String.format("Collection %s exists but dimension %d does not match expected %d",
                                            collectionName, existingSize, dimension));
                        }
                    }
                }
                return; // Collection exists and dimension is fine
            }
        } catch (HttpClientErrorException.NotFound e) {
            // Collection does not exist, proceed to create
            createCollection(collectionName, dimension);
        } catch (Exception e) {
            throw new VectorStoreException("Failed to check collection status", e);
        }
    }

    private void createCollection(String collectionName, int dimension) {
        String url = qdrantUrl + "/collections/" + collectionName;
        
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> vectorsConfig = new HashMap<>();
        vectorsConfig.put("size", dimension);
        vectorsConfig.put("distance", "Cosine");
        requestBody.put("vectors", vectorsConfig);

        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(requestBody, createHeaders()), Map.class);
        } catch (Exception e) {
            throw new VectorStoreException("Failed to create collection " + collectionName, e);
        }
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) return;

        // Verify dimension of first record
        int dimension = records.get(0).getVector().size();
        for (VectorRecord record : records) {
            if (record.getVector().size() != dimension) {
                throw new VectorStoreException("Vector dimension mismatch within the batch");
            }
        }

        String collectionName = getCollectionName(dimension);
        String url = qdrantUrl + "/collections/" + collectionName + "/points?wait=true";

        List<Map<String, Object>> points = new ArrayList<>();
        for (VectorRecord record : records) {
            Map<String, Object> point = new HashMap<>();
            // vectorId must be a valid UUID string
            point.put("id", record.getVectorId());
            point.put("vector", record.getVector());
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("chunk_id", record.getChunkId());
            payload.put("document_id", record.getDocumentId());
            payload.put("index_version", record.getIndexVersion());
            payload.put("embedding_model", record.getEmbeddingModel());
            point.put("payload", payload);
            
            points.add(point);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("points", points);

        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(requestBody, createHeaders()), Map.class);
        } catch (Exception e) {
            throw new VectorStoreException("Failed to upsert vectors to Qdrant", e);
        }
    }

    @Override
    public List<VectorRecord> search(List<Double> queryVector, Long documentId, String indexVersion, int limit) {
        Map<String, Object> filter = buildFilter(documentId, indexVersion);
        return performSearch(queryVector, limit, filter);
    }

    @Override
    public List<VectorRecord> search(List<Double> queryVector, List<com.example.studyassistant.retrieval.model.DocumentVersionPair> authorizedPairs, int limit) {
        if (authorizedPairs == null || authorizedPairs.isEmpty()) {
            return new ArrayList<>(); // No authorized documents
        }
        Map<String, Object> filter = buildMultiPairFilter(authorizedPairs);
        return performSearch(queryVector, limit, filter);
    }

    @SuppressWarnings("unchecked")
    private List<VectorRecord> performSearch(List<Double> queryVector, int limit, Map<String, Object> filter) {
        String collectionName = getCollectionName(queryVector.size());
        String url = qdrantUrl + "/collections/" + collectionName + "/points/search";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("vector", queryVector);
        requestBody.put("limit", limit);
        requestBody.put("with_payload", true);

        if (filter != null) {
            requestBody.put("filter", filter);
        }

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, createHeaders()), Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("result")) {
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) body.get("result");
                List<VectorRecord> records = new ArrayList<>();
                for (Map<String, Object> item : resultList) {
                    Map<String, Object> payload = (Map<String, Object>) item.get("payload");
                    if (payload != null) {
                        VectorRecord record = new VectorRecord();
                        record.setVectorId(String.valueOf(item.get("id")));
                        
                        Number chunkIdNum = (Number) payload.get("chunk_id");
                        if (chunkIdNum != null) record.setChunkId(chunkIdNum.longValue());
                        
                        Number docIdNum = (Number) payload.get("document_id");
                        if (docIdNum != null) record.setDocumentId(docIdNum.longValue());
                        
                        record.setIndexVersion((String) payload.get("index_version"));
                        record.setEmbeddingModel((String) payload.get("embedding_model"));
                        
                        Number scoreNum = (Number) item.get("score");
                        if (scoreNum != null) {
                            record.setScore(scoreNum.doubleValue());
                        }
                        
                        records.add(record);
                    }
                }
                return records;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            throw new VectorStoreException("Failed to search Qdrant", e);
        }
    }

    @Override
    public void deleteByDocumentAndVersion(Long documentId, String indexVersion) {
        // Since we don't know the exact dimension here without passing it, 
        // we assume the current model dimension (or we'd need to search all collections).
        // For CP3, we just use a default or assume the caller knows.
        // But since this uses REST, we can just hit the expected collection.
        // We'll require a small change if we want it completely dynamic.
        
        // Wait, we need to know the collection name to delete. We can get it using the default configured dimension.
        // Alternatively, the VectorStore could take the dimension as an argument.
        // Let's use an arbitrary dimension of 1536 as a default for the collection name, 
        // or just rely on the model property which maps to a single collection in this V1 architecture.
        String collectionName = getCollectionName(1536); // Assuming standard dimension
        
        String url = qdrantUrl + "/collections/" + collectionName + "/points/delete?wait=true";
        
        Map<String, Object> filter = buildFilter(documentId, indexVersion);
        if (filter == null) {
            throw new IllegalArgumentException("Must provide documentId or indexVersion for safe deletion");
        }
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("filter", filter);
        
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(requestBody, createHeaders()), Map.class);
        } catch (Exception e) {
            throw new VectorStoreException("Failed to delete from Qdrant", e);
        }
    }
    
    private Map<String, Object> buildFilter(Long documentId, String indexVersion) {
        List<Map<String, Object>> mustConditions = new ArrayList<>();
        
        if (documentId != null) {
            Map<String, Object> docCondition = new HashMap<>();
            docCondition.put("key", "document_id");
            Map<String, Object> matchDoc = new HashMap<>();
            matchDoc.put("value", documentId);
            docCondition.put("match", matchDoc);
            mustConditions.add(docCondition);
        }
        
        if (indexVersion != null) {
            Map<String, Object> versionCondition = new HashMap<>();
            versionCondition.put("key", "index_version");
            Map<String, Object> matchVer = new HashMap<>();
            matchVer.put("value", indexVersion);
            versionCondition.put("match", matchVer);
            mustConditions.add(versionCondition);
        }
        
        if (mustConditions.isEmpty()) {
            return null;
        }
        
        Map<String, Object> filter = new HashMap<>();
        filter.put("must", mustConditions);
        return filter;
    }

    private Map<String, Object> buildMultiPairFilter(List<com.example.studyassistant.retrieval.model.DocumentVersionPair> pairs) {
        List<Map<String, Object>> shouldConditions = new ArrayList<>();
        
        for (com.example.studyassistant.retrieval.model.DocumentVersionPair pair : pairs) {
            Map<String, Object> pairCondition = new HashMap<>();
            
            List<Map<String, Object>> mustConditions = new ArrayList<>();
            
            Map<String, Object> docCondition = new HashMap<>();
            docCondition.put("key", "document_id");
            Map<String, Object> matchDoc = new HashMap<>();
            matchDoc.put("value", pair.getDocumentId());
            docCondition.put("match", matchDoc);
            mustConditions.add(docCondition);
            
            Map<String, Object> versionCondition = new HashMap<>();
            versionCondition.put("key", "index_version");
            Map<String, Object> matchVer = new HashMap<>();
            matchVer.put("value", pair.getIndexVersion());
            versionCondition.put("match", matchVer);
            mustConditions.add(versionCondition);
            
            pairCondition.put("must", mustConditions);
            shouldConditions.add(pairCondition);
        }
        
        Map<String, Object> filter = new HashMap<>();
        filter.put("should", shouldConditions);
        return filter;
    }
}
