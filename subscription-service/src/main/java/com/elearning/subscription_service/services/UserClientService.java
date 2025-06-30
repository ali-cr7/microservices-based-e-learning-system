package com.elearning.subscription_service.services;

import com.elearning.subscription_service.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClientService {
    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserDetailsFallback")
    public UserDto getUserDetails(String email) {
        return restTemplate.getForObject("http://user-service/api/users/email?email=" + email, UserDto.class);
    }

    public UserDto getUserDetailsFallback(String email, Throwable t) {
        System.err.println("Fallback for getUserDetails for email " + email + ". Error: " + t.getMessage());
        UserDto defaultUser = new UserDto();
        defaultUser.setEmail(email);
        defaultUser.setName("User info unavailable");
        defaultUser.setRole("UNKNOWN");
        return defaultUser;
    }
} 