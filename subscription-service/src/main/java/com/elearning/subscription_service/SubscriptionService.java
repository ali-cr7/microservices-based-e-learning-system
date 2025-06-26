package com.elearning.subscription_service;

import com.elearning.subscription_service.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository, RestTemplate restTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.restTemplate = restTemplate;
    }

    public Subscription enrollInCourse(String learnerEmail, Long courseId) {
        // 1. Verify learner existence and role with User-Service
        UserDto learner;
        try {
            learner = restTemplate.getForObject("http://user-service/api/users/email?email=" + learnerEmail, UserDto.class);
            if (learner == null || !"LEARNER".equals(learner.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only learners can enroll in courses.");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner not found.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error communicating with user service: " + e.getMessage());
        }

        // 2. Verify course existence and status with Course-Service
        CourseDto course;
        try {
            course = restTemplate.getForObject("http://course-service/api/courses/" + courseId, CourseDto.class);
            if (course == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
            }
            if (!course.isApproved()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is not yet approved or available for enrollment.");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error communicating with course service: " + e.getMessage());
        }

        // 3. Check if already enrolled (ACTIVE or PENDING)
        Optional<Subscription> existingSubscription = subscriptionRepository.findByLearnerEmailAndCourseId(learnerEmail, courseId);
        if (existingSubscription.isPresent()) {
            if (existingSubscription.get().getStatus().equals("ACTIVE") || existingSubscription.get().getStatus().equals("PENDING_PAYMENT")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled or have a pending enrollment for this course.");
            }
        }

        // 4. Create new subscription
        Subscription subscription = new Subscription();
        subscription.setLearnerEmail(learnerEmail);
        subscription.setCourseId(courseId);
        subscription.setSubscriptionDate(LocalDateTime.now());
        subscription.setStatus("PENDING_PAYMENT"); // Start as pending payment
        subscription.setPriceAtSubscription(course.getPrice()); // Use actual course price

        return subscriptionRepository.save(subscription);
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

    public List<SubscriptionCourseDto> getMyEnrolledCourses(String learnerEmail) {
        List<Subscription> subscriptions = subscriptionRepository.findByLearnerEmailAndStatus(learnerEmail, "ACTIVE");

        return subscriptions.stream()
                .map(subscription -> {
                    CourseDto course = null;
                    String instructorName = null;
                    List<AssessmentScoreDto> assessmentScores = null;
                    StudentEvaluationDto evaluation = null;
                    try {
                        course = restTemplate.getForObject("http://course-service/api/courses/" + subscription.getCourseId(), CourseDto.class);
                        if (course != null && course.getInstructor() != null) {
                            UserDto instructor = restTemplate.getForObject("http://user-service/api/users/email?email=" + course.getInstructor(), UserDto.class);
                            if (instructor != null) {
                                instructorName = instructor.getName();
                            }
                        }

                        // Fetch assessment scores and evaluation from assessment service
                        try {
                            CourseSummaryRequestDto request = new CourseSummaryRequestDto(subscription.getCourseId());
                            CourseSummaryResponseDto summaryResponse = restTemplate.postForObject(
                                    "http://assessment-service/api/assessments/course/summary-by-email?learnerEmail="+subscription.getLearnerEmail(),
                                request,
                                CourseSummaryResponseDto.class
                            );
                            if (summaryResponse != null) {
                                assessmentScores = summaryResponse.getAssessments();
                                evaluation = summaryResponse.getEvaluation();
                            }
                        } catch (Exception e) {
                            System.err.println("Error fetching assessment summary for course " + subscription.getCourseId() + ": " + e.getMessage());
                            // Continue without assessment data if service is unavailable
                        }

                        if (course != null) {
                            return new SubscriptionCourseDto(subscription, course, instructorName, assessmentScores, evaluation);
                        } else {
                            System.err.println("Course with ID " + subscription.getCourseId() + " not found for subscription.");
                            return new SubscriptionCourseDto(subscription, null, instructorName, assessmentScores, evaluation);
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching course or instructor details for ID " + subscription.getCourseId() + ": " + e.getMessage());
                        return new SubscriptionCourseDto(subscription, null, null, null, null);
                    }
                })
                .collect(Collectors.toList());
    }

    public Optional<SubscriptionCourseDto> getSubscriptionById(long id) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(id);
        if (subscriptionOpt.isPresent()) {
            Subscription subscription = subscriptionOpt.get();
            CourseDto course = null;
            String instructorName = null;
            List<AssessmentScoreDto> assessmentScores = null;
            StudentEvaluationDto evaluation = null;
            try {
                course = restTemplate.getForObject("http://course-service/api/courses/" + subscription.getCourseId(), CourseDto.class);
                if (course != null && course.getInstructor() != null) {
                    UserDto instructor = restTemplate.getForObject("http://user-service/api/users/email?email=" + course.getInstructor(), UserDto.class);
                    if (instructor != null) {
                        instructorName = instructor.getName();
                    }
                }
                
                // Fetch assessment scores and evaluation
                try {
                    CourseSummaryRequestDto request = new CourseSummaryRequestDto(subscription.getCourseId());
                    CourseSummaryResponseDto summaryResponse = restTemplate.postForObject(
                        "http://assessment-service/api/assessments/course/summary-by-email?learnerEmail="+subscription.getLearnerEmail(),
                        request, 
                        CourseSummaryResponseDto.class
                    );
                    System.out.println("hiiiiiiiiiiiiiii");
                    System.out.println("http://assessment-service/api/assessments/course/summary-by-email?learnerEmail="+subscription.getLearnerEmail() );
                    if (summaryResponse != null) {
                        assessmentScores = summaryResponse.getAssessments();
                        evaluation = summaryResponse.getEvaluation();
                    }
                } catch (Exception e) {
                    System.err.println("Error fetching assessment summary for course " + subscription.getCourseId() + ": " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Error fetching course or instructor details for subscription ID " + id + ": " + e.getMessage());
            }
            return Optional.of(new SubscriptionCourseDto(subscription, course, instructorName, assessmentScores, evaluation));
        }
        return Optional.empty();
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