package com.example.studyassistant.retrieval.service;

import java.util.List;

public interface EmbeddingProvider {
    /**
     * Embeds a single text.
     * @param text The text to embed.
     * @return The vector representation.
     */
    List<Double> embed(String text);

    /**
     * Embeds a batch of texts.
     * @param texts The list of texts to embed.
     * @return A list of vector representations, preserving input order.
     */
    List<List<Double>> embedBatch(List<String> texts);

    /**
     * Gets the identifier of the embedding model being used.
     * @return Model identifier (e.g., "text-embedding-3-small")
     */
    String getModelIdentifier();

    /**
     * Gets the expected dimension of the vectors produced.
     * @return The dimension.
     */
    int getDimension();
}
