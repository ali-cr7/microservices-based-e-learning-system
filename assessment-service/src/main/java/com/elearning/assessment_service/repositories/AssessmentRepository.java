package com.elearning.assessment_service.repositories;

import com.elearning.assessment_service.entities.Assessment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    @EntityGraph(attributePaths = "assessmentQuestions")
    Optional<Assessment> findById(Long id);

    @EntityGraph(attributePaths = "assessmentQuestions")
    List<Assessment> findByCourseId(Long courseId);

    @EntityGraph(attributePaths = "assessmentQuestions")
    List<Assessment> findByInstructorEmail(String instructorEmail);
} 