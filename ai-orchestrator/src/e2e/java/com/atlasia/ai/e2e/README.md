# Atlasia E2E Test Suite

This directory contains comprehensive End-to-End (E2E) tests for the Atlasia AI Orchestrator backend.

## Overview

The E2E test suite validates complete user workflows and system integration using:
- **WireMock** for mocking external services (GitHub API, LLM endpoints)
- **Spring Boot Test** with random ports for realistic testing
- **WebSocket/STOMP** clients for collaboration testing
- **Automated test reporting** with screenshots on failure

## Test Structure

```
e2e/
├── configuration/
│   ├── AbstractE2ETest.java         # Base test class with WireMock setup
│   ├── E2ETestReporter.java         # Test result reporter with HTML/JSON output
│   └── E2ETestWatcher.java          # JUnit extension for capturing failures
└── scenarios/
    ├── AuthenticationFlowE2ETest.java      # Complete auth flows
    ├── MfaLoginFlowE2ETest.java            # MFA setup and verification
    ├── OAuth2CallbackFlowE2ETest.java      # OAuth2 provider linking
    ├── PasswordResetFlowE2ETest.java       # Password reset workflows
    ├── WebSocketCollaborationE2ETest.java  # Real-time collaboration
    └── ...
```

## Test Categories

### 1. Authentication Flow Tests (`AuthenticationFlowE2ETest`)

Comprehensive authentication testing including:
- User registration and login
- MFA setup, verification, and login flow
- OAuth2 account linking (GitHub, Google)
- Password reset (initiation, completion, expired tokens)
- Token refresh and logout
- Current user retrieval

**Key Tests:**
- `testCompleteRegistrationAndLoginFlow()` - End-to-end user signup
- `testMfaSetupAndVerificationFlow()` - MFA QR code generation and activation
- `testOAuth2CallbackWithTokenValidation()` - OAuth2 token exchange and validation
- `testPasswordResetCompleteFlow()` - Full password reset workflow

### 2. MFA Tests (`MfaLoginFlowE2ETest`)

Multi-Factor Authentication workflows:
- MFA setup with QR code generation
- TOTP code verification
- MFA-required login flow
- Invalid code handling
- Unauthorized access prevention

**Key Tests:**
- `testMfaSetupWithQrCode()` - Generates secret and base64 QR code
- `testMfaSetupVerificationAndActivation()` - Activates MFA with valid TOTP
- `testMfaLoginFlowWithValidCode()` - Tests 2-step login process
- `testMfaLoginWithInvalidCode()` - Validates rejection of bad codes

### 3. OAuth2 Tests (`OAuth2CallbackFlowE2ETest`)

OAuth2 provider integration:
- GitHub account linking
- Google account linking
- Token validation via WireMock
- Multiple provider support
- Duplicate account conflict handling

**Key Tests:**
- `testOAuth2LinkAccountWithGitHub()` - Links GitHub account with token validation
- `testOAuth2LinkAccountWithGoogle()` - Links Google account
- `testOAuth2LinkWithMultipleProviders()` - User with 2+ OAuth2 accounts
- `testOAuth2PostMessageEventSimulation()` - Simulates OAuth2 popup callback

### 4. Password Reset Tests (`PasswordResetFlowE2ETest`)

Password reset workflows:
- Reset initiation with email
- Token generation and validation
- Reset completion
- Expired token handling
- Login with new password

**Key Tests:**
- `testPasswordResetInitiation()` - Generates reset token
- `testPasswordResetCompletion()` - Completes reset and updates password
- `testLoginWithNewPasswordAfterReset()` - Validates new credentials
- `testPasswordResetWithExpiredToken()` - Rejects expired tokens
- `testPasswordResetInvalidatesOldTokens()` - New token invalidates old ones

### 5. WebSocket Collaboration Tests (`WebSocketCollaborationE2ETest`)

Real-time collaboration features:
- WebSocket connection with JWT authentication
- Concurrent graft/prune/flag mutations
- CRDT-based conflict resolution
- Cursor tracking
- HTTP polling fallback
- Message sequencing and persistence

**Key Tests:**
- `testWebSocketConnectionAndJoin()` - Establishes STOMP connection
- `testConcurrentGraftMutations()` - Multiple users grafting agents simultaneously
- `testCRDTConcurrentGraftAtSamePosition()` - 3+ users grafting at same position (CRDT merge)
- `testConcurrentPruneMutations()` - Simultaneous workflow pruning
- `testConcurrentMixedMutations()` - Mixed graft/prune/flag operations
- `testHttpPollingFallback()` - HTTP polling when WebSocket unavailable
- `testWebSocketAuthenticationFailure()` - Rejects invalid JWT tokens

## Running Tests

### Run All E2E Tests
```bash
cd ai-orchestrator
mvn clean verify -Pe2e
```

### Run Specific Test Class
```bash
mvn test -Dtest=AuthenticationFlowE2ETest
```

### Run Specific Test Method
```bash
mvn test -Dtest=WebSocketCollaborationE2ETest#testCRDTConcurrentGraftAtSamePosition
```

### Run with Test Report Generation
```bash
mvn clean verify
# Reports generated in: target/e2e-test-reports/
```

## Test Reports

After test execution, reports are generated in `target/e2e-test-reports/`:

### HTML Report (`e2e-test-report.html`)
Interactive HTML report with:
- Test execution summary
- Pass/fail status for each test
- Collapsible stack traces for failures
- Screenshots for failed tests
- Timestamp and duration

### JSON Report (`e2e-test-report.json`)
Machine-readable test results:
```json
{
  "generatedAt": "2024-01-15T10:30:00Z",
  "totalTests": 50,
  "passedTests": 48,
  "failedTests": 2,
  "results": [...]
}
```

### Screenshots (`screenshots/`)
PNG screenshots captured on test failure:
- Filename format: `{TestClass}_{testMethod}_{timestamp}.png`
- Contains rendered HTML content or API response state

## WireMock Stubs

Tests use WireMock to mock external services:

### GitHub API
```java
stubFor(get(urlPathEqualTo("/user"))
    .withHeader("Authorization", equalTo("Bearer github-token"))
    .willReturn(aResponse()
        .withStatus(200)
        .withBody("{\"id\": 12345, \"login\": \"user\"}")));
```

### OAuth2 Token Exchange
```java
stubFor(post(urlPathEqualTo("/oauth/token"))
    .willReturn(aResponse()
        .withBody("{\"access_token\": \"...\", \"refresh_token\": \"...\"}")));
```

### LLM Endpoints
```java
stubFor(post(urlPathEqualTo("/chat/completions"))
    .withRequestBody(containing("You are a product manager"))
    .willReturn(aResponse()
        .withBody("{\"choices\": [...]}")));
```

## Best Practices

### 1. Test Isolation
- Each test uses `@BeforeEach` to clean database state
- WireMock is reset between tests
- WebSocket sessions are properly closed in `finally` blocks

### 2. Proper Resource Cleanup
```java
try {
    session = connectToWebSocket(client, user);
    // ... test logic
} finally {
    if (session != null && session.isConnected()) {
        session.disconnect();
    }
    client.stop();
}
```

### 3. Async Validation with Awaitility
```java
await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
    List<Event> events = repository.findAll();
    assertEquals(2, events.size());
});
```

### 4. Meaningful Assertions
```java
assertEquals(HttpStatus.OK, response.getStatusCode(),
    "MFA setup should return 200. Body: " + response.getBody());
```

## Troubleshooting

### WebSocket Connection Failures
- Ensure JWT token is valid and not expired
- Check port availability (tests use random ports)
- Verify security configuration allows WebSocket connections

### WireMock Stub Not Matching
- Use `WireMock.reset()` in `@BeforeEach`
- Verify request URL, headers, and body match stub definition
- Check WireMock logs for mismatches

### Database State Issues
- Ensure proper cleanup order (child tables before parent)
- Use `jdbcTemplate.execute("DELETE FROM ...")` in `@BeforeEach`
- Consider using `@Transactional` with rollback

### Test Timeouts
- Increase `await()` timeout for slow systems
- Check for deadlocks in concurrent tests
- Verify external service mocks are properly configured

## Contributing

When adding new E2E tests:

1. Extend `AbstractE2ETest` for base configuration
2. Add proper cleanup in `@BeforeEach` and `finally` blocks
3. Use meaningful test names (e.g., `testMfaLoginFlowWithValidCode`)
4. Add descriptive assertion messages
5. Mock all external service calls with WireMock
6. Document test purpose in class-level Javadoc

## Related Documentation

- [AGENTS.md](../../../../../AGENTS.md) - Build and test commands
- [MFA_IMPLEMENTATION.md](../../../MFA_IMPLEMENTATION.md) - MFA implementation details
- [OAUTH2_IMPLEMENTATION_SUMMARY.md](../../../OAUTH2_IMPLEMENTATION_SUMMARY.md) - OAuth2 setup
- [docs/COLLABORATION.md](../../../../../docs/COLLABORATION.md) - WebSocket collaboration architecture
- [docs/CRDT_COLLABORATION.md](../../../../../docs/CRDT_COLLABORATION.md) - CRDT implementation details
