package com.elearning.subscription_service;

import com.elearning.subscription_service.dto.CourseDto;
import com.elearning.subscription_service.dto.SubscriptionCourseDto;
import com.elearning.subscription_service.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final JwtUtil jwtUtil;

    @Autowired
    public SubscriptionController(SubscriptionService subscriptionService, JwtUtil jwtUtil) {
        this.subscriptionService = subscriptionService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/enroll")
    public ResponseEntity<?> enrollInCourse(@RequestHeader("Authorization") String authHeader, @RequestBody EnrollmentRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String learnerEmail = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        if (!"LEARNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners can enroll in courses.");
        }

        try {
            Subscription subscription = subscriptionService.enrollInCourse(learnerEmail, request.getCourseId());
            return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/payment-callback") // Simplified for mocking
    public ResponseEntity<?> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        // In a real scenario, validate callback from payment gateway (e.g., webhook signature)
        // For now, assume a simple success/failure based on a mock status
        String status = request.isSuccess() ? "ACTIVE" : "FAILED";

        Optional<Subscription> updatedSubscription = subscriptionService.updateSubscriptionStatus(request.getSubscriptionId(), status);

        return updatedSubscription
                .map(sub -> ResponseEntity.ok("Payment processed successfully, status: " + sub.getStatus()))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscription not found."));
    }

    @GetMapping("/my-courses")
    public ResponseEntity<?> getMyEnrolledCourses(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String learnerEmail = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        if (!"LEARNER".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can view enrolled courses.");
        }

        List<SubscriptionCourseDto> enrolledCourses = subscriptionService.getMyEnrolledCourses(learnerEmail);
        return ResponseEntity.ok(enrolledCourses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubscriptionById(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String email = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        Optional<SubscriptionCourseDto> subscriptionCourseDtoOpt = subscriptionService.getSubscriptionById(id);

        if (subscriptionCourseDtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscription not found.");
        }

        SubscriptionCourseDto subscriptionCourseDto = subscriptionCourseDtoOpt.get();
        Subscription subscription = subscriptionCourseDto.getSubscription();

        // Only allow the learner who owns the subscription or an admin to view it
        if ("LEARNER".equals(role) && !subscription.getLearnerEmail().equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to view this subscription.");
        } else if (!"ADMIN".equals(role) && !"LEARNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can view subscriptions.");
        }

        return ResponseEntity.ok(subscriptionCourseDto);
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<?> cancelSubscription(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String email = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        Optional<SubscriptionCourseDto> subscriptionOpt = subscriptionService.getSubscriptionById(id);

        if (subscriptionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscription not found.");
        }

        Subscription subscription = subscriptionOpt.get().getSubscription();

        // Only allow the learner who owns the subscription or an admin to cancel it
        if ("LEARNER".equals(role) && !subscription.getLearnerEmail().equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to cancel this subscription.");
        } else if (!"ADMIN".equals(role) && !"LEARNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can cancel subscriptions.");
        }

        if (subscriptionService.cancelSubscription(id)) {
            return ResponseEntity.ok().body("Subscription with ID " + id + " cancelled successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Could not cancel subscription (e.g., already cancelled or invalid status).");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllSubscriptions(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String role = jwtUtil.extractRole(token);

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only admins can view all subscriptions.");
        }

        List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
        return ResponseEntity.ok(subscriptions);
    }

    // DTO for enrollment request
    static class EnrollmentRequest {
        private Long courseId;
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
    }

    // DTO for mock payment callback
    static class PaymentCallbackRequest {
        private Long subscriptionId;
        private boolean success;
        public Long getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
} 