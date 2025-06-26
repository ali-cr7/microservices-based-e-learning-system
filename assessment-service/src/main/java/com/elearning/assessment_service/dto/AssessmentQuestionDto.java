package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestionDto {
    private Long id;
    private QuestionDto question;
    private Integer questionOrder;
} 