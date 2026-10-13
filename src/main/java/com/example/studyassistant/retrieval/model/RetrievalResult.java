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
public class RetrievalResult {
    private List<RetrievedChunk> chunks;
    private boolean hasSufficientEvidence;
    private int totalTokens;
}
