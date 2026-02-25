CREATE TABLE persisted_collaboration_messages (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message_data JSONB,
    timestamp TIMESTAMP NOT NULL,
    sequence_number BIGINT NOT NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_run FOREIGN KEY (run_id) REFERENCES ai_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_run_id_timestamp ON persisted_collaboration_messages(run_id, timestamp DESC);
CREATE INDEX idx_run_id_sequence ON persisted_collaboration_messages(run_id, sequence_number DESC);
CREATE INDEX idx_critical_messages ON persisted_collaboration_messages(run_id, is_critical);
CREATE INDEX idx_sequence_after ON persisted_collaboration_messages(run_id, sequence_number);
CREATE INDEX idx_event_type ON persisted_collaboration_messages(run_id, event_type);

COMMENT ON TABLE persisted_collaboration_messages IS 'Stores critical collaboration events for late-joiners and replay functionality';
COMMENT ON COLUMN persisted_collaboration_messages.sequence_number IS 'Monotonically increasing sequence number per run for ordered replay';
COMMENT ON COLUMN persisted_collaboration_messages.is_critical IS 'Critical events (GRAFT, PRUNE, FLAG) are always persisted';
COMMENT ON INDEX idx_sequence_after IS 'Optimizes queries for fetching messages after a specific sequence number';
COMMENT ON INDEX idx_critical_messages IS 'Optimizes queries for fetching only critical messages';
