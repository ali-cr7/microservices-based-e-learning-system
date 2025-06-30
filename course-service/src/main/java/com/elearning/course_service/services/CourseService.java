package com.elearning.course_service.services;

import com.elearning.course_service.entities.Course;
import com.elearning.course_service.repositories.CourseRepository;
import com.elearning.course_service.dto.CourseResponseDto;
import com.elearning.course_service.dto.SessionDto;
import com.elearning.course_service.entities.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CourseService {
    @Autowired
    private CourseRepository courseRepository;

    @Transactional
    public Course addCourse(Course course) {
        // Always set approved to false on initial creation by instructor
        course.setApproved(false);
        return courseRepository.save(course);
    }

    @Transactional
    public Optional<CourseResponseDto> approveCourse(Long courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isPresent()) {
            Course course = courseOpt.get();
            course.setApproved(true);
            return Optional.of(convertToDto(courseRepository.save(course)));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDto> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDto> getCourseRequests() {
        return courseRepository.findByApproved(false).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDto> getAvailableCourses() {
        return courseRepository.findByApproved(true).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDto> getCoursesByInstructorAndApprovedStatus(String instructor, boolean approved) {
        return courseRepository.findByInstructorAndApproved(instructor, approved).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourseResponseDto getCourseById(Long id) {
        return courseRepository.findById(id).map(this::convertToDto).orElse(null);
    }

    @Transactional
    public boolean deleteCourse(Long id) {
        if (courseRepository.existsById(id)) {
            courseRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private CourseResponseDto convertToDto(Course course) {
        CourseResponseDto dto = new CourseResponseDto();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setPrice(course.getPrice());
        dto.setInstructor(course.getInstructor());
        dto.setInstructorName(course.getInstructorName());
        dto.setApproved(course.isApproved());

        // Initialize sessions collection before mapping to DTO
        List<SessionDto> sessionDtos = Optional.ofNullable(course.getSessions())
                .orElse(Collections.emptyList())
                .stream()
                .map(this::convertSessionToDto)
                .collect(Collectors.toList());
        dto.setSessions(sessionDtos);

        return dto;
    }

    private SessionDto convertSessionToDto(Session session) {
        SessionDto dto = new SessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
       // dto.setPdfFile(session.getPdfFile());
       dto.setPdfFileUrl(session.getPdfFileUrl());
        dto.setCourseId(session.getCourse().getId());
        return dto;
    }
}
