package com.elearning.subscription_service.services;

import com.elearning.subscription_service.dto.CourseSummaryRequestDto;
import com.elearning.subscription_service.dto.CourseSummaryResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;

@Service
public class AssessmentClientService {
    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "assessmentService", fallbackMethod = "getCourseSummaryFallback")
    public CourseSummaryResponseDto getCourseSummary(Long courseId, String learnerEmail) {
        CourseSummaryRequestDto request = new CourseSummaryRequestDto(courseId);
        return restTemplate.postForObject(
                "http://assessment-service/api/assessments/course/summary-by-email?learnerEmail=" + learnerEmail,
                request,
                CourseSummaryResponseDto.class
        );
    }

    public CourseSummaryResponseDto getCourseSummaryFallback(Long courseId, String learnerEmail, Throwable t) {
        System.err.println("Fallback for getCourseSummary for course " + courseId + ". Error: " + t.getMessage());
        CourseSummaryResponseDto emptySummary = new CourseSummaryResponseDto();
        emptySummary.setAssessments(Collections.emptyList());
        emptySummary.setEvaluation(null);
        return emptySummary;
    }
}
