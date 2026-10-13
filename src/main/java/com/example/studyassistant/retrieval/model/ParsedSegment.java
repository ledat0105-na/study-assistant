package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedSegment {
    private String text;
    private Integer pageStart;
    private Integer pageEnd;
    private String sectionTitle;
    private Boolean isSlide;
    private Boolean isWord;
}
