CREATE TABLE IF NOT EXISTS graft_executions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    graft_id VARCHAR(100) NOT NULL,
    agent_name VARCHAR(50) NOT NULL,
    checkpoint_after VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    output_artifact_id UUID REFERENCES ai_run_artifact(id),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    timeout_ms BIGINT DEFAULT 300000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_graft_executions_run_id ON graft_executions(run_id);
CREATE INDEX idx_graft_executions_status ON graft_executions(status);
CREATE INDEX idx_graft_executions_agent_name ON graft_executions(agent_name);
CREATE INDEX idx_graft_executions_checkpoint_after ON graft_executions(checkpoint_after);

ALTER TABLE ai_run ADD COLUMN IF NOT EXISTS executed_grafts JSONB DEFAULT '[]'::jsonb;
