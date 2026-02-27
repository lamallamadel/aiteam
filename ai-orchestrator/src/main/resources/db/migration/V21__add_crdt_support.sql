-- Add CRDT support columns to collaboration_events table
ALTER TABLE collaboration_events ADD COLUMN crdt_changes BYTEA;
ALTER TABLE collaboration_events ADD COLUMN source_region VARCHAR(50);
ALTER TABLE collaboration_events ADD COLUMN lamport_timestamp BIGINT;

-- Create index for lamport timestamp ordering
CREATE INDEX idx_collaboration_events_lamport ON collaboration_events(run_id, lamport_timestamp);

-- Create index for source region filtering
CREATE INDEX idx_collaboration_events_region ON collaboration_events(run_id, source_region);

COMMENT ON COLUMN collaboration_events.crdt_changes IS 'Binary CRDT change log for conflict-free merge';
COMMENT ON COLUMN collaboration_events.source_region IS 'Source region/orchestrator instance for multi-region deployment';
COMMENT ON COLUMN collaboration_events.lamport_timestamp IS 'Logical Lamport clock timestamp for causal ordering';
