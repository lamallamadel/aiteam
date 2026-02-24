package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.RunArtifactRepository;
import com.atlasia.ai.persistence.RunRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ArtifactStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactStorageService.class);
    
    private static final String STORAGE_ROOT = "/app/artifacts";
    
    private final RunRepository runRepository;
    private final RunArtifactRepository artifactRepository;
    private final FileUploadSecurityService fileUploadSecurityService;
    private final Tika tika;

    public ArtifactStorageService(
            RunRepository runRepository,
            RunArtifactRepository artifactRepository,
            FileUploadSecurityService fileUploadSecurityService) {
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.fileUploadSecurityService = fileUploadSecurityService;
        this.tika = new Tika();
    }

    @Transactional
    public RunArtifactEntity storeArtifact(UUID runId, MultipartFile file, UUID uploadedBy) {
        RunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        fileUploadSecurityService.validateFileUpload(file);

        String sanitizedFilename = fileUploadSecurityService.sanitizeFilename(file.getOriginalFilename());
        String storageFilename = UUID.randomUUID().toString();
        
        Path runDirectory = Paths.get(STORAGE_ROOT, runId.toString());
        Path filePath = runDirectory.resolve(storageFilename);

        try {
            Files.createDirectories(runDirectory);
            
            Files.copy(file.getInputStream(), filePath);
            
            setRestrictivePermissions(filePath);
            
            String contentType = detectContentType(file);
            
            RunArtifactEntity artifact = new RunArtifactEntity(
                "FILE_UPLOAD",
                "uploaded_file",
                "{}",
                Instant.now()
            );
            artifact.setRun(run);
            artifact.setOriginalFilename(sanitizedFilename);
            artifact.setContentType(contentType);
            artifact.setSizeBytes(file.getSize());
            artifact.setUploadedBy(uploadedBy);
            artifact.setUploadedAt(Instant.now());
            artifact.setFilePath(filePath.toString());
            
            artifactRepository.save(artifact);
            
            logger.info("Stored artifact {} for run {} by user {}: {} bytes, type {}", 
                artifact.getId(), runId, uploadedBy, file.getSize(), contentType);
            
            return artifact;
            
        } catch (IOException e) {
            logger.error("Failed to store artifact for run {}", runId, e);
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private void setRestrictivePermissions(Path filePath) {
        try {
            if (filePath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> permissions = new HashSet<>();
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(filePath, permissions);
                logger.debug("Set restrictive permissions (600) on file: {}", filePath);
            } else {
                logger.warn("POSIX file permissions not supported on this file system, skipping permission setting");
            }
        } catch (IOException e) {
            logger.error("Failed to set restrictive permissions on file: {}", filePath, e);
        }
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream);
        } catch (IOException e) {
            logger.warn("Failed to detect content type for file: {}", file.getOriginalFilename(), e);
            return "application/octet-stream";
        }
    }

    public Path getArtifactPath(UUID artifactId) {
        RunArtifactEntity artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));
        
        if (artifact.getFilePath() == null) {
            throw new IllegalArgumentException("Artifact is not a file upload");
        }
        
        Path path = Paths.get(artifact.getFilePath());
        
        if (!Files.exists(path)) {
            throw new IllegalStateException("Artifact file not found on disk: " + path);
        }
        
        return path;
    }

    public RunArtifactEntity getArtifact(UUID artifactId) {
        return artifactRepository.findById(artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));
    }
}
