package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.UserRegistrationRequest;
import com.atlasia.ai.api.dto.UserRegistrationResponse;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class UserRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final RoleRepository roleRepository;

    public UserRegistrationService(
            UserRepository userRepository,
            PasswordService passwordService,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UserRegistrationResponse registerUser(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        username = username.trim();
        email = email.trim().toLowerCase();
        
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        if (!passwordService.validatePasswordStrength(password)) {
            String errors = passwordService.getPasswordStrengthErrors(password);
            throw new IllegalArgumentException("Password does not meet strength requirements: " + errors);
        }
        
        String passwordHash = passwordService.hashPassword(password);
        
        UserEntity user = new UserEntity(username, email, passwordHash);
        
        RoleEntity userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(new RoleEntity("USER", "Default user role")));
        user.addRole(userRole);
        
        UserEntity savedUser = userRepository.save(user);
        
        passwordService.savePasswordToHistory(savedUser.getId(), passwordHash);
        
        logger.info("User registered successfully: username={}, email={}", username, email);
        
        return new UserRegistrationResponse(
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail(),
            "User registered successfully"
        );
    }

    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        return registerUser(request.getUsername(), request.getEmail(), request.getPassword());
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
