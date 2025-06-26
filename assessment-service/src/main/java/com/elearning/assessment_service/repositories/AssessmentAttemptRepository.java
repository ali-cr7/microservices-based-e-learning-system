package com.elearning.assessment_service.repositories;

import com.elearning.assessment_service.entities.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {
    List<AssessmentAttempt> findByLearnerEmail(String learnerEmail);
    List<AssessmentAttempt> findByAssessmentId(Long assessmentId);
    List<AssessmentAttempt> findByAssessmentIdIn(List<Long> assessmentIds);
    List<AssessmentAttempt> findByAssessmentInstructorEmail(String instructorEmail);
} 