package com.elearning.course_service.repositories;

import com.elearning.course_service.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByCourseId(Long courseId);
} 