CREATE TABLE ai_trace_event (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    parent_event_id UUID,
    event_type VARCHAR(50) NOT NULL,
    agent_name VARCHAR(100),
    label VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    tokens_used INTEGER,
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_trace_event_run FOREIGN KEY (run_id) REFERENCES ai_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_trace_event_parent FOREIGN KEY (parent_event_id) REFERENCES ai_trace_event(id) ON DELETE SET NULL
);

CREATE INDEX idx_trace_event_run_id ON ai_trace_event(run_id);
CREATE INDEX idx_trace_event_parent ON ai_trace_event(parent_event_id);
CREATE INDEX idx_trace_event_type ON ai_trace_event(event_type);
CREATE INDEX idx_trace_event_start_time ON ai_trace_event(start_time);
