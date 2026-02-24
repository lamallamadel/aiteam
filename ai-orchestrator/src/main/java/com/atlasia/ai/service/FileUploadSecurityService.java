package com.atlasia.ai.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FileUploadSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadSecurityService.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "text/plain",
        "application/json",
        "application/pdf",
        "image/png",
        "image/jpeg"
    ));

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*[/\\\\]\\.\\.([/\\\\]|$).*");
    private static final Pattern SUSPICIOUS_CHARACTERS = Pattern.compile(".*[\\x00-\\x1F].*");
    
    private final Tika tika;

    public FileUploadSecurityService() {
        this.tika = new Tika();
    }

    public void validateFileUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        validateFileSize(file);
        validateFilename(file.getOriginalFilename());
        validateMimeType(file);
        performVirusScan(file);
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            logger.warn("File size exceeds maximum allowed: {} bytes (max: {} bytes)", 
                file.getSize(), MAX_FILE_SIZE);
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / 1024 / 1024)
            );
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        if (PATH_TRAVERSAL_PATTERN.matcher(filename).matches()) {
            logger.warn("Path traversal attempt detected in filename: {}", filename);
            throw new IllegalArgumentException("Invalid filename: path traversal detected");
        }

        if (SUSPICIOUS_CHARACTERS.matcher(filename).matches()) {
            logger.warn("Suspicious characters detected in filename: {}", filename);
            throw new IllegalArgumentException("Invalid filename: contains suspicious characters");
        }

        if (filename.length() > 255) {
            throw new IllegalArgumentException("Filename is too long (max 255 characters)");
        }
    }

    private void validateMimeType(MultipartFile file) {
        String detectedMimeType;
        try (InputStream inputStream = file.getInputStream()) {
            detectedMimeType = tika.detect(inputStream);
        } catch (IOException e) {
            logger.error("Failed to detect MIME type for file: {}", file.getOriginalFilename(), e);
            throw new IllegalArgumentException("Failed to validate file type");
        }

        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            logger.warn("Unsupported MIME type detected: {} for file: {}", 
                detectedMimeType, file.getOriginalFilename());
            throw new IllegalArgumentException(
                String.format("File type not allowed. Detected type: %s. Allowed types: %s", 
                    detectedMimeType, String.join(", ", ALLOWED_MIME_TYPES))
            );
        }

        logger.debug("File {} validated with MIME type: {}", file.getOriginalFilename(), detectedMimeType);
    }

    private void performVirusScan(MultipartFile file) {
        logger.debug("Virus scan placeholder for file: {}", file.getOriginalFilename());
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unnamed";
        }

        String sanitized = filename.trim();
        
        Path path = Paths.get(sanitized);
        sanitized = path.getFileName().toString();
        
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        sanitized = sanitized.replaceAll("_+", "_");
        
        if (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
        }
        
        if (sanitized.length() > 255) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, lastDot);
            }
            sanitized = sanitized.substring(0, Math.min(sanitized.length(), 255 - extension.length())) + extension;
        }
        
        return sanitized;
    }
}
