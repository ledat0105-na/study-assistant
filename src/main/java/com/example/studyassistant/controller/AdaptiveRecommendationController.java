package com.example.studyassistant.controller;

import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationResponse;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notebooks/{notebookId}/recommendations")
@RequiredArgsConstructor
public class AdaptiveRecommendationController {

    private final AdaptiveRecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<AdaptiveRecommendationResponse> getRecommendations(
            @PathVariable Long notebookId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = (Long) session.getAttribute("userId");

        List<AdaptiveRecommendationDto> recommendations = recommendationService.getRecommendations(userId, notebookId, limit);
        
        AdaptiveRecommendationResponse response = new AdaptiveRecommendationResponse(
                AdaptiveRecommendationService.POLICY_VERSION,
                recommendations.size(),
                List.copyOf(recommendations)
        );

        return ResponseEntity.ok(response);
    }
}
