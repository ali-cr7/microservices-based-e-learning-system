package com.elearning.assessment_service.repositories;

import com.elearning.assessment_service.entities.StudentEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentEvaluationRepository extends JpaRepository<StudentEvaluation, Long> {
    List<StudentEvaluation> findByStudentId(Long studentId);
    List<StudentEvaluation> findByInstructorEmail(String instructorEmail);
    Optional<StudentEvaluation> findByStudentIdAndInstructorEmail(Long studentId, String instructorEmail);
    Optional<StudentEvaluation> findByStudentIdAndCourseId(Long studentId, Long courseId);
} 