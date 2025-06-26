package com.elearning.course_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponseDto {
    private Long id;
    private String title;
    private String description;
    private Double price;
    private String instructor;
    private boolean approved;
    private  String instructorName;
    private List<SessionDto> sessions;
} 