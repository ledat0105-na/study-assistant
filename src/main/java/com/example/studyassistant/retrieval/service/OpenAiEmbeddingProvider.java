package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.exception.EmbeddingProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${ai.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${ai.embedding.dimension:1536}")
    private int dimension;

    @Value("${ai.embedding.batch-size:100}")
    private int batchSize;

    private final RestTemplate restTemplate;

    public OpenAiEmbeddingProvider() {
        this.restTemplate = new RestTemplate();
        // A production system would configure timeouts here via RestTemplateBuilder
        // To keep it minimal without adding Spring Boot specific config classes here, 
        // we use the default RestTemplate which relies on the OS default timeout.
        // We log configuring this in a real setup.
    }

    // For testing
    public OpenAiEmbeddingProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot embed empty or null text");
        }
        List<List<Double>> result = embedBatchInternal(Collections.singletonList(text));
        if (result == null || result.isEmpty()) {
            throw new EmbeddingProviderException("Provider returned empty result for single embedding");
        }
        return result.get(0);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<List<Double>> allEmbeddings = new ArrayList<>(texts.size());
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            allEmbeddings.addAll(embedBatchInternal(batch));
        }
        
        return allEmbeddings;
    }

    @Override
    public String getModelIdentifier() {
        return model;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    private List<List<Double>> embedBatchInternal(List<String> texts) {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            throw new EmbeddingProviderException("OpenAI API key is not configured");
        }

        String url = "https://api.openai.com/v1/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", texts);
        // text-embedding-3 series supports explicit dimension parameter
        if (model.startsWith("text-embedding-3")) {
            requestBody.put("dimensions", dimension);
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);
            return extractAndValidateEmbeddings(responseEntity, texts.size());
        } catch (HttpClientErrorException e) {
            // Do not log the exact body if it contains sensitive input, but log the status
            throw new EmbeddingProviderException("OpenAI API error: " + e.getStatusCode(), e);
        } catch (EmbeddingProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingProviderException("Failed to call OpenAI embedding API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> extractAndValidateEmbeddings(ResponseEntity<Map> responseEntity, int expectedCount) {
        if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
            throw new EmbeddingProviderException("Invalid API response status or empty body");
        }

        Map<String, Object> body = responseEntity.getBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");

        if (data == null) {
            throw new EmbeddingProviderException("API response missing 'data' field");
        }

        if (data.size() != expectedCount) {
            throw new EmbeddingProviderException("Expected " + expectedCount + " embeddings, but received " + data.size());
        }

        // OpenAI returns data sorted by index, but we should sort just to be absolutely safe
        data.sort((a, b) -> {
            Integer idxA = (Integer) a.get("index");
            Integer idxB = (Integer) b.get("index");
            return idxA.compareTo(idxB);
        });

        List<List<Double>> embeddings = new ArrayList<>(expectedCount);
        for (Map<String, Object> item : data) {
            List<Number> vectorNums = (List<Number>) item.get("embedding");
            if (vectorNums == null) {
                throw new EmbeddingProviderException("Missing 'embedding' array in data item");
            }
            if (vectorNums.size() != dimension) {
                throw new EmbeddingProviderException("Dimension mismatch. Expected " + dimension + ", got " + vectorNums.size());
            }
            
            List<Double> vector = vectorNums.stream()
                    .map(Number::doubleValue)
                    .collect(Collectors.toList());
            embeddings.add(vector);
        }

        return embeddings;
    }
}
