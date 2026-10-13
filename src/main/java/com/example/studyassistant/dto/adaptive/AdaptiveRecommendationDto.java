package com.example.studyassistant.dto.adaptive;

import com.example.studyassistant.dto.MasteryState;
import lombok.Data;

import java.util.List;

@Data
public class AdaptiveRecommendationDto {
    private Long conceptId;
    private String conceptName;
    private List<AdaptiveReasonCode> reasonCodes;
    private MasteryState masteryState;
    private Double masteryEstimate; // null if UNSEEN
    private int evidenceCount; // 0 if UNSEEN
}
