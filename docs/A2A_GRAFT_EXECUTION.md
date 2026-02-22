# A2A Graft Execution Engine

## Overview

The A2A Graft Execution Engine enables runtime injection of specialized agents at designated checkpoints in the Atlasia pipeline. Grafts allow you to "graft" additional analysis, validation, or transformation steps without modifying the core pipeline.

## Architecture

### Key Components

1. **GraftExecutionService**: Core orchestration service that manages the graft lifecycle
2. **GraftExecutionEntity**: Persistence model tracking individual graft executions
3. **GraftExecutionRepository**: JPA repository for graft execution data
4. **WorkflowEngine Integration**: Pauses after each step to execute scheduled grafts
5. **A2ADiscoveryService Integration**: Dynamic agent discovery for grafted agents

### Execution Flow

```
Pipeline Step (e.g., ARCHITECT) Completes
    ↓
WorkflowEngine calls GraftExecutionService.executeGraftsAfterCheckpoint()
    ↓
Filter pending_grafts JSON for matching checkpoint
    ↓
For each graft:
    ↓
    Check Circuit Breaker (OPEN? → Skip)
    ↓
    Discover Agent via A2ADiscoveryService
    ↓
    Invoke Agent with RunContext (includes env snapshot + all prior outputs)
    ↓
    Execute with Timeout (default 5min, configurable)
    ↓
    Retry up to 3 times on failure (with exponential backoff)
    ↓
    Capture Output as RunArtifactEntity
    ↓
    Update executed_grafts metadata
    ↓
    Emit SSE Events (GRAFT_START, GRAFT_COMPLETE, GRAFT_FAILED)
    ↓
Pipeline Resumes to Next Step
```

## Usage

### Scheduling a Graft

Grafts are scheduled by adding entries to the `pending_grafts` JSONB field on `RunEntity`:

```json
[
  {
    "graftId": "security-scan-post-architect",
    "agentName": "security-analyzer-v1",
    "after": "ARCHITECT",
    "timeoutMs": 300000
  },
  {
    "graftId": "cost-estimate",
    "agentName": "cost-estimator-v1",
    "after": "DEVELOPER",
    "timeoutMs": 60000
  }
]
```

### Checkpoints

Available checkpoints: `PM`, `QUALIFIER`, `ARCHITECT`, `DEVELOPER`, `WRITER`

## Circuit Breaker

- Failure Threshold: **5 consecutive failures**
- Reset Window: **5 minutes**
- States: CLOSED → OPEN → HALF_OPEN → CLOSED

## Retry Logic

Up to **3 retries** with exponential backoff (1s, 2s, 3s)

## Timeout Control

Default: **5 minutes (300,000 ms)**, configurable per-graft via `timeoutMs`

## Database Schema

```sql
CREATE TABLE graft_executions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    graft_id VARCHAR(100),
    agent_name VARCHAR(50),
    checkpoint_after VARCHAR(50),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20),  -- PENDING, RUNNING, COMPLETED, FAILED, TIMEOUT, CIRCUIT_OPEN
    output_artifact_id UUID,
    error_message TEXT,
    retry_count INTEGER,
    timeout_ms BIGINT
);
```

## SSE Events

- **GRAFT_START**: Graft execution begins
- **GRAFT_COMPLETE**: Graft completes successfully (includes artifactId)
- **GRAFT_FAILED**: Graft fails (includes errorType and message)

## Metrics

- `orchestrator.graft.executions.total`
- `orchestrator.graft.success.total`
- `orchestrator.graft.failure.total`
- `orchestrator.graft.timeout.total`
- `orchestrator.graft.circuit.open.total`
- `orchestrator.graft.duration`
