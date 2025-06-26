package com.elearning.assessment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    @NotBlank(message = "Question text cannot be empty")
    private String text;
    private List<String> options;
    private String correctOption;
    private String selectedAnswer;
} 