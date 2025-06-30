package com.elearning.subscription_service.services;

import com.elearning.subscription_service.entities.Subscription;
import com.elearning.subscription_service.dto.*;
import com.elearning.subscription_service.repositories.SubscriptionRepository;
import com.elearning.subscription_service.utils.JwtUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {


    private final SubscriptionRepository subscriptionRepository;
    private final CourseClientService courseClientService;
    private final UserClientService userClientService;
    private final AssessmentClientService assessmentClientService;
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository, CourseClientService courseClientService, UserClientService userClientService, AssessmentClientService assessmentClientService, RestTemplate restTemplate, JwtUtil jwtUtil) {
        this.subscriptionRepository = subscriptionRepository;
        this.courseClientService = courseClientService;
        this.userClientService = userClientService;
        this.assessmentClientService = assessmentClientService;
        this.restTemplate = restTemplate;
        this.jwtUtil = jwtUtil;
    }
    @CircuitBreaker(name = "enrollmentCircuitBreaker", fallbackMethod = "enrollInCourseFallback")
    @TimeLimiter(name = "defaultTimeLimiter")
    @RateLimiter(name = "defaultRateLimiter")
    public CompletableFuture<Subscription> enrollInCourse(String learnerEmail, Long courseId) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Verify learner existence and role with User-Service
            UserDto learner = userClientService.getUserDetails(learnerEmail);
            if (learner == null) { // Can be null from fallback or actual 404
                 throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner not found or user service is unavailable.");
            }
            if (!"LEARNER".equals(learner.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only learners can enroll in courses.");
            }

            // 2. Verify course existence and status with Course-Service
            CourseDto course = courseClientService.getCourseDetails(courseId);
            if (course == null) { // Can be null from fallback or actual 404
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found or course service is unavailable.");
            }
            if (!course.isApproved()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is not yet approved or available for enrollment.");
            }

            // 3. Check if already enrolled (ACTIVE or PENDING)
            Optional<Subscription> existingSubscription = subscriptionRepository.findByLearnerEmailAndCourseId(learnerEmail, courseId);
            if (existingSubscription.isPresent()) {
                if (List.of("ACTIVE", "PENDING_PAYMENT").contains(existingSubscription.get().getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled or have a pending enrollment for this course.");
                }
            }

            // 4. Create new subscription
            Subscription subscription = new Subscription();
            subscription.setLearnerEmail(learnerEmail);
            subscription.setCourseId(courseId);
            subscription.setSubscriptionDate(LocalDateTime.now());
            subscription.setStatus("PENDING_PAYMENT");
            subscription.setPriceAtSubscription(course.getPrice());

            return subscriptionRepository.save(subscription);
        });
    }
    public CompletableFuture<Subscription> enrollInCourseFallback(String learnerEmail, Long courseId, Throwable t) {
        System.err.println("Fallback for enrollInCourse due to: " + t.getMessage());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Enrollment is currently unavailable. Please try again later.");
    }
    public Optional<Subscription> updateSubscriptionStatus(Long subscriptionId, String newStatus) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isPresent()) {
            Subscription subscription = subscriptionOpt.get();
            subscription.setStatus(newStatus);
            return Optional.of(subscriptionRepository.save(subscription));
        }
        return Optional.empty();
    }
    @TimeLimiter(name = "defaultTimeLimiter")
    @RateLimiter(name = "defaultRateLimiter")
    public CompletableFuture<List<SubscriptionCourseDto>> getMyEnrolledCourses(String learnerEmail) {
         return CompletableFuture.supplyAsync(() -> {
            List<Subscription> subscriptions = subscriptionRepository.findByLearnerEmailAndStatus(learnerEmail, "ACTIVE");

            return subscriptions.stream()
                .map(subscription -> {
                    CourseDto course = courseClientService.getCourseDetails(subscription.getCourseId());
                    // Defensive: check for fallback/empty CourseDto (assuming id is always set for real courses)
                    if (course == null || course.getId() == null) {
                        return new SubscriptionCourseDto(
                            subscription,
                            null, // No course details available
                            "Course info unavailable",
                            Collections.emptyList(),
                            null
                        );
                    }
                    String instructorName = (course.getInstructor() != null) ? getInstructorName(course.getInstructor()) : "N/A";
                    CourseSummaryResponseDto summary = assessmentClientService.getCourseSummary(subscription.getCourseId(), subscription.getLearnerEmail());
                    List<AssessmentScoreDto> assessmentScores = (summary != null) ? summary.getAssessments() : Collections.emptyList();
                    StudentEvaluationDto evaluation = (summary != null) ? summary.getEvaluation() : null;
                    return new SubscriptionCourseDto(subscription, course, instructorName, assessmentScores, evaluation);
                })
                .collect(Collectors.toList());
        });
    }

    @TimeLimiter(name = "defaultTimeLimiter")
    @RateLimiter(name = "defaultRateLimiter")
    public CompletableFuture<SubscriptionCourseDto> getMyEnrolledCourseById(String learnerEmail, Long courseId) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByLearnerEmailAndCourseId(learnerEmail, courseId);
            
            if (subscriptionOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found for this course.");
            }
            
            Subscription subscription = subscriptionOpt.get();
            CourseDto course = courseClientService.getCourseDetails(subscription.getCourseId());
            
            // Defensive: check for fallback/empty CourseDto
            if (course == null || course.getId() == null) {
                return new SubscriptionCourseDto(
                    subscription,
                    null, // No course details available
                    "Course info unavailable",
                    Collections.emptyList(),
                    null
                );
            }
            
            String instructorName = (course.getInstructor() != null) ? getInstructorName(course.getInstructor()) : "N/A";
            CourseSummaryResponseDto summary = assessmentClientService.getCourseSummary(subscription.getCourseId(), subscription.getLearnerEmail());
            List<AssessmentScoreDto> assessmentScores = (summary != null) ? summary.getAssessments() : Collections.emptyList();
            StudentEvaluationDto evaluation = (summary != null) ? summary.getEvaluation() : null;
            return new SubscriptionCourseDto(subscription, course, instructorName, assessmentScores, evaluation);
        });
    }
    @TimeLimiter(name = "defaultTimeLimiter")
    @RateLimiter(name = "defaultRateLimiter")
    public CompletableFuture<Optional<SubscriptionCourseDto>> getSubscriptionById(long id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(id);
            if (subscriptionOpt.isPresent()) {
                Subscription subscription = subscriptionOpt.get();
                CourseDto course = courseClientService.getCourseDetails(subscription.getCourseId());
                String instructorName = (course != null && course.getInstructor() != null) ?
                                        getInstructorName(course.getInstructor()) : "N/A";

                CourseSummaryResponseDto summary = assessmentClientService.getCourseSummary(subscription.getCourseId(), subscription.getLearnerEmail());

                List<AssessmentScoreDto> assessmentScores = (summary != null) ? summary.getAssessments() : Collections.emptyList();
                StudentEvaluationDto evaluation = (summary != null) ? summary.getEvaluation() : null;

                return Optional.of(new SubscriptionCourseDto(subscription, course, instructorName, assessmentScores, evaluation));
            }
            return Optional.empty();
        });
    }
    private String getInstructorName(String email) {
        UserDto instructor = userClientService.getUserDetails(email);
        return (instructor != null) ? instructor.getName() : "N/A";
    }
    public boolean cancelSubscription(Long subscriptionId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isPresent()) {
            Subscription subscription = subscriptionOpt.get();
            // Only allow cancellation if the subscription is active or pending payment
            if ("ACTIVE".equals(subscription.getStatus()) || "PENDING_PAYMENT".equals(subscription.getStatus())) {
                subscription.setStatus("CANCELLED");
                subscriptionRepository.save(subscription);
                return true;
            }
        }
        return false;
    }
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }
}









