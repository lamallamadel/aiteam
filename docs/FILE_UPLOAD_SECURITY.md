# File Upload Security - Run Artifacts

## Overview

This document describes the secure file upload implementation for run artifacts in the Atlasia AI Orchestrator.

## Components

### 1. FileUploadSecurityService

**Location**: `ai-orchestrator/src/main/java/com/atlasia/ai/service/FileUploadSecurityService.java`

**Responsibilities**:
- File size validation (max 50MB)
- MIME type detection using Apache Tika (content-based, not extension-based)
- Filename sanitization (removes path traversal attempts)
- Virus scanning integration point (placeholder for ClamAV)

**Allowed MIME Types**:
- `text/plain`
- `application/json`
- `application/pdf`
- `image/png`
- `image/jpeg`

**Security Features**:
- Path traversal prevention (blocks `../` patterns)
- Suspicious character filtering (control characters)
- Filename length limits (255 characters)
- Content-based MIME type detection (not relying on file extensions)

### 2. ArtifactStorageService

**Location**: `ai-orchestrator/src/main/java/com/atlasia/ai/service/ArtifactStorageService.java`

**Responsibilities**:
- Store uploaded files to file system
- Set restrictive file permissions (600 - owner read/write only)
- Generate UUID-based filenames to prevent collisions
- Store metadata in database

**Storage Structure**:
```
/app/artifacts/
  └── {runId}/
      └── {uuid}
```

**Features**:
- Files stored with UUID names (original filenames preserved in metadata)
- POSIX file permissions (600) set on uploaded files
- Metadata tracking: original_filename, content_type, size_bytes, uploaded_by, uploaded_at

### 3. RateLimitService

**Location**: `ai-orchestrator/src/main/java/com/atlasia/ai/service/RateLimitService.java`

**Responsibilities**:
- Rate limiting for download requests
- Sliding window algorithm (10 requests per minute per user)

**Configuration**:
- Max requests: 10 per minute
- Window size: 60 seconds
- Per-user tracking

### 4. CurrentUserService

**Location**: `ai-orchestrator/src/main/java/com/atlasia/ai/service/CurrentUserService.java`

**Responsibilities**:
- Extract current authenticated user from SecurityContext
- Extract user ID from JWT token

### 5. FileUploadController

**Location**: `ai-orchestrator/src/main/java/com/atlasia/ai/controller/FileUploadController.java`

**Endpoints**:

#### POST /api/runs/{runId}/artifacts/upload
- **Authorization**: Requires `RUN_UPDATE` permission
- **Content-Type**: `multipart/form-data`
- **Parameter**: `file` (MultipartFile)
- **Response**: JSON with artifact metadata
- **Status Codes**:
  - 201: Created
  - 400: Bad Request (validation error)
  - 401: Unauthorized
  - 403: Forbidden (insufficient permissions)
  - 500: Internal Server Error

#### GET /api/runs/{runId}/artifacts/{artifactId}/download
- **Authorization**: Requires `RUN_VIEW` permission
- **Rate Limiting**: 10 requests per minute per user
- **Response**: File with `Content-Disposition: attachment` header
- **Headers**:
  - `X-RateLimit-Remaining`: Remaining requests in current window
- **Status Codes**:
  - 200: OK
  - 401: Unauthorized
  - 403: Forbidden (insufficient permissions)
  - 404: Not Found
  - 429: Too Many Requests (rate limit exceeded)

## Database Schema

### Migration: V16__add_file_upload_artifacts.sql

Added columns to `ai_run_artifact` table:
- `original_filename` VARCHAR(500)
- `content_type` VARCHAR(255)
- `size_bytes` BIGINT
- `uploaded_by` UUID
- `uploaded_at` TIMESTAMP WITH TIME ZONE
- `file_path` VARCHAR(1000)

Indexes:
- `idx_ai_run_artifact_uploaded_by`
- `idx_ai_run_artifact_uploaded_at`

Made `payload` column nullable to support file uploads.

## Configuration

### application.yml

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
      enabled: true
```

### Security Configuration

- CSRF protection disabled for `/api/runs/**` endpoints
- CORS headers include `X-RateLimit-Remaining`
- JWT authentication required for all endpoints

## Security Features

1. **File Size Limits**: 50MB maximum
2. **MIME Type Validation**: Content-based detection using Apache Tika
3. **Filename Sanitization**: Removes path traversal and suspicious characters
4. **Authorization**: User must have RUN_UPDATE permission to upload, RUN_VIEW to download
5. **Rate Limiting**: 10 downloads per minute per user
6. **File Permissions**: POSIX 600 (owner read/write only)
7. **Virus Scanning**: Integration point ready for ClamAV
8. **UUID Filenames**: Prevents filename-based attacks
9. **Metadata Tracking**: Full audit trail with user, timestamps, file info

## Usage Examples

### Upload Artifact

```bash
curl -X POST \
  -H "Authorization: Bearer <jwt_token>" \
  -F "file=@/path/to/file.pdf" \
  http://localhost:8080/api/runs/{runId}/artifacts/upload
```

Response:
```json
{
  "artifactId": "123e4567-e89b-12d3-a456-426614174000",
  "runId": "123e4567-e89b-12d3-a456-426614174001",
  "originalFilename": "file.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1024000,
  "uploadedAt": "2024-01-15T10:30:00Z"
}
```

### Download Artifact

```bash
curl -X GET \
  -H "Authorization: Bearer <jwt_token>" \
  -OJ http://localhost:8080/api/runs/{runId}/artifacts/{artifactId}/download
```

Response Headers:
```
Content-Disposition: attachment; filename="file.pdf"
Content-Type: application/pdf
X-RateLimit-Remaining: 9
```

## Future Enhancements

1. **ClamAV Integration**: Implement actual virus scanning in `performVirusScan()`
2. **Storage Backend**: Add support for S3/cloud storage
3. **Compression**: Automatic compression for large text files
4. **Encryption**: Encrypt files at rest
5. **Retention Policy**: Automatic cleanup of old artifacts
6. **Checksums**: Store and verify file checksums
7. **Duplicate Detection**: Content-based deduplication
