package com.elearning.assessment_service.controllers;

import com.elearning.assessment_service.dto.StudentEvaluationDto;
import com.elearning.assessment_service.services.StudentEvaluationService;
import com.elearning.assessment_service.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/assessments/evaluations")
public class StudentEvaluationController {
    @Autowired
    private StudentEvaluationService evaluationService;
    @Autowired
    private JwtUtil jwtUtil;

    private String validateTokenAndExtractRole(String token, String... requiredRoles) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String jwt = token.substring(7);
        try {
            String role = jwtUtil.extractRole(jwt);
            if (role == null || (requiredRoles.length > 0 && java.util.Arrays.stream(requiredRoles).noneMatch(r -> r.equals(role) || "ADMIN".equals(role)))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role privileges.");
            }
            return jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token: " + e.getMessage());
        }
    }

    @PostMapping("/student/add-evaluation")
    public ResponseEntity<StudentEvaluationDto> addOrUpdateEvaluation(
            @RequestHeader("Authorization") String token,
            @RequestBody AddEvaluationRequest request) {
        String instructorEmail = validateTokenAndExtractRole(token, "INSTRUCTOR");
        StudentEvaluationDto dto = evaluationService.addOrUpdateEvaluation(
                request.studentId, instructorEmail, request.evaluationText, request.courseId);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentEvaluationDto>> getEvaluationsForStudent(@RequestHeader("Authorization") String token, @PathVariable Long studentId) {
        validateTokenAndExtractRole(token, "INSTRUCTOR", "ADMIN");
        List<StudentEvaluationDto> dtos = evaluationService.getEvaluationsForStudent(studentId);
        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }

    @GetMapping("/instructor")
    public ResponseEntity<List<StudentEvaluationDto>> getEvaluationsByInstructor(@RequestHeader("Authorization") String token) {
        String instructorEmail = validateTokenAndExtractRole(token, "INSTRUCTOR");
        List<StudentEvaluationDto> dtos = evaluationService.getEvaluationsByInstructor(instructorEmail);
        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }
    public static class AddEvaluationRequest {
        public Long studentId;
        public String evaluationText;
        public Long courseId;
    }
} 