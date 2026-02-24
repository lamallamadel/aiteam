package com.atlasia.ai.controller;

import com.atlasia.ai.config.RequiresPermission;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private final ArtifactStorageService artifactStorageService;
    private final AuthorizationService authorizationService;
    private final CurrentUserService currentUserService;
    private final RateLimitService rateLimitService;

    public FileUploadController(
            ArtifactStorageService artifactStorageService,
            AuthorizationService authorizationService,
            CurrentUserService currentUserService,
            RateLimitService rateLimitService) {
        this.artifactStorageService = artifactStorageService;
        this.authorizationService = authorizationService;
        this.currentUserService = currentUserService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(value = "/{runId}/artifacts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_UPDATE)
    public ResponseEntity<Map<String, Object>> uploadArtifact(
            @PathVariable("runId") UUID runId,
            @RequestParam("file") MultipartFile file) {
        
        try {
            UUID currentUserId = currentUserService.getCurrentUserId();
            
            if (!authorizationService.hasPermission(currentUserId, RoleService.RESOURCE_RUN, RoleService.ACTION_UPDATE, runId)) {
                logger.warn("User {} attempted to upload artifact to run {} without permission", currentUserId, runId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            RunArtifactEntity artifact = artifactStorageService.storeArtifact(runId, file, currentUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("artifactId", artifact.getId());
            response.put("runId", runId);
            response.put("originalFilename", artifact.getOriginalFilename());
            response.put("contentType", artifact.getContentType());
            response.put("sizeBytes", artifact.getSizeBytes());
            response.put("uploadedAt", artifact.getUploadedAt());

            logger.info("Artifact uploaded successfully: {} for run {} by user {}", 
                artifact.getId(), runId, currentUserId);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid upload request for run {}: {}", runId, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Bad Request");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (SecurityException e) {
            logger.warn("Security error during upload for run {}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            logger.error("Unexpected error during upload for run {}", runId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal Server Error");
            errorResponse.put("message", "Failed to upload artifact");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{runId}/artifacts/{artifactId}/download")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_VIEW)
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable("runId") UUID runId,
            @PathVariable("artifactId") UUID artifactId) {
        
        try {
            UUID currentUserId = currentUserService.getCurrentUserId();

            if (!rateLimitService.allowRequest(currentUserId, "artifact_download")) {
                logger.warn("Rate limit exceeded for user {} downloading artifact {}", currentUserId, artifactId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", "0")
                    .build();
            }

            if (!authorizationService.hasPermission(currentUserId, RoleService.RESOURCE_RUN, RoleService.ACTION_VIEW, runId)) {
                logger.warn("User {} attempted to download artifact {} from run {} without permission", 
                    currentUserId, artifactId, runId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            RunArtifactEntity artifact = artifactStorageService.getArtifact(artifactId);

            if (!artifact.getRun().getId().equals(runId)) {
                logger.warn("Artifact {} does not belong to run {}", artifactId, runId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Path filePath = artifactStorageService.getArtifactPath(artifactId);
            
            if (!Files.exists(filePath)) {
                logger.error("Artifact file not found on disk: {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Resource resource = new FileSystemResource(filePath);

            String contentType = artifact.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }

            String filename = artifact.getOriginalFilename();
            if (filename == null || filename.isEmpty()) {
                filename = "artifact_" + artifactId.toString();
            }

            int remainingRequests = rateLimitService.getRemainingRequests(currentUserId, "artifact_download");

            logger.info("User {} downloading artifact {} from run {}", currentUserId, artifactId, runId);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-RateLimit-Remaining", String.valueOf(remainingRequests))
                .body(resource);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid download request for artifact {}: {}", artifactId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (SecurityException e) {
            logger.warn("Security error during download for artifact {}: {}", artifactId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            logger.error("Unexpected error during download for artifact {}", artifactId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
