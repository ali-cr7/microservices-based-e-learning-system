package com.elearning.subscription_service.controllers;

import com.elearning.subscription_service.entities.Subscription;
import com.elearning.subscription_service.services.SubscriptionService;
import com.elearning.subscription_service.dto.SubscriptionCourseDto;
import com.elearning.subscription_service.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<ResponseEntity<?>> enrollInCourse(@RequestHeader("Authorization") String authHeader, @RequestBody EnrollmentRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token"));
        }
        String learnerEmail = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        if (!"LEARNER".equals(role)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners can enroll in courses."));
        }

        return subscriptionService.enrollInCourse(learnerEmail, request.getCourseId())
                .handle((subscription, ex) -> {
                    if (ex != null) {
                        if (ex.getCause() instanceof ResponseStatusException rse) {
                            return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
                        }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + ex.getMessage());
                    }
                    return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
                });
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
    public CompletableFuture<ResponseEntity<?>> getMyEnrolledCourses(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token"));
        }
        String learnerEmail = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        if (!"LEARNER".equals(role) && !"ADMIN".equals(role)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can view enrolled courses."));
        }

        return subscriptionService.getMyEnrolledCourses(learnerEmail)
                .handle((courses, ex) -> {
                    if (ex != null) {
                        // Log the actual exception for debugging
                        System.err.println("Error in getMyEnrolledCourses: " + ex.getMessage());
                        ex.printStackTrace();
                        
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("An error occurred while fetching courses. Please try again later.");
                    }
                    return ResponseEntity.ok(courses);
                });
    }


    @GetMapping("/my-course/{id}")
    public CompletableFuture<ResponseEntity<?>> getMyEnrolledCourseById(@RequestHeader("Authorization") String authHeader,@PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token"));
        }
        String learnerEmail = jwtUtil.extractAllClaims(token).getSubject();
        String role = jwtUtil.extractRole(token);

        if (!"LEARNER".equals(role) && !"ADMIN".equals(role)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can view enrolled courses."));
        }

        return subscriptionService.getMyEnrolledCourseById(learnerEmail,id)
                .handle((courses, ex) -> {
                    if (ex != null) {
                        // Log the actual exception for debugging
                        System.err.println("Error in getMyEnrolledCourses: " + ex.getMessage());
                        ex.printStackTrace();

                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("An error occurred while fetching courses. Please try again later.");
                    }
                    return ResponseEntity.ok(courses);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getSubscriptionById(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token"));
        }
        
        return subscriptionService.getSubscriptionById(id).handle((subscriptionCourseDtoOpt, ex) -> {
            if (ex != null) {
                // Log the actual exception for debugging
                System.err.println("Error in getSubscriptionById: " + ex.getMessage());
                ex.printStackTrace();
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while fetching subscription. Please try again later.");
            }

            if (subscriptionCourseDtoOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscription not found.");
            }
            
            String email = jwtUtil.extractAllClaims(token).getSubject();
            String role = jwtUtil.extractRole(token);

            SubscriptionCourseDto subscriptionCourseDto = subscriptionCourseDtoOpt.get();
            Subscription subscription = subscriptionCourseDto.getSubscription();

            // Only allow the learner who owns the subscription or an admin to view it
            if ("LEARNER".equals(role) && !subscription.getLearnerEmail().equals(email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to view this subscription.");
            } else if (!"ADMIN".equals(role) && !"LEARNER".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only learners or admins can view subscriptions.");
            }

            return ResponseEntity.ok(subscriptionCourseDto);
        });
    }

    @DeleteMapping("/{id}/cancel")
    public CompletableFuture<ResponseEntity<?>> cancelSubscription(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token"));
        }
       
        return subscriptionService.getSubscriptionById(id).handle((subscriptionOpt, ex) -> {
            if (ex != null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while fetching subscription: " + ex.getMessage());
            }

            if (subscriptionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscription not found.");
            }

            String email = jwtUtil.extractAllClaims(token).getSubject();
            String role = jwtUtil.extractRole(token);
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
        });
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