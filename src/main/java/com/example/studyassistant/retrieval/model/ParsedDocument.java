package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {
    private List<ParsedSegment> segments = new ArrayList<>();
    
    public String toBackwardCompatibleRawText() {
        StringBuilder sb = new StringBuilder();
        for (ParsedSegment segment : segments) {
            if (segment.getPageStart() != null && segment.getPageStart().equals(segment.getPageEnd())) {
                if (Boolean.TRUE.equals(segment.getIsSlide())) {
                    sb.append("[Slide ").append(segment.getPageStart()).append("]\n");
                } else if (!Boolean.TRUE.equals(segment.getIsWord())) {
                    sb.append("[Trang ").append(segment.getPageStart()).append("]\n");
                }
            }
            sb.append(segment.getText()).append("\n");
        }
        return sb.toString();
    }
}
