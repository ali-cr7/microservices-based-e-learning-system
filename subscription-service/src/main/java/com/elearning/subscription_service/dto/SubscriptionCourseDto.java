package com.elearning.subscription_service.dto;

import com.elearning.subscription_service.Subscription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCourseDto {
    private Subscription subscription;
    private CourseDto course;
    private String instructorName;
    private List<AssessmentScoreDto> assessmentScores;
    private StudentEvaluationDto evaluation;
    
    // Constructor for backward compatibility
    public SubscriptionCourseDto(Subscription subscription, CourseDto course, String instructorName) {
        this.subscription = subscription;
        this.course = course;
        this.instructorName = instructorName;
        this.assessmentScores = null;
        this.evaluation = null;
    }
} 