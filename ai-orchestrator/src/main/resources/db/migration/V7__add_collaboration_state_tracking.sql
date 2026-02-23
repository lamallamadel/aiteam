-- Add state tracking columns to collaboration_events table
ALTER TABLE collaboration_events
ADD COLUMN IF NOT EXISTS state_before JSONB,
ADD COLUMN IF NOT EXISTS state_after JSONB;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_run_timestamp ON collaboration_events(run_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_run_user ON collaboration_events(run_id, user_id);
CREATE INDEX IF NOT EXISTS idx_run_event_type ON collaboration_events(run_id, event_type);

-- Add comments for documentation
COMMENT ON COLUMN collaboration_events.state_before IS 'JSON snapshot of state before the event';
COMMENT ON COLUMN collaboration_events.state_after IS 'JSON snapshot of state after the event';
