package com.example.studyassistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Map;

@Data
public class QuizSubmissionRequest {
    @NotNull(message = "submissionToken is required")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "Invalid UUID format")
    private String submissionToken;

    @NotNull(message = "answers map cannot be null")
    private Map<Long, String> answers;
}
