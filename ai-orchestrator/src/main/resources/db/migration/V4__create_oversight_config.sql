CREATE TABLE ai_oversight_config (
    id UUID PRIMARY KEY,
    repo VARCHAR(255),
    autonomy_level VARCHAR(50) NOT NULL DEFAULT 'SUPERVISED',
    checkpoint_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    escalation_level VARCHAR(50) NOT NULL DEFAULT 'SUPERVISED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_oversight_config_repo ON ai_oversight_config(repo);
CREATE INDEX idx_oversight_config_checkpoint ON ai_oversight_config(checkpoint_name);
