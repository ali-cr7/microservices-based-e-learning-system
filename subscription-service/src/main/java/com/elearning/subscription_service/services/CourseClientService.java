package com.elearning.subscription_service.services;

import com.elearning.subscription_service.dto.CourseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CourseClientService {
    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "courseService", fallbackMethod = "getCourseDetailsFallback")
    public CourseDto getCourseDetails(Long courseId) {
        return restTemplate.getForObject("http://course-service/api/courses/" + courseId, CourseDto.class);
    }

    public CourseDto getCourseDetailsFallback(Long courseId, Throwable t) {
        System.err.println("Fallback for getCourseDetails for courseId " + courseId + ". Error: " + t.getMessage());
        CourseDto defaultCourse = new CourseDto();
        defaultCourse.setId(courseId);
        defaultCourse.setTitle("Course info unavailable");
        defaultCourse.setDescription("Course details are temporarily unavailable");
        defaultCourse.setInstructor("N/A");
        defaultCourse.setApproved(false);
        defaultCourse.setPrice(0.0);
        System.out.println("DEBUG: Returning fallback CourseDto: " + defaultCourse);
        return defaultCourse;
    }
} 