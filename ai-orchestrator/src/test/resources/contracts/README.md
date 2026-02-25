# A2A Protocol Contract Definitions

This directory contains contract definitions for the Agent-to-Agent (A2A) protocol used by the Atlasia orchestrator.

## Overview

The A2A protocol enables agent discovery, capability matching, task delegation, and binding verification between the orchestrator and external agents. These contracts ensure compatibility and consistency across agent implementations.

## Contract Files

### 1. `agent-card-schema.json`
**Purpose**: Defines the structure and validation rules for AgentCard objects.

**Tests**: 
- AgentCard model field types and structure
- Constraint validation (maxTokens, maxDurationMs, costBudgetUsd)
- Status values (active, degraded, inactive)
- Capability list structure

**Endpoint**: `GET /.well-known/agent.json`

**Key Fields**:
- `name`: Agent identifier (alphanumeric with hyphens/underscores)
- `version`: Semantic version string
- `role`: Agent role in uppercase (PM, DEVELOPER, REVIEW, etc.)
- `capabilities`: Array of capability strings
- `constraints`: Resource limits and budget constraints
- `status`: Agent health status

### 2. `capability-discovery.json`
**Purpose**: Validates capability-based agent discovery responses.

**Tests**:
- `A2ADiscoveryService.discover(Set<String> capabilities)` return structure
- Agent matching based on capabilities
- List of matching agents with full card details

**Endpoint**: `GET /api/a2a/capabilities/{capability}`

**Scenario**: Request agents with `code_generation` capability, expect list of matching AgentCards.

### 3. `task-submission.json`
**Purpose**: Defines the contract for task submission payloads and responses.

**Tests**:
- Request payload structure to `/api/a2a/tasks`
- Response structure including taskId, status, and timestamps
- UUID format validation
- Timestamp ISO-8601 format validation

**Endpoint**: `POST /api/a2a/tasks`

**Request Fields**:
- `repo`: Repository in format "owner/repo"
- `issueNumber`: Integer issue number
- `mode`: "code" or "chat"

**Response Fields**:
- `taskId`: UUID of created task
- `status`: "submitted"
- `repo`, `issueNumber`: Echo from request
- `createdAt`: ISO-8601 timestamp

### 4. `binding-verification.json`
**Purpose**: Validates binding verification responses from the orchestrator.

**Tests**:
- `AgentBindingService.verifyBinding(AgentBinding)` response structure
- HMAC signature validation
- Binding expiry and TTL validation
- Capability matching between declared and required

**Endpoint**: `GET /api/a2a/bindings/{bindingId}`

**Key Fields**:
- `binding`: Full AgentBinding object with signature
- `valid`: Boolean verification status
- `issuedAt`, `expiresAt`: Binding lifecycle timestamps
- `declaredCapabilities`, `requiredCapabilities`: Capability sets

### 5. `multi-capability-discovery.json`
**Purpose**: Validates multi-capability agent discovery with scoring.

**Tests**:
- `A2ADiscoveryService.discover(Set<String>)` with multiple required capabilities
- Agent scoring based on capability coverage
- Best-match selection from multiple candidates

**Endpoint**: `POST /api/a2a/discover` (conceptual - uses GET /api/a2a/capabilities)

**Scenario**: Request agents with multiple capabilities (`code_generation`, `security_review`, `code_quality`), expect sorted list by match score.

### 6. `agent-registration.json`
**Purpose**: Validates external agent registration via A2A protocol.

**Tests**:
- Agent registration request structure
- Response echoes registered agent card
- Admin authorization requirement
- Constraint validation

**Endpoint**: `POST /api/a2a/agents`

**Request Fields**:
- Full AgentCard structure with all required fields
- Admin bearer token required

**Response**: HTTP 201 with created AgentCard

## Contract Structure

Each contract JSON file follows this structure:

```json
{
  "description": "Human-readable contract description",
  "request": {
    "method": "HTTP method",
    "url": "Endpoint path",
    "headers": { "Header-Name": "value" },
    "body": { "field": "value" },
    "matchers": {
      "body": [
        {
          "path": "$.field",
          "type": "by_regex|by_type|by_equality",
          "value": "pattern or exact value"
        }
      ]
    }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": { "expected": "structure" },
    "matchers": {
      "body": [
        {
          "path": "$.field",
          "type": "by_regex|by_type|by_equality",
          "value": "pattern",
          "minOccurrence": 0
        }
      ]
    }
  }
}
```

## Matcher Types

- **by_regex**: Field must match regex pattern
- **by_type**: Field must be of specified type (used with minOccurrence for arrays)
- **by_equality**: Field must exactly match value

## Running Contract Tests

### Unit Tests
```bash
cd ai-orchestrator
mvn test -Dtest=A2AContractTest
```

Tests the contract definitions against model classes in isolation.

### Integration Tests
```bash
cd ai-orchestrator
mvn test -Dtest=A2AContractIntegrationTest
```

Tests actual HTTP endpoints using MockMvc with REST-assured.

### All Contract Tests
```bash
cd ai-orchestrator
mvn test -Dtest=A2AContract*
```

## Test Implementation

Contract tests are implemented in:
- `com.atlasia.ai.contract.A2AContractTest` - Unit tests validating models against contracts
- `com.atlasia.ai.contract.A2AContractIntegrationTest` - HTTP endpoint integration tests
- `com.atlasia.ai.contract.A2AContractTestBase` - Base class with common setup

## Adding New Contracts

1. Create a new JSON file in this directory following the structure above
2. Add test methods in `A2AContractTest` to validate model structure
3. Add test methods in `A2AContractIntegrationTest` to validate HTTP behavior
4. Update this README with contract documentation

## Contract Versioning

Contracts follow semantic versioning:
- **Major**: Breaking changes to request/response structure
- **Minor**: Additive changes (new optional fields)
- **Patch**: Documentation or matcher improvements

Current version: `1.0.0`

## External Agent Integration

External agents implementing the A2A protocol should:
1. Validate their responses against these contracts
2. Use the matcher patterns for dynamic field validation
3. Ensure backward compatibility when contracts are updated
4. Register with the orchestrator using `POST /api/a2a/agents`

## References

- A2A Protocol Specification: See `docs/A2A_PROTOCOL.md`
- AgentCard Model: `com.atlasia.ai.service.A2ADiscoveryService.AgentCard`
- AgentBinding Model: `com.atlasia.ai.service.AgentBindingService.AgentBinding`
- Discovery Service: `com.atlasia.ai.service.A2ADiscoveryService`
- Binding Service: `com.atlasia.ai.service.AgentBindingService`
