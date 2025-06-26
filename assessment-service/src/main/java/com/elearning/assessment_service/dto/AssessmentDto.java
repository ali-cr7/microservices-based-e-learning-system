package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentDto {
    private Long id;
    private String title;
    private Long courseId;

    private String description;
    private String courseName;
   
    private Integer durationMinutes;
    private Double passMark;
    private Integer maxAttempts;
    private LocalDateTime creationDate;
    private String instructorEmail;
    private List<AssessmentQuestionDto> assessmentQuestions;
} 