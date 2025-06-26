package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssessmentRequest {
    private Long courseId;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Double passMark;
    private Integer maxAttempts;
    private List<QuestionOrderDto> questions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionOrderDto {
        private Long questionId;
        private Integer order;
    }
} 