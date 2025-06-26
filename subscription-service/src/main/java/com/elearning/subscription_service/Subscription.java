package com.elearning.subscription_service;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String learnerEmail; // Corresponds to the user's email from user-service
    private Long courseId;       // Corresponds to the course ID from course-service
    private LocalDateTime subscriptionDate;
    private String status;       // e.g., PENDING_PAYMENT, ACTIVE, COMPLETED, CANCELLED
    private Double priceAtSubscription; // Store the price at the time of subscription
}
