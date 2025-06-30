package com.elearning.course_service.repositories;

import com.elearning.course_service.entities.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    // Add custom queries if needed
    List<Course> findByApproved(boolean approved);

    // Finds courses by instructor email and approval status
    List<Course> findByInstructorAndApproved(String instructor, boolean approved);
}