package com.elearning.assessment_service.services;

import com.elearning.assessment_service.dto.CourseDto;
import com.elearning.assessment_service.utils.JwtUtil;
import com.elearning.assessment_service.dto.AssessmentAttemptDto;
import com.elearning.assessment_service.dto.UserDto;
import com.elearning.assessment_service.entities.Assessment;
import com.elearning.assessment_service.entities.AssessmentAttempt;
import com.elearning.assessment_service.entities.AssessmentQuestion;
import com.elearning.assessment_service.entities.Question;
import com.elearning.assessment_service.repositories.AssessmentAttemptRepository;
import com.elearning.assessment_service.repositories.AssessmentRepository;
import com.elearning.assessment_service.repositories.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssessmentAttemptService {

    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentService assessmentService;
    private final JwtUtil jwtUtil;
    private final UserServiceClient userServiceClient;
    private final RestTemplate restTemplate;


    @Autowired
    public AssessmentAttemptService(AssessmentAttemptRepository assessmentAttemptRepository, AssessmentRepository assessmentRepository, QuestionRepository questionRepository, AssessmentService assessmentService, JwtUtil jwtUtil, UserServiceClient userServiceClient, RestTemplate restTemplate) {
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.assessmentService = assessmentService;
        this.jwtUtil = jwtUtil;
        this.userServiceClient = userServiceClient;
        this.restTemplate = restTemplate;
    }

    public AssessmentAttemptDto startAttempt(Long assessmentId, String learnerEmail) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));

        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setAssessment(assessment);
        attempt.setLearnerEmail(learnerEmail);
        attempt.setStartTime(LocalDateTime.now());
        attempt.setCompleted(false);
        attempt.setScore(0);
        attempt.setAnswers(null);

        attempt = assessmentAttemptRepository.save(attempt);
        return convertToDto(attempt);
    }

    public AssessmentAttemptDto submitAttempt(Long attemptId, Map<String, String> answers, String learnerEmail) {
        AssessmentAttempt attempt = assessmentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment Attempt not found"));

        if (!attempt.getLearnerEmail().equals(learnerEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to submit this attempt.");
        }

        if (attempt.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assessment attempt already completed.");
        }

        attempt.setEndTime(LocalDateTime.now());
        attempt.setCompleted(true);
        attempt.setAnswers(answers);

        // Calculate score
        int score = calculateScore(attempt.getAssessment(), answers);
        attempt.setScore(score);

        attempt = assessmentAttemptRepository.save(attempt);
        return convertToDto(attempt);
    }

    private int calculateScore(Assessment assessment, Map<String, String> answers) {
        int score = 0;
        List<AssessmentQuestion> assessmentQuestions = assessment.getAssessmentQuestions();
        if (assessmentQuestions != null) {
            for (AssessmentQuestion aq : assessmentQuestions) {
                Question question = aq.getQuestion();
                if (question != null) {
                    String questionId = String.valueOf(question.getId());
                    if (answers.containsKey(questionId) && answers.get(questionId).equals(question.getCorrectOption())) {
                        score++;
                    }
                }
            }
        }
        return score;
    }

    public List<AssessmentAttemptDto> getAttemptsByUser(String userEmail) {
        return assessmentAttemptRepository.findByLearnerEmail(userEmail).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<AssessmentAttemptDto> getAttemptsByStudentId(Long studentId) {
        UserDto user = userServiceClient.getUserById(studentId);
        if (user == null || user.getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found or email is missing for student ID: " + studentId);
        }
        return getAttemptsByUser(user.getEmail());
    }

    public AssessmentAttemptDto getAttemptById(Long attemptId, String userEmail, String token) {
        AssessmentAttempt attempt = assessmentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment Attempt not found"));

        String role = jwtUtil.extractRole(token.substring(7));

        if ("LEARNER".equals(role) && !attempt.getLearnerEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this attempt.");
        }

        return convertToDto(attempt);
    }

    public List<AssessmentAttemptDto> getAttemptsForAssessment(Long assessmentId) {
        return assessmentAttemptRepository.findByAssessmentId(assessmentId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<AssessmentAttemptDto> getAttemptsForInstructor(String instructorEmail) {
        return assessmentAttemptRepository.findByAssessmentInstructorEmail(instructorEmail).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public AssessmentAttemptDto evaluateAttempt(Long attemptId) {
        AssessmentAttempt attempt = assessmentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment Attempt not found"));

        if (!attempt.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot evaluate incomplete attempt.");
        }

        // Recalculate score to ensure accuracy
        int score = calculateScore(attempt.getAssessment(), attempt.getAnswers());
        attempt.setScore(score);
        
        attempt = assessmentAttemptRepository.save(attempt);
        return convertToDto(attempt);
    }

    private AssessmentAttemptDto convertToDto(AssessmentAttempt attempt) {
        AssessmentAttemptDto dto = new AssessmentAttemptDto();
        dto.setId(attempt.getId());
        dto.setAssessmentId(attempt.getAssessment().getId());
        dto.setAssessmentTitle(attempt.getAssessment().getTitle());
        dto.setLearnerEmail(attempt.getLearnerEmail());
        dto.setStartTime(attempt.getStartTime());
        dto.setEndTime(attempt.getEndTime());
        dto.setCompleted(attempt.isCompleted());
        dto.setScore(attempt.getScore());
        dto.setAnswers(attempt.getAnswers());

        // Set assessment details
        Assessment assessment = attempt.getAssessment();
        dto.setCourseName(assessment.getTitle());
        dto.setDurationMinutes(assessment.getDurationMinutes());
        dto.setPassMark(assessment.getPassMark());
        
        // Calculate if passed
        if (attempt.isCompleted() && assessment.getPassMark() != null) {
            double percentage = (double) attempt.getScore() / assessment.getAssessmentQuestions().size() * 100;
            dto.setPassed(percentage >= assessment.getPassMark());
        } else {
            dto.setPassed(false);
        }

        // Fetch and set student information from user service
        // Fetch course name from course-service
        String studentName = null;
        Long studentId = null;
        if (attempt.getLearnerEmail()!= null) {
            try {
                UserDto user = restTemplate.getForObject("http://user-service/api/users/email?email=" + attempt.getLearnerEmail(), UserDto.class);
                if (user != null) {
                    studentName = user.getName();
                    studentId = user.getId();
                }
            } catch (Exception e) {
                // Optionally log error
                studentName = null;
                studentId = null;
            }
        }
        dto.setStudentName(studentName);
        dto.setStudentId(studentId);

        return dto;
    }
}