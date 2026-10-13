package com.example.studyassistant.dto.adaptive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveRecommendationResponse {
    private String policyVersion;
    private int count;
    private List<AdaptiveRecommendationDto> recommendations;
}
