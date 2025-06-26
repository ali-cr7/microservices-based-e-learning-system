package com.elearning.course_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    private Long id;
    private String title;
    private String pdfFileUrl;
    private Long courseId;
} 