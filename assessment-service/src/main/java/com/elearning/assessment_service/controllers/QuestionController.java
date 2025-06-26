package com.elearning.assessment_service.controllers;

import com.elearning.assessment_service.utils.JwtUtil;
import com.elearning.assessment_service.services.QuestionService;
import com.elearning.assessment_service.dto.QuestionDto;
import com.elearning.assessment_service.entities.Question;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assessments/questions")
public class QuestionController {
    private final QuestionService questionService;
    private final JwtUtil jwtUtil;

    @Autowired
    public QuestionController(QuestionService questionService, JwtUtil jwtUtil) {
        this.questionService = questionService;
        this.jwtUtil = jwtUtil;
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
    public ResponseEntity<QuestionDto> createQuestion(@RequestHeader("Authorization") String token, @Valid @RequestBody QuestionDto questionDto) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        Question question = questionService.createQuestion(questionDto);
        return new ResponseEntity<>(questionService.convertToDto(question), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionDto> getQuestionById(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable Long id) {
        validateTokenAndExtractRole(token, "INSTRUCTOR", "ADMIN", "LEARNER");
        Optional<Question> question = questionService.getQuestionById(id);
        return question.map(q -> new ResponseEntity<>(questionService.convertToDto(q), HttpStatus.OK))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    @GetMapping
    public ResponseEntity<List<QuestionDto>> getAllQuestions(@RequestHeader("Authorization") String token) {
        validateTokenAndExtractRole(token, "INSTRUCTOR", "ADMIN");
        List<Question> questions = questionService.getAllQuestions();
        List<QuestionDto> questionDtos = questions.stream()
                .map(q -> questionService.convertToDto(q))
                .collect(Collectors.toList());
        return new ResponseEntity<>(questionDtos, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionDto> updateQuestion(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestBody QuestionDto questionDto) {
        validateTokenAndExtractRole(token, "INSTRUCTOR");
        Question updatedQuestion = questionService.updateQuestion(id, questionDto);
        if (updatedQuestion != null) {
            return new ResponseEntity<>(questionService.convertToDto(updatedQuestion), HttpStatus.OK);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        validateTokenAndExtractRole(token, "ADMIN");
        if (questionService.deleteQuestion(id)) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }
    }
} 