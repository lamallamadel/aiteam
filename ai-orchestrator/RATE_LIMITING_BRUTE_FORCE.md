# Rate Limiting and Brute-Force Protection

## Overview

Atlasia AI Orchestrator implements comprehensive rate limiting and brute-force protection to secure the application against abuse and malicious attacks.

## Rate Limiting

### Configuration

Rate limits are configured in `application.yml` under `resilience4j.ratelimiter`:

```yaml
resilience4j:
  ratelimiter:
    instances:
      auth:
        limitForPeriod: 5
        limitRefreshPeriod: 1m
        timeoutDuration: 0
        registerHealthIndicator: true
        eventConsumerBufferSize: 100
      api:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
        timeoutDuration: 0
        registerHealthIndicator: true
        eventConsumerBufferSize: 100
      upload:
        limitForPeriod: 10
        limitRefreshPeriod: 1m
        timeoutDuration: 0
        registerHealthIndicator: true
        eventConsumerBufferSize: 100
```

### Endpoint Categories

1. **Authentication Endpoints** (`/api/auth/login`, `/api/auth/register`, `/api/auth/password-reset`)
   - **Limit**: 5 requests per minute per IP address
   - **Purpose**: Prevent brute-force attacks on authentication

2. **API Endpoints** (all `/api/*` except auth and upload)
   - **Limit**: 100 requests per minute per user
   - **Purpose**: Prevent API abuse and ensure fair resource usage

3. **File Upload Endpoints** (paths containing `/upload`)
   - **Limit**: 10 requests per minute per user
   - **Purpose**: Prevent abuse of file upload functionality

### Implementation

- **Filter**: `RateLimitingFilter` intercepts all requests and applies rate limits based on endpoint category
- **Per-IP Limiting**: Authentication endpoints use IP-based rate limiting
- **Per-User Limiting**: API and upload endpoints use authenticated user-based rate limiting
- **Response Headers**: 
  - `X-RateLimit-Remaining`: Number of remaining requests
  - `Retry-After`: Time in seconds to wait before retrying (when rate limited)

### HTTP Response

When rate limit is exceeded:
- **Status Code**: 429 Too Many Requests
- **Headers**: `X-RateLimit-Remaining: 0`, `Retry-After: 60`
- **Body**: `{"error":"Too Many Requests","message":"Rate limit exceeded. Please try again later."}`

## Brute-Force Protection

### Configuration

- **Max Failed Attempts**: 5 attempts
- **Lock Duration**: 15 minutes
- **Implementation**: `BruteForceProtectionService`

### Features

1. **Failed Login Tracking**
   - Tracks failed login attempts per username using Caffeine cache
   - Cache expires after 15 minutes automatically

2. **Account Locking**
   - Automatically locks account after 5 failed login attempts
   - Lock persists in database (`users.locked` column)
   - Cache-based temporary lock for 15 minutes

3. **Email Notifications**
   - Sends alert email when account is locked
   - Includes information about lock duration and unlock process
   - Gracefully handles missing mail configuration

4. **Automatic Unlock**
   - Accounts automatically unlock after 15 minutes
   - Failed attempt counter resets automatically

### Authentication Flow

1. Check if user is blocked (brute-force protection)
2. Load user from database
3. Verify account is enabled
4. Verify account is not permanently locked
5. Validate password
   - On failure: record failed login attempt
   - On success: reset failed login counter

### Admin Unlock Endpoint

**Endpoint**: `POST /api/admin/users/{userId}/unlock?username={username}`

**Authentication**: Requires `ADMIN` role

**Response**:
```json
{
  "message": "Account unlocked successfully",
  "userId": "uuid",
  "username": "string"
}
```

**Example**:
```bash
curl -X POST "https://api.atlasia.ai/api/admin/users/123e4567-e89b-12d3-a456-426614174000/unlock?username=john.doe" \
  -H "Authorization: Bearer <admin-token>"
```

## Prometheus Metrics

### Rate Limiting Metrics

- `ratelimiter.rejected.auth`: Counter for rejected authentication requests
- `ratelimiter.rejected.api`: Counter for rejected API requests
- `ratelimiter.rejected.upload`: Counter for rejected upload requests
- `resilience4j.ratelimiter.available.permissions`: Gauge for available permissions per rate limiter
- `resilience4j.ratelimiter.waiting.threads`: Gauge for waiting threads per rate limiter

### Brute-Force Protection Metrics

- `bruteforce.blocked.accounts`: Counter for accounts blocked due to brute force
- `bruteforce.failed.logins`: Counter for failed login attempts

### Health Indicators

- `/actuator/health`: Includes rate limiter health status
- Rate limiter registry exposes operational status

## Email Configuration

Email notifications require mail server configuration in `application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${vault.secret.data.atlasia.mail-username}
    password: ${vault.secret.data.atlasia.mail-password}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
        from: noreply@atlasia.ai
```

**Note**: Email notifications are optional. If mail is not configured, the system will log a warning and continue operation without sending emails.

## Security Considerations

1. **IP Spoofing Protection**: Filter checks `X-Forwarded-For` and `X-Real-IP` headers for proxy scenarios
2. **Cache Size Limits**: Failed login cache limited to 10,000 entries
3. **Automatic Cleanup**: Caffeine cache automatically expires old entries
4. **Persistent Locking**: Database-level locks survive application restarts
5. **Audit Logging**: All security events are logged with appropriate levels

## Dependencies

- `resilience4j-ratelimiter:2.1.0`: Rate limiting implementation
- `caffeine`: High-performance caching for failed login attempts
- `spring-boot-starter-mail`: Email notification support (optional)
- `micrometer-registry-prometheus`: Metrics export

## Testing

Test rate limiting by making multiple requests:

```bash
# Test authentication rate limit (5 requests/minute per IP)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}'
  sleep 1
done

# Test brute-force protection (5 failed attempts)
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"victim","password":"wrongpass"}'
  echo "Attempt $i"
done
```

## Monitoring

Access Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus | grep -E "(ratelimiter|bruteforce)"
```

Check rate limiter health:

```bash
curl http://localhost:8080/actuator/health
```
