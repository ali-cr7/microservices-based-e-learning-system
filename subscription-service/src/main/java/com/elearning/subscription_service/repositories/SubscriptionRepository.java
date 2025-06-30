package com.elearning.subscription_service.repositories;

import com.elearning.subscription_service.entities.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByLearnerEmail(String learnerEmail);
    List<Subscription> findByLearnerEmailAndStatus(String learnerEmail, String status);
    Optional<Subscription> findByLearnerEmailAndCourseId(String learnerEmail, Long courseId);
}
