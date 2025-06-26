package com.elearning.subscription_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentEvaluationDto {
    private Long id;
    private Long studentId;
    private String instructorEmail;
    private String evaluationText;
    private Long courseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 