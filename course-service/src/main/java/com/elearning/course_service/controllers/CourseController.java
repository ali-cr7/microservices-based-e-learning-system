package com.elearning.course_service.controllers;

import com.elearning.course_service.dto.CourseResponseDto;
import com.elearning.course_service.dto.SessionDto;
import com.elearning.course_service.dto.UserDto;
import com.elearning.course_service.entities.Course;
import com.elearning.course_service.services.CourseService;
import com.elearning.course_service.services.FileStorageService; // New import
import com.elearning.course_service.services.SessionService;
import com.elearning.course_service.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final SessionService sessionService;
    private final FileStorageService fileStorageService; // Inject FileStorageService

    @Autowired
    public CourseController(CourseService courseService, JwtUtil jwtUtil, RestTemplate restTemplate, SessionService sessionService, FileStorageService fileStorageService) {
        this.courseService = courseService;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
        this.sessionService = sessionService;
        this.fileStorageService = fileStorageService; // Initialize FileStorageService
    }

    @PostMapping
    public ResponseEntity<?> addCourse(@RequestHeader("Authorization") String authHeader, @RequestBody Course course) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }

        String instructorEmail = jwtUtil.extractAllClaims(token).getSubject(); // Assuming subject is email
        String roleFromToken = jwtUtil.extractRole(token);

        // Step 1: Validate role from token (initial check, as before)
        if (!"INSTRUCTOR".equals(roleFromToken) && !"ADMIN".equals(roleFromToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only instructors or admins can add courses");
        }

        // Step 2: Call User-Service to verify instructor's actual role and existence
        UserDto userFromUserService;
        try {
            userFromUserService = restTemplate.getForObject(
                    "http://user-service/api/users/email?email=" + instructorEmail,
                    UserDto.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Instructor not found in user service.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error communicating with user service: " + e.getMessage());
        }

        if (userFromUserService == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Instructor not found or invalid from user service.");
        }

        // Double check the role from the user service's data
        if (!"INSTRUCTOR".equals(userFromUserService.getRole()) && !"ADMIN".equals(userFromUserService.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("User found, but does not have INSTRUCTOR or ADMIN role in user service.");
        }

        course.setInstructor(instructorEmail);
        course.setInstructorName(userFromUserService.getName());
        return ResponseEntity.ok(courseService.addCourse(course));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<CourseResponseDto>> getMyCourseRequests(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String role = jwtUtil.extractRole(token);
        if (!"INSTRUCTOR".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String instructorEmail = jwtUtil.extractAllClaims(token).getSubject();
        return ResponseEntity.ok(courseService.getCoursesByInstructorAndApprovedStatus(instructorEmail, false));
    }

    @GetMapping("/my-courses")
    public ResponseEntity<List<CourseResponseDto>> getMyCourses(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String role = jwtUtil.extractRole(token);
        if (!"INSTRUCTOR".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String instructorEmail = jwtUtil.extractAllClaims(token).getSubject();
        return ResponseEntity.ok(courseService.getCoursesByInstructorAndApprovedStatus(instructorEmail, true));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<CourseResponseDto>> getCourseRequests(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        String role = jwtUtil.extractRole(token);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        return ResponseEntity.ok(courseService.getCourseRequests());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCourse(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String role = jwtUtil.extractRole(token);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only admins can approve courses");
        }
        return courseService.approveCourse(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String role = jwtUtil.extractRole(token);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only admins can delete courses");
        }
        if (courseService.deleteCourse(id)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<CourseResponseDto>> getAvailableCourses() {
        return ResponseEntity.ok(courseService.getAvailableCourses());
    }

    // Keep existing endpoints, if any, and consider their authorization
    @GetMapping("/{id}")
    public ResponseEntity<CourseResponseDto> getCourseById(@PathVariable Long id) {
        CourseResponseDto course = courseService.getCourseById(id);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(course);
    }

    @GetMapping("/{courseId}/instructor")
    public ResponseEntity<?> getCourseInstructor(@PathVariable Long courseId) {
        CourseResponseDto course = courseService.getCourseById(courseId);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }

        String instructorEmail = course.getInstructor();
        if (instructorEmail == null || instructorEmail.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Instructor information not available for this course.");
        }

        try {
            UserDto instructor = restTemplate.getForObject(
                    "http://user-service/api/users/email?email=" + instructorEmail,
                    UserDto.class
            );
            if (instructor == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Instructor not found in user service.");
            }
            return ResponseEntity.ok(instructor);
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Instructor not found in user service.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching instructor details: " + e.getMessage());
        }
    }

    @PostMapping(value = "/{courseId}/sessions", consumes = {"multipart/form-data"})
    public ResponseEntity<?> addSessionToCourse(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable Long courseId,
                                                @RequestParam("title") String title,
                                                @RequestPart("file") MultipartFile file) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }

        String userEmail = jwtUtil.extractAllClaims(token).getSubject();
        String roleFromToken = jwtUtil.extractRole(token);

        CourseResponseDto course = courseService.getCourseById(courseId);
        if (course == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Course not found");
        }

        if (!"ADMIN".equals(roleFromToken) && !(("INSTRUCTOR".equals(roleFromToken) || "ADMIN".equals(roleFromToken)) && userEmail.equals(course.getInstructor()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the course instructor or an admin can add sessions to this course");
        }

        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PDF file is required.");
        }

        try {
            // Store the file and get its URL
            String fileUrl = fileStorageService.storeFile(file);
            // Pass the URL to the session service
            SessionDto sessionDto = sessionService.createSession(courseId, title, fileUrl);
            return ResponseEntity.status(HttpStatus.CREATED).body(sessionDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error creating session: " + e.getMessage());
        }
    }

    @GetMapping("/{courseId}/sessions")
    public ResponseEntity<?> getSessionsForCourse(@RequestHeader("Authorization") String authHeader, @PathVariable Long courseId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }      String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String userEmail = jwtUtil.extractAllClaims(token).getSubject();
        String roleFromToken = jwtUtil.extractRole(token);
        CourseResponseDto course = courseService.getCourseById(courseId);
        if (course == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Course not found");
        }       // All authenticated users can view sessions


             List<SessionDto> sessions = sessionService.getSessionsByCourseId(courseId);
        return ResponseEntity.ok(sessions);
    }
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSessionById(@RequestHeader("Authorization") String authHeader, @PathVariable Long sessionId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }       String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }      // All authenticated users can view a specific session
        Optional<SessionDto> sessionDto = sessionService.getSessionById(sessionId);
        return sessionDto.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@RequestHeader("Authorization") String authHeader, @PathVariable Long sessionId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Missing or invalid Authorization header");
        }       String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired token");
        }
        String userEmail = jwtUtil.extractAllClaims(token).getSubject();
        String roleFromToken = jwtUtil.extractRole(token);
        Optional<SessionDto> sessionDto = sessionService.getSessionById(sessionId);
        if (sessionDto.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");     }
        Long courseId = sessionDto.get().getCourseId(); 
            CourseResponseDto course = courseService.getCourseById(courseId);   
               if (course == null) {        
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Associated course not found for session");
               }      if (!"ADMIN".equals(roleFromToken) && !(("INSTRUCTOR".equals(roleFromToken) || "ADMIN".equals(roleFromToken)) && userEmail.equals(course.getInstructor()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the course instructor or an admin can delete sessions from this course");
        }
               sessionService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }}