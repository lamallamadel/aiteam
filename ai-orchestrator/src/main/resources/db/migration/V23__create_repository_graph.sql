CREATE TABLE repository_graph (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_url VARCHAR(500) NOT NULL UNIQUE,
    dependencies JSONB NOT NULL DEFAULT '[]'::jsonb,
    workspace_type VARCHAR(50),
    workspace_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repository_graph_repo_url ON repository_graph(repo_url);
CREATE INDEX idx_repository_graph_workspace_type ON repository_graph(workspace_type);

COMMENT ON TABLE repository_graph IS 'Cross-repository dependency graph for multi-repo workflow orchestration';
COMMENT ON COLUMN repository_graph.repo_url IS 'Full repository URL (e.g., github.com/owner/repo)';
COMMENT ON COLUMN repository_graph.dependencies IS 'JSON array of upstream repository URLs this repo depends on';
COMMENT ON COLUMN repository_graph.workspace_type IS 'Monorepo workspace type: maven_modules, npm_workspaces, or null for single-repo';
COMMENT ON COLUMN repository_graph.workspace_config IS 'JSON config for monorepo workspaces: {modules: [...], root: "path"}';
