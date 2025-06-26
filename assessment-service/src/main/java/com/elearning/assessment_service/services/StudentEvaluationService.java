package com.elearning.assessment_service.services;

import com.elearning.assessment_service.dto.StudentEvaluationDto;
import com.elearning.assessment_service.entities.StudentEvaluation;
import com.elearning.assessment_service.repositories.StudentEvaluationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StudentEvaluationService {
    @Autowired
    private StudentEvaluationRepository repository;

    public StudentEvaluationDto addOrUpdateEvaluation(Long studentId, String instructorEmail, String evaluationText, Long courseId) {
        Optional<StudentEvaluation> existing = repository.findByStudentIdAndInstructorEmail(studentId, instructorEmail);
        StudentEvaluation eval;
        if (existing.isPresent()) {
            eval = existing.get();
            eval.setEvaluationText(evaluationText);
            eval.setUpdatedAt(LocalDateTime.now());
            eval.setCourseId(courseId);
        } else {
            eval = new StudentEvaluation();
            eval.setStudentId(studentId);
            eval.setInstructorEmail(instructorEmail);
            eval.setEvaluationText(evaluationText);
            eval.setCourseId(courseId);
            eval.setCreatedAt(LocalDateTime.now());
            eval.setUpdatedAt(LocalDateTime.now());
        }
        eval = repository.save(eval);
        return toDto(eval);
    }

    public List<StudentEvaluationDto> getEvaluationsForStudent(Long studentId) {
        return repository.findByStudentId(studentId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<StudentEvaluationDto> getEvaluationsByInstructor(String instructorEmail) {
        return repository.findByInstructorEmail(instructorEmail).stream().map(this::toDto).collect(Collectors.toList());
    }

    public Optional<StudentEvaluationDto> getEvaluation(Long studentId, String instructorEmail) {
        return repository.findByStudentIdAndInstructorEmail(studentId, instructorEmail).map(this::toDto);
    }

    public StudentEvaluationDto getEvaluationForStudentAndCourse(Long studentId, Long courseId) {
        Optional<StudentEvaluation> eval = repository.findByStudentIdAndCourseId(studentId, courseId);
        return eval.map(this::toDto).orElse(null);
    }

    private StudentEvaluationDto toDto(StudentEvaluation eval) {
        return new StudentEvaluationDto(
            eval.getId(),
            eval.getStudentId(),
            eval.getInstructorEmail(),
            eval.getEvaluationText(),
            eval.getCourseId(),
            eval.getCreatedAt(),
            eval.getUpdatedAt()
        );
    }
} 