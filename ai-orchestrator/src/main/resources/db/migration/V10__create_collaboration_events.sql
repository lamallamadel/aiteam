-- Collaboration events for multi-user workflow editing
CREATE TABLE collaboration_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_collaboration_events_run_id ON collaboration_events(run_id);
CREATE INDEX idx_collaboration_events_timestamp ON collaboration_events(timestamp);
CREATE INDEX idx_collaboration_events_user_id ON collaboration_events(user_id);
