CREATE TABLE ai_trigger_config (
    id UUID PRIMARY KEY,
    repo VARCHAR(255) NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    config JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_trigger_config_repo_type ON ai_trigger_config(repo, trigger_type);
CREATE INDEX idx_trigger_config_enabled ON ai_trigger_config(enabled);
