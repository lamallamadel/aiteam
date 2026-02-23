# Graft Management UI - User Guide

## Overview
The Graft Management UI provides a comprehensive interface for monitoring, scheduling, and managing dynamic agent grafts in the Atlasia orchestrator. It consists of three main sections: Execution History, Graft Scheduler, and Circuit Breaker Dashboard.

## Accessing the UI

### Global View
Navigate to `/grafts` to see all graft executions across all workflow runs.

### Run-Specific View
Navigate to `/grafts/:runId` to see grafts for a specific workflow run. The scheduler will be pre-populated with the run ID.

## Features

### 1. Execution History

**Purpose**: View and analyze all graft executions with detailed status and metrics.

**What You'll See:**
- **Graft ID**: Unique identifier for each graft execution (displayed as first 8 characters)
- **Agent**: Which agent was grafted (e.g., `security-scanner-v1`)
- **Checkpoint**: After which pipeline step the graft was injected (e.g., `ARCHITECT`)
- **Status**: Current execution status with color-coded badge
- **Started**: When the graft execution began
- **Duration**: How long the graft took to execute (or "—" if still running)
- **Retries**: Number of retry attempts (highlighted in orange if > 0)
- **Actions**: Buttons to view artifacts (📄) or errors (⚠️)

**Status Indicators:**
- 🟢 **COMPLETED** - Graft executed successfully
- 🔵 **RUNNING** - Currently executing
- 🔴 **FAILED** - Execution failed after retries
- 🟠 **TIMEOUT** - Exceeded timeout limit
- 🟡 **CIRCUIT_OPEN** - Blocked by circuit breaker
- ⚫ **PENDING** - Queued for execution

**Filtering:**
Use the search box to filter by:
- Run ID
- Agent name
- Graft ID
- Checkpoint name

The result count updates in real-time as you type.

### 2. Graft Scheduler

**Purpose**: Dynamically inject new grafts into running or paused workflows.

**How to Schedule a Graft:**

1. **Enter Run ID**: The UUID of the workflow run where you want to inject the graft
   - If you navigated from a specific run, this is pre-filled

2. **Select Checkpoint**: Choose where to inject the graft
   - Options: PM, QUALIFIER, ARCHITECT, DEVELOPER, REVIEW, TESTER
   - The graft executes *after* this step completes

3. **Choose Agent**: Type to search for available agents
   - Autocomplete suggestions appear as you type
   - Click a suggestion to select it
   - Examples: `security-scanner-v1`, `performance-analyzer-v1`, `judge-v1`

4. **Set Timeout** (optional): Specify timeout in milliseconds
   - Default: 300000 (5 minutes)
   - Increase for long-running agents

5. **Click "Schedule Graft"**: Submit the graft request
   - Success message appears on successful scheduling
   - Error message displays if validation fails

**Validation Rules:**
- Run ID must not be empty
- Checkpoint must be selected
- Agent name must not be empty

**Use Cases:**
- Add security scanning after architecture design
- Inject performance analysis after code generation
- Add custom validation at any pipeline stage
- Emergency debugging agent injection

### 3. Circuit Breaker Dashboard

**Purpose**: Monitor and manage circuit breaker health for all graft agents.

**What Each Card Shows:**

**Header:**
- Agent name badge
- Circuit state badge (CLOSED/OPEN/HALF_OPEN)
- Reset button (appears when circuit is not CLOSED)

**Metrics:**
- **Failure Rate**: Visual progress bar with percentage
  - Green bar: < 50% failure rate
  - Red bar: ≥ 50% failure rate
- **Executions**: Success (✓) and failure (✗) counts
- **Current Failures**: Number of consecutive failures (highlighted in red)
- **Last Failure**: Timestamp of most recent failure

**Recent Failures Section** (if any):
- Shows up to 3 most recent failures
- Each failure displays:
  - Graft ID (first 8 characters)
  - Timestamp
  - Error message (truncated to 60 characters)

**Circuit States:**
- 🟢 **CLOSED** - Normal operation, requests allowed
- 🔴 **OPEN** - Too many failures, requests blocked
- 🟠 **HALF_OPEN** - Testing if service recovered, limited requests allowed

**Manual Reset:**
1. Click the "Reset" button on an OPEN or HALF_OPEN circuit
2. Circuit immediately transitions to CLOSED
3. Failure count resets to 0
4. New graft executions will be attempted

**Circuit Breaker Behavior:**
- Opens after 5 consecutive failures within 5-minute window
- Automatically transitions to HALF_OPEN after 5 minutes
- Returns to CLOSED after successful execution in HALF_OPEN state
- Independent per agent (one agent's failures don't affect others)

## Real-Time Visualization

### Neural Trace Integration

When viewing a workflow run's neural trace (pipeline visualization), you'll see:

**Graft Count Badge** (top-right):
- Shows number of active grafts
- Example: "🔀 2 graft(s)"

**Graft Indicators on Pipeline**:
- Small "+" buttons between pipeline steps
- Turns into "🔀" icon when graft is active at that checkpoint
- Animated pulsing effect on active grafts

**Graft Status Badge** (appears above connector):
- Shows agent name
- Status icon indicates current state:
  - ⚡ Running
  - ✓ Completed
  - ✗ Failed
  - ⏱ Timeout
  - 🔌 Circuit Open
  - ⏳ Pending
- Color matches status (blue for running, green for completed, etc.)
- Slides in with animation when graft starts

## Best Practices

### When to Use Grafts
- **Security Scanning**: After architecture or code generation
- **Performance Analysis**: After code generation
- **Custom Validation**: At any checkpoint where specialized validation is needed
- **Debugging**: Inject diagnostic agents for troubleshooting

### Circuit Breaker Management
- **Monitor failure rates**: Keep an eye on agents with high failure rates
- **Investigate before reset**: Check recent failures to understand root cause
- **Reset cautiously**: Only reset if you've addressed the underlying issue
- **Use HALF_OPEN wisely**: After reset, monitor first few executions closely

### Scheduling Tips
- **Checkpoint selection**: Choose the earliest safe checkpoint (don't wait too long)
- **Timeout configuration**: Set realistic timeouts based on agent complexity
- **Agent selection**: Use autocomplete to avoid typos
- **Run verification**: Double-check run ID before scheduling

### Monitoring
- **Regular checks**: Review execution history periodically
- **Status tracking**: Watch for patterns in failures
- **Circuit health**: Keep circuits CLOSED when possible
- **Error analysis**: Click error icons to understand failure causes

## Troubleshooting

### Graft Not Executing
1. Check if run is still active
2. Verify checkpoint hasn't already passed
3. Check circuit breaker state for the agent
4. Review pending grafts list in run details

### Circuit Keeps Opening
1. Review recent failure messages
2. Check agent health endpoint
3. Verify agent configuration
4. Consider increasing timeout
5. Check A2A registry for agent status

### Autocomplete Not Working
1. Refresh the page
2. Check network connectivity
3. Verify A2A registry is populated
4. Try typing full agent name

### Scheduler Validation Fails
1. Ensure all required fields are filled
2. Check run ID format (must be valid UUID)
3. Verify agent name exists in autocomplete
4. Select checkpoint from dropdown (don't type)

## API Reference

For programmatic access, see the following endpoints:

- `GET /api/grafts/executions` - List executions
- `GET /api/grafts/executions/{id}` - Get specific execution
- `GET /api/grafts/circuit-breaker/status` - Get circuit breaker statuses
- `POST /api/grafts/circuit-breaker/{agentName}/reset` - Reset circuit
- `GET /api/grafts/agents` - List available agents
- `POST /api/runs/{runId}/grafts` - Schedule new graft

All endpoints require Bearer token authentication.

## Keyboard Shortcuts

- **Tab**: Navigate between form fields
- **Enter**: Submit scheduler form (when all fields valid)
- **Escape**: Close autocomplete suggestions
- **Arrow Up/Down**: Navigate autocomplete suggestions

## Support

For additional help:
- See `GRAFT_MANAGEMENT_IMPLEMENTATION.md` for technical details
- Check `docs/COLLABORATION.md` for workflow integration
- Review `AGENTS.md` for agent configuration
