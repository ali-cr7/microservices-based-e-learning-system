package com.elearning.assessment_service.entities;

import com.elearning.assessment_service.utils.HashMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "assessment_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(nullable = false)
    private String learnerEmail;

    private LocalDateTime startTime;
    private LocalDateTime endTime; // Renamed from submitTime
    private boolean completed;     // Replaced AttemptStatus
    private int score;             // Changed from Double to int

    @Convert(converter = HashMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, String> answers; // Changed from String to Map<String, String>
}