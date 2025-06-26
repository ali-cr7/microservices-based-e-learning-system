package com.elearning.assessment_service.entities;

import com.elearning.assessment_service.utils.StringListConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000, name = "question_text")
    private String text;

    @Convert(converter = StringListConverter.class)
    @Column(length = 1000)
    private List<String> options;

    @Column(length = 500)
    private String correctOption;
} 