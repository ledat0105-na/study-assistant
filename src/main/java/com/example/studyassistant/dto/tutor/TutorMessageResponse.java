package com.example.studyassistant.dto.tutor;

import com.example.studyassistant.service.tutor.TutorMode;
import com.example.studyassistant.retrieval.model.CitationDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TutorMessageResponse {
    private Long conversationId;
    private Long targetConceptId;
    private Long focusConceptId;
    private TutorMode mode;
    private String message;
    private boolean hasSufficientEvidence;
    private List<CitationDto> citations;
}
