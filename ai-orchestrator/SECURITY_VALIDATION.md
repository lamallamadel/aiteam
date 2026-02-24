# Input Validation and Security Implementation

This document describes the comprehensive input validation and security measures implemented in the Atlasia AI Orchestrator.

## Components Implemented

### 1. InputSanitizationService (`service/InputSanitizationService.java`)

A centralized service for input sanitization and validation:

- **`sanitizeHtml(input)`**: Uses OWASP Java HTML Sanitizer with a PolicyFactory that allows only safe HTML tags (b, i, u, em, strong, p, br, span, div, h1-h6, ul, ol, li, a, code, pre, blockquote) with limited attributes
- **`sanitizeFileName(name)`**: Removes path traversal characters (../, ..\, /, \) and invalid filename characters
- **`validateRepositoryPath(path)`**: Ensures repository paths match the allowlist pattern `owner/repo` format
- **`isValidFileName(fileName)`**: Validates filename contains only safe characters and no path traversal attempts
- **`isSafeHtml(input)`**: Checks if input HTML is already safe

### 2. Custom Jakarta Validation Annotations

Located in `service/exception/validation/`:

#### `@SafeHtml`
- Validates that string input contains only safe HTML
- Uses `SafeHtmlValidator` backed by `InputSanitizationService`
- Applied to fields that may contain user-generated HTML content

#### `@SafeFileName`
- Validates filename safety (no path traversal, only alphanumeric and safe characters)
- Uses `SafeFileNameValidator` backed by `InputSanitizationService`
- Prevents directory traversal attacks

#### `@RepositoryPath`
- Validates repository paths follow `owner/repo` format
- Uses `RepositoryPathValidator` backed by `InputSanitizationService`
- Ensures repository paths match allowlist pattern

### 3. Enhanced DTO Validation

All DTOs have been updated with comprehensive validation annotations:

#### `RunRequest`
- `repo`: @NotBlank, @RepositoryPath, @Size(max=255)
- `issueNumber`: @NotNull, @Min(1)
- `mode`: @NotBlank, @Pattern(code|chat), @Size(max=50)
- `autonomy`: @Pattern(autonomous|confirm|observe), @Size(max=50)

#### `EscalationDecisionRequest`
- `decision`: @NotBlank, @Pattern(PROCEED|ABORT)
- `guidance`: @SafeHtml, @Size(max=5000)

#### `UserRegistrationRequest`
- `username`: @NotBlank, @Pattern(alphanumeric+_-), @Size(3-50)
- `email`: @NotBlank, @Email, @Size(max=255)
- `password`: @NotBlank, @Size(8-128)

#### `LoginRequest`
- `username`: @NotBlank, @Size(3-50)
- `password`: @NotBlank, @Size(1-128)
- `deviceInfo`: @SafeHtml, @Size(max=500)

#### `PasswordResetInitiateRequest`
- `email`: @NotBlank, @Email, @Size(max=255)

#### `PasswordResetCompleteRequest`
- `token`: @NotBlank, @Size(max=500)
- `newPassword`: @NotBlank, @Size(8-128)

#### `RefreshTokenRequest`
- `refreshToken`: @NotBlank, @Size(max=1000)

#### `OAuth2LinkRequest`
- `provider`: @NotBlank, @Pattern(alphanumeric+_-), @Size(max=50)
- `providerUserId`: @NotBlank, @Size(max=255)
- `accessToken`: @Size(max=2000)
- `refreshToken`: @Size(max=2000)

### 4. ValidationExceptionHandler

Global exception handler (`service/exception/ValidationExceptionHandler.java`) with `@ControllerAdvice`:

- Catches `MethodArgumentNotValidException` (from @Valid on @RequestBody)
- Catches `ConstraintViolationException` (from @Validated on method parameters)
- Returns structured 400 Bad Request responses with:
  - Error message
  - HTTP status code
  - Timestamp
  - List of field validation errors (field name, rejected value, error message)

Response format:
```json
{
  "message": "Validation failed",
  "status": 400,
  "timestamp": "2024-02-24T01:14:00Z",
  "errors": [
    {
      "field": "email",
      "rejectedValue": "invalid-email",
      "message": "Email must be valid"
    }
  ]
}
```

### 5. Controller Validation

All controllers have been annotated with `@Validated`:
- `RunController`
- `AuthController`
- `GraftController`
- `ChatController`
- `OversightController`
- `AnalyticsController`
- `WebSocketAdminController`
- `TraceController`
- `PersonaController`
- `EvalController`
- `CollaborationController`
- `A2AController`

This enables method-level validation for @RequestBody parameters marked with @Valid.

### 6. SQL Injection Prevention

All JPA repositories already use parameterized queries through:
- Spring Data JPA method names (automatically parameterized)
- @Query annotations with @Param parameters (manually parameterized)

Examples from `RunRepository`:
```java
@Query("SELECT r FROM RunEntity r WHERE r.status = :status AND r.repo = :repo")
List<RunEntity> findByStatusAndRepo(@Param("status") RunStatus status, @Param("repo") String repo);
```

This approach prevents SQL injection by separating SQL logic from data values.

## Security Benefits

1. **XSS Prevention**: HTML sanitization prevents cross-site scripting attacks
2. **Path Traversal Prevention**: Filename and repository path validation blocks directory traversal
3. **SQL Injection Prevention**: Parameterized queries prevent SQL injection
4. **Input Length Limits**: @Size constraints prevent buffer overflow and DoS attacks
5. **Format Validation**: @Pattern and custom validators ensure data integrity
6. **Structured Error Responses**: Consistent error handling without leaking sensitive information

## Dependencies

The implementation uses:
- `owasp-java-html-sanitizer` (already in pom.xml version 20240325.1)
- Jakarta Validation API (jakarta.validation.constraints)
- Spring Validation (org.springframework.validation)
- Spring Boot Starter Validation (already in pom.xml)
