package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkDraft {
    private Integer chunkIndex;
    private String content;
    private Integer pageStart;
    private Integer pageEnd;
    private String sectionTitle;
    private Integer tokenCount;
    private String contentHash;
}
