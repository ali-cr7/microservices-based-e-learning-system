package com.elearning.course_service.services;

import com.elearning.course_service.Course;
import com.elearning.course_service.CourseRepository;
import com.elearning.course_service.dto.SessionDto;
import com.elearning.course_service.entities.Session;
import com.elearning.course_service.repositories.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private CourseRepository courseRepository;

    // Modified to accept String pdfFileUrl
    public SessionDto createSession(Long courseId, String title, String pdfFileUrl) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            throw new RuntimeException("Course not found");
        }

        Course course = courseOptional.get();
        Session session = new Session();
        session.setTitle(title);
        session.setPdfFileUrl(pdfFileUrl); // Set the URL
        session.setCourse(course);

        Session savedSession = sessionRepository.save(session);
        return convertToDto(savedSession);
    }

    public List<SessionDto> getSessionsByCourseId(Long courseId) {
        List<Session> sessions = sessionRepository.findByCourseId(courseId);
        return sessions.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Optional<SessionDto> getSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .map(this::convertToDto);
    }

    public void deleteSession(Long sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    private SessionDto convertToDto(Session session) {
        SessionDto dto = new SessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setPdfFileUrl(session.getPdfFileUrl()); // Get the URL
        dto.setCourseId(session.getCourse().getId());
        return dto;
    }
}