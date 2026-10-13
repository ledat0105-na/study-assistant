package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedChunk {
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private Integer chunkIndex;
    private String content;
    private Integer pageStart;
    private Integer pageEnd;
    private String sectionTitle;
    private Integer tokenCount;
    private String indexVersion;

    // Retrieval Metadata
    private Integer denseRank;
    private Integer keywordRank;
    private Double denseScore;
    private Double keywordScore;
    private Double rrfScore;
}
