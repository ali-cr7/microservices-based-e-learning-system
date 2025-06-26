package com.elearning.assessment_service.services;

import com.elearning.assessment_service.dto.CourseDto;
import com.elearning.assessment_service.dto.CreateAssessmentRequest;
import com.elearning.assessment_service.dto.UserDto;
import com.elearning.assessment_service.dto.AssessmentDto;
import com.elearning.assessment_service.dto.AssessmentQuestionDto;
import com.elearning.assessment_service.dto.QuestionDto;
import com.elearning.assessment_service.entities.Assessment;
import com.elearning.assessment_service.entities.AssessmentQuestion;
import com.elearning.assessment_service.entities.Question;
import com.elearning.assessment_service.repositories.AssessmentQuestionRepository;
import com.elearning.assessment_service.repositories.AssessmentRepository;
import com.elearning.assessment_service.repositories.QuestionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AssessmentService {
    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public AssessmentService(AssessmentRepository assessmentRepository, QuestionRepository questionRepository, AssessmentQuestionRepository assessmentQuestionRepository, RestTemplate restTemplate) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.assessmentQuestionRepository = assessmentQuestionRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public AssessmentDto createAssessment(CreateAssessmentRequest request, String instructorEmail) {
        // 1. Verify instructor existence and role with User-Service
        UserDto instructor;
        try {
            instructor = restTemplate.getForObject("http://user-service/api/users/email?email=" + instructorEmail, UserDto.class);
            if (instructor == null || (!"INSTRUCTOR".equals(instructor.getRole()) && !"ADMIN".equals(instructor.getRole()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only instructors or admins can create assessments.");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error communicating with user service: " + e.getMessage());
        }

        // 2. Verify course existence with Course-Service
        CourseDto course;
        try {
            course = restTemplate.getForObject("http://course-service/api/courses/" + request.getCourseId(), CourseDto.class);
            if (course == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error communicating with course service: " + e.getMessage());
        }

        // 3. Create the Assessment entity
        Assessment assessment = new Assessment();
        assessment.setTitle(request.getTitle());
        assessment.setCourseId(request.getCourseId());
        assessment.setDescription(request.getDescription());
        assessment.setDurationMinutes(request.getDurationMinutes());
        assessment.setPassMark(request.getPassMark());
        assessment.setMaxAttempts(request.getMaxAttempts());
        assessment.setCreationDate(LocalDateTime.now());
        assessment.setInstructorEmail(instructorEmail);

        // 4. Save the assessment to get an ID
        Assessment savedAssessment = assessmentRepository.save(assessment);

        // 5. Add questions to the assessment
        List<AssessmentQuestion> assessmentQuestions = new ArrayList<>();
        for (CreateAssessmentRequest.QuestionOrderDto qDto : request.getQuestions()) {
            Optional<Question> questionOpt = questionRepository.findById(qDto.getQuestionId());
            if (questionOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question with ID " + qDto.getQuestionId() + " not found.");
            }
            AssessmentQuestion aq = new AssessmentQuestion();
            aq.setAssessment(savedAssessment);
            aq.setQuestion(questionOpt.get());
            aq.setQuestionOrder(qDto.getOrder());
            assessmentQuestions.add(aq);
        }
        assessmentQuestionRepository.saveAll(assessmentQuestions);

        savedAssessment.setAssessmentQuestions(assessmentQuestions);

        return convertToDto(savedAssessment);
    }

    public Optional<AssessmentDto> getAssessmentById(Long id) {
        return assessmentRepository.findById(id).map(this::convertToDto);
    }

    public List<AssessmentDto> getAssessmentsByCourseId(Long courseId) {
        return assessmentRepository.findByCourseId(courseId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AssessmentDto updateAssessment(Long id, CreateAssessmentRequest updatedRequest) {
        return assessmentRepository.findById(id)
                .map(existingAssessment -> {
                    // Update basic assessment details
                    existingAssessment.setTitle(updatedRequest.getTitle());
                    existingAssessment.setDescription(updatedRequest.getDescription());
                    existingAssessment.setDurationMinutes(updatedRequest.getDurationMinutes());
                    existingAssessment.setPassMark(updatedRequest.getPassMark());
                    existingAssessment.setMaxAttempts(updatedRequest.getMaxAttempts());

                    // Update questions (clear existing and add new)
                    assessmentQuestionRepository.deleteAll(existingAssessment.getAssessmentQuestions());
                    existingAssessment.getAssessmentQuestions().clear();

                    List<AssessmentQuestion> newAssessmentQuestions = new ArrayList<>();
                    for (CreateAssessmentRequest.QuestionOrderDto qDto : updatedRequest.getQuestions()) {
                        Optional<Question> questionOpt = questionRepository.findById(qDto.getQuestionId());
                        if (questionOpt.isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question with ID " + qDto.getQuestionId() + " not found.");
                        }
                        AssessmentQuestion aq = new AssessmentQuestion();
                        aq.setAssessment(existingAssessment);
                        aq.setQuestion(questionOpt.get());
                        aq.setQuestionOrder(qDto.getOrder());
                        newAssessmentQuestions.add(aq);
                    }
                    assessmentQuestionRepository.saveAll(newAssessmentQuestions);
                    existingAssessment.setAssessmentQuestions(newAssessmentQuestions);

                    return convertToDto(assessmentRepository.save(existingAssessment));
                }).orElse(null);
    }

    @Transactional
    public boolean deleteAssessment(Long id) {
        if (assessmentRepository.existsById(id)) {
            // Deleting an assessment should also delete its associated AssessmentQuestions
            // This is handled by orphanRemoval = true in Assessment entity's @OneToMany
            assessmentRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public AssessmentDto convertToDto(Assessment assessment) {
        AssessmentDto dto = new AssessmentDto();
        dto.setId(assessment.getId());
        dto.setTitle(assessment.getTitle());
        dto.setCourseId(assessment.getCourseId());
        dto.setDescription(assessment.getDescription());
        dto.setDurationMinutes(assessment.getDurationMinutes());
        dto.setPassMark(assessment.getPassMark());
        dto.setMaxAttempts(assessment.getMaxAttempts());
        dto.setCreationDate(assessment.getCreationDate());
        dto.setInstructorEmail(assessment.getInstructorEmail());

        // Fetch course name from course-service
        String courseName = null;
        if (assessment.getCourseId() != null) {
            try {
                CourseDto course = restTemplate.getForObject("http://course-service/api/courses/" + assessment.getCourseId(), CourseDto.class);
                if (course != null) {
                    courseName = course.getTitle();
                }
            } catch (Exception e) {
                // Optionally log error
                courseName = null;
            }
        }
        dto.setCourseName(courseName);

        if (assessment.getAssessmentQuestions() != null) {
            // Force initialization of questions to avoid LazyInitializationException
            assessment.getAssessmentQuestions().forEach(aq -> {
                if (aq.getQuestion() != null) {
                    aq.getQuestion().getText();
                    aq.getQuestion().getOptions();
                    aq.getQuestion().getCorrectOption();
                }
            });
            dto.setAssessmentQuestions(assessment.getAssessmentQuestions().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private AssessmentQuestionDto convertToDto(AssessmentQuestion assessmentQuestion) {
        AssessmentQuestionDto dto = new AssessmentQuestionDto();
        dto.setId(assessmentQuestion.getId());
        dto.setQuestionOrder(assessmentQuestion.getQuestionOrder());
        if (assessmentQuestion.getQuestion() != null) {
            dto.setQuestion(convertToDto(assessmentQuestion.getQuestion()));
        }
        return dto;
    }

    public QuestionDto convertToDto(Question question) {
        QuestionDto dto = new QuestionDto();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setOptions(question.getOptions());
        dto.setCorrectOption(question.getCorrectOption());
        return dto;
    }

    public List<AssessmentDto> getAssessmentsForSubscribedCourses(String token) {
        // Call subscription-service to get courses the user is subscribed to
        String url = "http://subscription-service/api/subscriptions/my-courses";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", token);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
        
        // SubscriptionCourseDto[] is not available in this module, so use Map or Object and extract courseIds
        List<Long> courseIds = new ArrayList<>();
        try {
            org.springframework.http.ResponseEntity<Object[]> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                entity,
                Object[].class
            );
            Object[] subscriptionCourses = response.getBody();
            if (subscriptionCourses != null) {
                for (Object obj : subscriptionCourses) {
                    // Each obj is a LinkedHashMap, extract course.id
                    if (obj instanceof java.util.Map) {
                        java.util.Map<?,?> map = (java.util.Map<?,?>) obj;
                        Object courseObj = map.get("course");
                        if (courseObj instanceof java.util.Map) {
                            Object idObj = ((java.util.Map<?,?>) courseObj).get("id");
                            if (idObj instanceof Number) {
                                courseIds.add(((Number) idObj).longValue());
                            } else if (idObj != null) {
                                try {
                                    courseIds.add(Long.parseLong(idObj.toString()));
                                } catch (Exception ignore) {}
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch subscriptions: " + e.getMessage());
        }
        // Now fetch all assessments for these courseIds
        List<AssessmentDto> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            result.addAll(getAssessmentsByCourseId(courseId));
        }
        return result;
    }

    public List<AssessmentDto> getAllAssessmentsWithCourseInfo() {
        List<Assessment> assessments = assessmentRepository.findAll();
        List<AssessmentDto> result = new ArrayList<>();
        for (Assessment assessment : assessments) {
            result.add(convertToDto(assessment));
        }
        return result;
    }

    public List<AssessmentDto> getAssessmentsByInstructor(String instructorEmail) {
        List<Assessment> assessments = assessmentRepository.findByInstructorEmail(instructorEmail);
        List<AssessmentDto> result = new ArrayList<>();
        for (Assessment assessment : assessments) {
            result.add(convertToDto(assessment));
        }
        return result;
    }
}