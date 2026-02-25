# A2A Protocol Contract Testing Framework - Overview

## Executive Summary

This contract testing framework validates the Agent-to-Agent (A2A) protocol implementation in the Atlasia orchestrator. It ensures compatibility between the orchestrator and external agents by testing data structures, API endpoints, and protocol behaviors against predefined contracts.

## Framework Components

### 1. Contract Definitions (JSON)
Location: `src/test/resources/contracts/`

Six contract files define the expected behavior of the A2A protocol:

| Contract | Purpose | Endpoint | Method |
|----------|---------|----------|--------|
| `agent-card-schema.json` | AgentCard structure validation | `/.well-known/agent.json` | GET |
| `capability-discovery.json` | Single-capability discovery | `/api/a2a/capabilities/{capability}` | GET |
| `multi-capability-discovery.json` | Multi-capability discovery with scoring | `/api/a2a/capabilities/{capability}` | GET |
| `task-submission.json` | Task submission payloads | `/api/a2a/tasks` | POST |
| `binding-verification.json` | Binding verification responses | `/api/a2a/bindings/{id}` | GET |
| `agent-registration.json` | External agent registration | `/api/a2a/agents` | POST |

### 2. Test Classes
Location: `src/test/java/com/atlasia/ai/contract/`

| Class | Purpose | Test Type |
|-------|---------|-----------|
| `A2AContractTest` | Model structure validation | Unit |
| `A2AContractIntegrationTest` | HTTP endpoint validation | Integration |
| `ContractValidationTest` | Validation utility testing | Unit |
| `A2AContractTestBase` | Common test setup and mocks | Base class |
| `ContractValidationHelper` | Pattern matching and validation utilities | Helper |

### 3. Tested Components

**Services:**
- `A2ADiscoveryService` - Agent discovery and capability matching
- `AgentBindingService` - Agent binding creation and verification

**Models:**
- `AgentCard` - Agent metadata and capabilities
- `AgentConstraints` - Resource limits and budgets
- `AgentBinding` - Cryptographically-signed delegation records

**Controllers:**
- `A2AController` - A2A protocol HTTP endpoints

## Test Coverage

### AgentCard Schema Tests
✓ Field type validation (name, version, role, vendor, status)  
✓ Constraint validation (maxTokens, maxDurationMs, costBudgetUsd)  
✓ Capability list non-empty validation  
✓ Status enum validation (active, degraded, inactive)  
✓ Pattern matching (UUID, version, role formats)  

### Capability Discovery Tests
✓ Single capability lookup  
✓ Multi-capability discovery with scoring  
✓ Empty result handling  
✓ Status filtering (only active agents)  
✓ Best-match selection based on coverage  

### Task Submission Tests
✓ Request payload structure (repo, issueNumber, mode)  
✓ Response structure (taskId, status, createdAt)  
✓ UUID format validation  
✓ ISO-8601 timestamp validation  
✓ Authorization requirements (GitHub token)  

### Binding Verification Tests
✓ HMAC signature validation  
✓ Expiry and TTL checks  
✓ Capability matching (declared vs required)  
✓ Binding structure validation  
✓ Timestamp validation (issuedAt < expiresAt)  

### Agent Registration Tests
✓ Registration payload structure  
✓ Response structure (HTTP 201)  
✓ Authorization requirements (admin token)  
✓ Constraint validation  

## Pattern Matchers

The framework includes regex patterns for validation:

```java
UUID:       [a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}
Version:    [0-9]+\.[0-9]+(\.[0-9]+)?
Role:       [A-Z_]+
Status:     active|degraded|inactive
Repo:       [a-zA-Z0-9\-_]+/[a-zA-Z0-9\-_]+
ISO-8601:   [0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z?
AgentName:  [a-zA-Z0-9\-_]+
```

## Running Tests

### All Contract Tests
```bash
cd ai-orchestrator
mvn test -Dtest=A2AContract*
```

### Specific Test Classes
```bash
# Unit tests - model validation
mvn test -Dtest=A2AContractTest

# Integration tests - HTTP endpoints
mvn test -Dtest=A2AContractIntegrationTest

# Validation helper tests
mvn test -Dtest=ContractValidationTest
```

### With Maven Verify
```bash
# Run all tests including contracts
mvn clean verify
```

### Spring Cloud Contract Plugin
The framework uses Spring Cloud Contract for contract-first testing:

```bash
# Generate tests from contracts
mvn spring-cloud-contract:generateTests

# Convert to stubs
mvn spring-cloud-contract:convert
```

## Test Output

Tests produce detailed output including:
- Contract file being tested
- Field-by-field validation results
- Pattern matching success/failure
- HTTP status code validation
- Response body structure validation

Example output:
```
✓ AgentCard schema contract - verify structure and field types
✓ Capability discovery contract - verify response from A2ADiscoveryService.discover()
✓ Task submission contract - verify payload structure for /api/a2a/tasks
✓ Binding verification contract - verify response from AgentBindingService.verifyBinding()
✓ Multi-capability discovery contract - A2ADiscoveryService.discover() with multiple capabilities
```

## Contract Validation Flow

```
1. Load contract JSON from resources
   ↓
2. Parse request/response specifications
   ↓
3. Create test data matching contract structure
   ↓
4. Execute operation (model creation or HTTP call)
   ↓
5. Validate response against matchers
   ↓
6. Assert field values and patterns
   ↓
7. Report success/failure
```

## Integration with CI/CD

Contract tests are automatically run as part of:
- `mvn test` - All unit tests including contracts
- `mvn verify` - Full build including integration tests
- GitHub Actions - On every pull request
- Pre-commit hooks - Local validation

## Adding New Contracts

To add a new contract:

1. Create JSON contract file in `src/test/resources/contracts/`
2. Add test method in `A2AContractTest` for model validation
3. Add test method in `A2AContractIntegrationTest` for HTTP validation
4. Update `README.md` with contract documentation
5. Update this overview document

## Dependencies

The framework uses:
- **Spring Cloud Contract** 4.1.4 - Contract testing framework
- **REST-assured** - HTTP endpoint testing
- **MockMvc** - Spring MVC test support
- **JUnit 5** - Test execution
- **AssertJ** - Fluent assertions
- **Mockito** - Mocking framework

## Benefits

1. **Contract-First Development** - Define contracts before implementation
2. **Backward Compatibility** - Detect breaking changes early
3. **Documentation** - Contracts serve as living documentation
4. **External Integration** - External agents can test against contracts
5. **Confidence** - High confidence in protocol stability
6. **Automation** - Automated validation in CI/CD pipeline

## Future Enhancements

- [ ] Consumer-driven contract testing with external agents
- [ ] Contract versioning and migration tests
- [ ] Performance benchmarks for discovery operations
- [ ] Chaos testing for binding expiry scenarios
- [ ] OpenAPI/Swagger contract generation
- [ ] Pact broker integration for multi-team contracts

## References

- A2A Protocol Specification: `docs/A2A_PROTOCOL.md`
- Contract README: `src/test/resources/contracts/README.md`
- Package Documentation: `com.atlasia.ai.contract` package-info
- Spring Cloud Contract Docs: https://spring.io/projects/spring-cloud-contract
- REST-assured Docs: https://rest-assured.io/

## Support

For questions or issues with contract tests:
1. Check contract JSON syntax in `src/test/resources/contracts/`
2. Review test output for specific validation failures
3. Consult `ContractValidationHelper` for pattern matchers
4. Review existing test cases in `A2AContractTest`
5. Check Maven build logs for Spring Cloud Contract errors

## Version History

- **1.0.0** (2024) - Initial release with 6 core contracts
  - AgentCard schema validation
  - Capability discovery (single and multi)
  - Task submission
  - Binding verification
  - Agent registration
