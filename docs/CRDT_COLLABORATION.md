# CRDT-Based Distributed Collaboration

## Overview

The Atlasia AI Orchestrator now uses **Conflict-free Replicated Data Types (CRDTs)** powered by Automerge for distributed state synchronization across multi-region deployments. This replaces the previous operational transformation (OT) approach with a mathematically proven convergent system.

## Architecture

### CRDT Document Model

Each workflow run maintains a CRDT document containing:
- **Grafts**: List-based CRDT for insertion operations (agents grafted into workflow)
- **Pruned Steps**: Set-based CRDT for removal operations (steps marked as pruned)
- **Flags**: Map-based CRDT for key-value metadata

### Components

1. **CrdtDocumentManager** (`CrdtDocumentManager.java`)
   - Manages Automerge documents per run
   - Applies mutations via `Automerge.change()`
   - Generates binary change logs
   - Merges changes from remote regions

2. **CrdtSyncService** (`CrdtSyncService.java`)
   - Handles peer-to-peer synchronization
   - Broadcasts changes via WebSocket mesh
   - Implements eventual consistency
   - Manages regional peer connections

3. **CrdtSnapshotService** (`CrdtSnapshotService.java`)
   - Creates periodic snapshots every 100 events
   - Stores snapshots in PostgreSQL
   - Enables fast recovery on session join
   - Retains last 10 snapshots per run

4. **CrdtMeshConfig** (`CrdtMeshConfig.java`)
   - Establishes WebSocket mesh topology
   - Auto-reconnects to peers
   - Monitors connection health
   - Routes CRDT sync messages

## Multi-Region Deployment

### Configuration

Configure peers via environment variables:

```bash
export CRDT_MESH_PEERS="wss://us-west.atlasia.ai/ws,wss://eu-central.atlasia.ai/ws,wss://ap-southeast.atlasia.ai/ws"
export REGION="us-east-1"
```

Or in `application.yml`:

```yaml
crdt:
  mesh:
    peers: wss://us-west.atlasia.ai/ws,wss://eu-central.atlasia.ai/ws
  region: us-east-1
```

### Sync Protocol

1. **Incremental Sync**: Each mutation generates a binary change log that is broadcast to all peers
2. **Full Sync**: On peer connection, full document state is transmitted for initial synchronization
3. **Causal Ordering**: Lamport logical clocks ensure causally correct event ordering
4. **Partition Tolerance**: CRDT properties guarantee AP (Availability + Partition Tolerance) per CAP theorem

## Conflict-Free Concurrent Editing

### Example: Simultaneous Grafts

**Scenario**: Two users in different regions graft agents at the same position simultaneously.

**Previous OT Approach** (LWW - Last Write Wins):
```
User A (US): Graft "SecurityAgent" after "Step1"
User B (EU): Graft "PerformanceAgent" after "Step1"
Result: Only one graft survives (non-deterministic)
```

**CRDT Approach** (Conflict-Free Merge):
```
User A (US): Graft "SecurityAgent" after "Step1" → CRDT Change [A1]
User B (EU): Graft "PerformanceAgent" after "Step1" → CRDT Change [B1]

Merge: Both grafts preserved with deterministic ordering based on:
  - Lamport timestamp
  - Region ID
  - User ID

Final State (converged on all regions):
  - "SecurityAgent" (lamport: 42, region: us-east-1)
  - "PerformanceAgent" (lamport: 42, region: eu-central-1)
```

## Database Schema

### `collaboration_events` Table

Extended with CRDT support:

```sql
ALTER TABLE collaboration_events 
ADD COLUMN crdt_changes BYTEA,           -- Binary Automerge change log
ADD COLUMN source_region VARCHAR(50),    -- Originating region
ADD COLUMN lamport_timestamp BIGINT;     -- Logical clock
```

### `crdt_snapshots` Table

New table for snapshot persistence:

```sql
CREATE TABLE crdt_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    snapshot_data BYTEA NOT NULL,        -- Full CRDT document state
    lamport_timestamp BIGINT NOT NULL,
    region VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_count INTEGER
);
```

## API Endpoints

### Get CRDT State
```
GET /api/crdt/runs/{runId}/state
```

Response:
```json
{
  "runId": "123e4567-e89b-12d3-a456-426614174000",
  "grafts": [...],
  "prunedSteps": [...],
  "flags": {...},
  "region": "us-east-1"
}
```

### Sync Changes
```
POST /api/crdt/runs/{runId}/sync
Content-Type: application/json

{
  "changes": "base64-encoded-binary-changes",
  "sourceRegion": "eu-central-1",
  "lamportTimestamp": 42
}
```

### Register Peer
```
POST /api/crdt/runs/{runId}/register-peer
Content-Type: application/json

{
  "peerRegion": "ap-southeast-1"
}
```

### Mesh Status
```
GET /api/crdt/mesh/status
```

## WebSocket Messages

### CRDT Sync Message
```json
{
  "type": "CRDT_SYNC",
  "runId": "123e4567-e89b-12d3-a456-426614174000",
  "sourceRegion": "us-east-1",
  "changes": "base64-encoded-binary-changes",
  "timestamp": 1234567890
}
```

### CRDT Full Sync Message
```json
{
  "type": "CRDT_FULL_SYNC",
  "runId": "123e4567-e89b-12d3-a456-426614174000",
  "sourceRegion": "us-east-1",
  "state": "base64-encoded-full-state",
  "timestamp": 1234567890
}
```

## Partition Tolerance

When network partitions occur:

1. **Each region continues accepting mutations** independently
2. **No coordination required** between regions during partition
3. **Automatic merge on reconnection** using CRDT convergence properties
4. **No data loss** - all operations preserved
5. **Deterministic convergence** - all regions reach same final state

## Performance Characteristics

- **Write Latency**: O(1) for local writes (no consensus)
- **Sync Latency**: Regional round-trip time only
- **Memory**: O(n) where n = number of operations
- **Snapshot Recovery**: O(1) snapshot load + O(k) incremental changes
- **Convergence**: Guaranteed eventual consistency

## Migration from OT

The system gracefully handles:
1. Existing runs continue with OT-based events
2. New mutations automatically use CRDT
3. Hybrid replay: OT events → CRDT state reconstruction
4. No downtime required for migration

## Monitoring

Key metrics exposed via `/actuator/prometheus`:

- `crdt_sync_messages_total`: Total sync messages sent
- `crdt_sync_failures_total`: Failed sync attempts
- `crdt_snapshot_size_bytes`: Size of CRDT snapshots
- `crdt_mesh_peers_connected`: Number of connected peers
- `crdt_lamport_clock`: Current Lamport timestamp per run

## References

- [Automerge CRDT Library](https://automerge.org/)
- [CAP Theorem](https://en.wikipedia.org/wiki/CAP_theorem)
- [Lamport Logical Clocks](https://en.wikipedia.org/wiki/Lamport_timestamp)
- [Conflict-free Replicated Data Types](https://crdt.tech/)
