package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorRecord {
    private String vectorId;
    private List<Double> vector;
    
    // Payload / metadata fields
    private Long chunkId;
    private Long documentId;
    private String indexVersion;
    private String embeddingModel;
    private Double score; // Returned by similarity search
}
