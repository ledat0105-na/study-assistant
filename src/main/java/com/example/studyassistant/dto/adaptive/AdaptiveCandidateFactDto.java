package com.example.studyassistant.dto.adaptive;

import com.example.studyassistant.dto.MasteryState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveCandidateFactDto {
    private Long conceptId;
    private String conceptName;

    private MasteryState masteryState;
    private Double masteryEstimate;
    private int evidenceCount;
    private LocalDateTime lastEvidenceAt;

    private Long firstDocumentId;
    private Integer firstChunkIndex;

    private List<Long> prerequisiteConceptIds;
}
