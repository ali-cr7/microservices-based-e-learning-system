package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAttemptRequest {
    private Long attemptId;
    private Map<String, String> answers;
} 