package com.atlasia.ai.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final JwtService jwtService;

    public CurrentUserService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("No authenticated user found");
        }

        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            Object details = authentication.getDetails();
            if (details instanceof UUID) {
                return (UUID) details;
            }
            
            Object credentials = authentication.getCredentials();
            if (credentials instanceof String) {
                try {
                    return jwtService.extractUserId((String) credentials);
                } catch (Exception e) {
                }
            }
        }

        throw new SecurityException("Could not extract user ID from authentication");
    }

    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("No authenticated user found");
        }

        return authentication.getName();
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
