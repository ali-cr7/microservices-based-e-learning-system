package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAttemptDto {
    private Long id;
    private Long assessmentId;
    private String assessmentTitle;
    private String learnerEmail;
    private Long studentId;
    private String studentName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean completed;
    private int score;
    private Map<String, String> answers;
    private String courseName;
    private Integer durationMinutes;
    private Double passMark;
    private boolean passed;
}
