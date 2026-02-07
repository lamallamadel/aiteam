CREATE TABLE ai_run (
    id UUID PRIMARY KEY,
    repo VARCHAR(255) NOT NULL,
    issue_number INTEGER NOT NULL,
    mode VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_agent VARCHAR(100),
    ci_fix_count INTEGER NOT NULL DEFAULT 0,
    e2e_fix_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_run_repo_issue ON ai_run(repo, issue_number);
CREATE INDEX idx_ai_run_status ON ai_run(status);
CREATE INDEX idx_ai_run_created_at ON ai_run(created_at);
