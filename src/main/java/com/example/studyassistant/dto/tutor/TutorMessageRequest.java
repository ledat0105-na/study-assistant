package com.example.studyassistant.dto.tutor;

import com.example.studyassistant.service.tutor.TutorRequestedMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TutorMessageRequest {
    @NotBlank(message = "message must not be blank")
    private String message;
    private Long conceptId;
    private TutorRequestedMode requestedMode;
}
