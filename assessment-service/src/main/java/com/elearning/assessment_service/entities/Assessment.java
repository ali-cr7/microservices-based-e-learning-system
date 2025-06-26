package com.elearning.assessment_service.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long courseId; // Link to the course service

    private String description;
    private Integer durationMinutes;
    private Double passMark;
    private Integer maxAttempts;

    private LocalDateTime creationDate;

    @Column(nullable = false)
    private String instructorEmail;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AssessmentQuestion> assessmentQuestions;
} 