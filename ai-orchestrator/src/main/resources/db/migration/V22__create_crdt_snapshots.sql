-- Create CRDT snapshots table for persistence and recovery
CREATE TABLE crdt_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    snapshot_data BYTEA NOT NULL,
    lamport_timestamp BIGINT NOT NULL,
    region VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_count INTEGER,
    CONSTRAINT fk_crdt_snapshot_run FOREIGN KEY (run_id) REFERENCES ai_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_crdt_snapshot_run ON crdt_snapshots(run_id, created_at DESC);
CREATE INDEX idx_crdt_snapshot_region ON crdt_snapshots(region);

COMMENT ON TABLE crdt_snapshots IS 'Periodic CRDT document snapshots for efficient recovery and synchronization';
COMMENT ON COLUMN crdt_snapshots.snapshot_data IS 'Serialized CRDT document state (Automerge binary format)';
COMMENT ON COLUMN crdt_snapshots.lamport_timestamp IS 'Logical timestamp at snapshot creation time';
COMMENT ON COLUMN crdt_snapshots.event_count IS 'Number of events merged into this snapshot';
