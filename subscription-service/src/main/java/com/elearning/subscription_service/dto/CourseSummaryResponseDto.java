package com.elearning.subscription_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryResponseDto {
    private List<AssessmentScoreDto> assessments;
    private StudentEvaluationDto evaluation;
} 