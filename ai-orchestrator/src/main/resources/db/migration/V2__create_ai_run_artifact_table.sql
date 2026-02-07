CREATE TABLE ai_run_artifact (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    artifact_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_run_artifact_run FOREIGN KEY (run_id) REFERENCES ai_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_run_artifact_run_id ON ai_run_artifact(run_id);
CREATE INDEX idx_ai_run_artifact_agent_name ON ai_run_artifact(agent_name);
CREATE INDEX idx_ai_run_artifact_created_at ON ai_run_artifact(created_at);
