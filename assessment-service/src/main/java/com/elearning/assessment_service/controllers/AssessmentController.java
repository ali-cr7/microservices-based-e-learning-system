package com.elearning.assessment_service.controllers;

import com.elearning.assessment_service.services.AssessmentAttemptService;
import com.elearning.assessment_service.services.AssessmentService;
import com.elearning.assessment_service.utils.JwtUtil;
import com.elearning.assessment_service.dto.CreateAssessmentRequest;
import com.elearning.assessment_service.dto.StartAttemptRequest;
import com.elearning.assessment_service.dto.SubmitAttemptRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.elearning.assessment_service.dto.AssessmentDto;
import com.elearning.assessment_service.dto.AssessmentAttemptDto;
import org.springframework.transaction.annotation.Transactional;
import com.elearning.assessment_service.dto.StudentEvaluationDto;
import com.elearning.assessment_service.services.StudentEvaluationService;

import java.util.Arrays;
import java.util.List;

// DTO for the request
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {
    private final AssessmentService assessmentService;
    private final JwtUtil jwtUtil;
    private final AssessmentAttemptService assessmentAttemptService;
    private final StudentEvaluationService studentEvaluationService;

    @Autowired
    public AssessmentController(AssessmentService assessmentService, JwtUtil jwtUtil, AssessmentAttemptService assessmentAttemptService, StudentEvaluationService studentEvaluationService) {
        this.assessmentService = assessmentService;
        this.jwtUtil = jwtUtil;
        this.assessmentAttemptService = assessmentAttemptService;
        this.studentEvaluationService = studentEvaluationService;
    }

    // DTO for the request
    public static class CourseSummaryRequest {
        public Long courseId;
    }

    // DTO for the response
    public static class CourseSummaryResponse {
        public List<AssessmentScore> assessments;
        public com.elearning.assessment_service.dto.StudentEvaluationDto evaluation;
        public CourseSummaryResponse(List<AssessmentScore> assessments, com.elearning.assessment_service.dto.StudentEvaluationDto evaluation) {
            this.assessments = assessments;
            this.evaluation = evaluation;
        }
        public static class AssessmentScore {
            public Long assessmentId;
            public String assessmentTitle;
            public Integer score;

            public AssessmentScore(Long assessmentId, String assessmentTitle, Integer score) {
                this.assessmentId = assessmentId;
                this.assessmentTitle = assessmentTitle;
                this.score = score;
            }
        }
    }

    private String validateTokenAndExtractRole(String token, String... requiredRoles) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String jwt = token.substring(7);
        try {
            String role = jwtUtil.extractRole(jwt);
            if (role == null || (requiredRoles.length > 0 && Arrays.stream(requiredRoles).noneMatch(r -> r.equals(role) || "ADMIN".equals(role)))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role privileges.");
            }
            return jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<AssessmentDto> createAssessment(@RequestHeader("Authorization") String token, @RequestBody CreateAssessmentRequest request) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        AssessmentDto assessment = assessmentService.createAssessment(request, jwtUtil.extractUsername(token.substring(7)));
        return new ResponseEntity<>(assessment, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<AssessmentDto> getAssessmentById(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable Long id) {
        validateTokenAndExtractRole(token, "INSTRUCTOR", "ADMIN", "LEARNER");
        return assessmentService.getAssessmentById(id)
                .map(assessment -> new ResponseEntity<>(assessment, HttpStatus.OK))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));
    }

    @GetMapping("/course/{courseId}")
    @Transactional
    public ResponseEntity<List<AssessmentDto>> getAssessmentsByCourseId(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable Long courseId) {
        validateTokenAndExtractRole(token, "INSTRUCTOR", "ADMIN", "LEARNER");
        List<AssessmentDto> assessments = assessmentService.getAssessmentsByCourseId(courseId);
        return new ResponseEntity<>(assessments, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssessmentDto> updateAssessment(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestBody CreateAssessmentRequest request) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        AssessmentDto updatedAssessment = assessmentService.updateAssessment(id, request);
        if (updatedAssessment != null) {
            return new ResponseEntity<>(updatedAssessment, HttpStatus.OK);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssessment(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        validateTokenAndExtractRole(token, "ADMIN");
        if (assessmentService.deleteAssessment(id)) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found");
        }
    }

    @PostMapping("/attempts/start")
    @Transactional
    public ResponseEntity<AssessmentAttemptDto> startAssessmentAttempt(@RequestHeader("Authorization") String token, @RequestBody StartAttemptRequest request) {
        String learnerEmail = validateTokenAndExtractRole(token, "LEARNER");
        AssessmentAttemptDto attempt = assessmentAttemptService.startAttempt(request.getAssessmentId(), learnerEmail);
        return new ResponseEntity<>(attempt, HttpStatus.CREATED);
    }

    @PostMapping("/attempts/submit")
    @Transactional
    public ResponseEntity<AssessmentAttemptDto> submitAssessmentAttempt(@RequestHeader("Authorization") String token, @RequestBody SubmitAttemptRequest request) {
        String learnerEmail = validateTokenAndExtractRole(token, "LEARNER");
        AssessmentAttemptDto attempt = assessmentAttemptService.submitAttempt(request.getAttemptId(), request.getAnswers(), learnerEmail);
        return new ResponseEntity<>(attempt, HttpStatus.OK);
    }

    @GetMapping("/my-attempts")
    @Transactional
    public ResponseEntity<List<AssessmentAttemptDto>> getMyAssessmentAttempts(@RequestHeader("Authorization") String token) {
        String userEmail = validateTokenAndExtractRole(token, "LEARNER", "ADMIN");
        List<AssessmentAttemptDto> attempts = assessmentAttemptService.getAttemptsByUser(userEmail);
        return new ResponseEntity<>(attempts, HttpStatus.OK);
    }

    @GetMapping("/attempts/{attemptId}")
    @Transactional
    public ResponseEntity<AssessmentAttemptDto> getAssessmentAttemptById(@RequestHeader("Authorization") String token, @PathVariable Long attemptId) {
        String userEmail = validateTokenAndExtractRole(token, "LEARNER", "INSTRUCTOR", "ADMIN");
        AssessmentAttemptDto attempt = assessmentAttemptService.getAttemptById(attemptId, userEmail, token);
        return new ResponseEntity<>(attempt, HttpStatus.OK);
    }

    @GetMapping("/my-courses/assessments")
    @Transactional
    public ResponseEntity<List<AssessmentDto>> getAssessmentsForMyCourses(@RequestHeader("Authorization") String token) {
        validateTokenAndExtractRole(token, "LEARNER", "ADMIN");
        List<AssessmentDto> assessments = assessmentService.getAssessmentsForSubscribedCourses(token);
        return new ResponseEntity<>(assessments, HttpStatus.OK);
    }

    @GetMapping("/all-with-course-info")
    @Transactional
    public ResponseEntity<List<AssessmentDto>> getAllAssessmentsWithCourseInfo(@RequestHeader("Authorization") String token) {
        validateTokenAndExtractRole(token, "ADMIN");
        List<AssessmentDto> assessments = assessmentService.getAllAssessmentsWithCourseInfo();
        return new ResponseEntity<>(assessments, HttpStatus.OK);
    }

    @GetMapping("/my-instructor-assessments")
    @Transactional
    public ResponseEntity<List<AssessmentDto>> getMyInstructorAssessments(@RequestHeader("Authorization") String token) {
        String instructorEmail = validateTokenAndExtractRole(token, "INSTRUCTOR");
        List<AssessmentDto> assessments = assessmentService.getAssessmentsByInstructor(instructorEmail);
        return new ResponseEntity<>(assessments, HttpStatus.OK);
    }

    @GetMapping("/instructor/attempts")
    @Transactional
    public ResponseEntity<List<AssessmentAttemptDto>> getAllStudentAttemptsForInstructor(@RequestHeader("Authorization") String token) {
        String instructorEmail = validateTokenAndExtractRole(token, "INSTRUCTOR");
        List<AssessmentAttemptDto> attempts = assessmentAttemptService.getAttemptsForInstructor(instructorEmail);
        return new ResponseEntity<>(attempts, HttpStatus.OK);
    }

    @GetMapping("/{assessmentId}/attempts")
    @Transactional
    public ResponseEntity<List<AssessmentAttemptDto>> getAttemptsForAssessment(@RequestHeader("Authorization") String token, @PathVariable Long assessmentId) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        List<AssessmentAttemptDto> attempts = assessmentAttemptService.getAttemptsForAssessment(assessmentId);
        return new ResponseEntity<>(attempts, HttpStatus.OK);
    }

    @PutMapping("/attempts/{attemptId}/evaluate")
    @Transactional
    public ResponseEntity<AssessmentAttemptDto> evaluateStudentAttempt(@RequestHeader("Authorization") String token, @PathVariable Long attemptId) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        AssessmentAttemptDto updated = assessmentAttemptService.evaluateAttempt(attemptId);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @PostMapping("/course/summary")
    @Transactional
    public ResponseEntity<CourseSummaryResponse> getCourseSummaryForLearner(
            @RequestHeader("Authorization") String token,
            @RequestBody CourseSummaryRequest request) {
        String learnerEmail = validateTokenAndExtractRole(token, "LEARNER");
        // Get all assessments for the course
        List<AssessmentDto> assessments = assessmentService.getAssessmentsByCourseId(request.courseId);
        List<Long> assessmentIds = assessments.stream().map(AssessmentDto::getId).toList();
        // Get all attempts by this learner for these assessments
        List<AssessmentAttemptDto> attempts = assessmentAttemptService.getAttemptsByUser(learnerEmail);
        // Filter attempts to only those for this course
        List<CourseSummaryResponse.AssessmentScore> scores = attempts.stream()
            .filter(a -> assessmentIds.contains(a.getAssessmentId()))
            .map(a -> new CourseSummaryResponse.AssessmentScore(
                a.getAssessmentId(),
                a.getAssessmentTitle(),
                a.getScore()
            ))
            .toList();
        
        // Get studentId from the first attempt for evaluation
        Long studentId = attempts.stream()
            .filter(a -> assessmentIds.contains(a.getAssessmentId()))
            .findFirst()
            .map(a -> a.getStudentId())
            .orElse(null);
            
        StudentEvaluationDto evaluation = null;
        if (studentId != null) {
            evaluation = studentEvaluationService.getEvaluationForStudentAndCourse(studentId, request.courseId);
        }
        CourseSummaryResponse resp = new CourseSummaryResponse(scores, evaluation);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }

    @PostMapping("/course/summary-by-email")
    @Transactional
    public ResponseEntity<CourseSummaryResponse> getCourseSummaryForLearnerByEmail(
            @RequestParam("learnerEmail") String learnerEmail,
            @RequestBody CourseSummaryRequest request) {
        // This endpoint is for internal service-to-service communication, so no token validation.
        
        // Get all assessments for the course
        List<AssessmentDto> assessments = assessmentService.getAssessmentsByCourseId(request.courseId);
        List<Long> assessmentIds = assessments.stream().map(AssessmentDto::getId).toList();
        // Get all attempts by this learner for these assessments
        List<AssessmentAttemptDto> attempts = assessmentAttemptService.getAttemptsByUser(learnerEmail);
        // Filter attempts to only those for this course
        List<CourseSummaryResponse.AssessmentScore> scores = attempts.stream()
                .filter(a -> assessmentIds.contains(a.getAssessmentId()))
                .map(a -> new CourseSummaryResponse.AssessmentScore(
                        a.getAssessmentId(),
                        a.getAssessmentTitle(),
                        a.getScore()
                ))
                .toList();

        // Get studentId from the first attempt for evaluation
        Long studentId = attempts.stream()
                .filter(a -> assessmentIds.contains(a.getAssessmentId()))
                .findFirst()
                .map(a -> a.getStudentId())
                .orElse(null);

        StudentEvaluationDto evaluation = null;
        if (studentId != null) {
            evaluation = studentEvaluationService.getEvaluationForStudentAndCourse(studentId, request.courseId);
        }
        CourseSummaryResponse resp = new CourseSummaryResponse(scores, evaluation);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }


//    @PutMapping("/attempts/{attemptId}/oral-evaluation")
//    @Transactional
//    public ResponseEntity<AssessmentAttemptDto> setOralEvaluation(@RequestHeader("Authorization") String token, @PathVariable Long attemptId, @RequestParam String oralEvaluation) {
//        validateTokenAndExtractRole(token, "INSTRUCTOR");
//        AssessmentAttemptDto updated = assessmentAttemptService.evaluateOral(attemptId, oralEvaluation);
//        return new ResponseEntity<>(updated, HttpStatus.OK);
//    }
} 