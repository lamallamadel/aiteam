package com.atlasia.ai.controller;

import com.atlasia.ai.service.BruteForceProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final BruteForceProtectionService bruteForceProtectionService;

    public AdminController(BruteForceProtectionService bruteForceProtectionService) {
        this.bruteForceProtectionService = bruteForceProtectionService;
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> unlockUser(@PathVariable("userId") UUID userId, 
                                                           @RequestParam("username") String username) {
        try {
            bruteForceProtectionService.unlockAccount(username);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Account unlocked successfully");
            response.put("userId", userId);
            response.put("username", username);
            
            logger.info("Admin unlocked account for user: {}", username);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to unlock account for user {}: {}", username, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Bad Request");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            
        } catch (Exception e) {
            logger.error("Error unlocking account for user {}", username, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", "Failed to unlock account");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
