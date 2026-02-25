# Multi-Repository Workflow Orchestration

## Overview

The Multi-Repository Workflow Orchestration system enables coordinated execution of AI workflows across multiple repositories with dependency-aware scheduling. This is essential for microservices architectures, monorepos, and cross-repo dependency chains.

## Key Features

### 1. Dependency-Aware Scheduling
- **Topological Sort**: Automatically determines execution order based on repository dependencies
- **Cyclic Dependency Detection**: Prevents invalid dependency graphs
- **Execution Plans**: Pre-compute and visualize execution order before running workflows

### 2. Cross-Repository Graft Execution
- **Trigger Downstream Workflows**: Security scan in repo-A triggers documentation update graft in repo-B
- **Context Propagation**: Pass analysis results and context data between repositories
- **A2A Task Submission**: Use Agent-to-Agent protocol for cross-repo task coordination

### 3. Monorepo Workspace Detection
- **Maven Multi-Module**: Auto-detect `pom.xml` modules
- **NPM Workspaces**: Auto-detect `package.json` workspaces
- **Package Manager Detection**: Identify yarn, pnpm, or npm

### 4. Coordinated Multi-PR Creation
- **Merge Ordering**: Create PRs with metadata indicating merge order based on dependency graph
- **Dependency Metadata**: Embed upstream/downstream dependency info in PR descriptions
- **Atomic Merges**: Merge infrastructure PRs before application PRs

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  MultiRepoOrchestrationService              │
├─────────────────────────────────────────────────────────────┤
│  - executeMultiRepoWorkflow()                               │
│  - createCoordinatedPullRequests()                          │
│  - mergeCoordinatedPullRequests()                           │
└────────────┬────────────────────────────┬───────────────────┘
             │                            │
             ▼                            ▼
┌────────────────────────┐  ┌─────────────────────────────────┐
│   MultiRepoScheduler   │  │   GraftExecutionService         │
├────────────────────────┤  ├─────────────────────────────────┤
│ - topologicalSort()    │  │ - executeCrossRepoGraft()       │
│ - buildDependencyGraph│  │ - executeGraftsAfterCheckpoint()│
│ - computeExecutionOrder│  └─────────────────────────────────┘
└────────────────────────┘
             │
             ▼
┌────────────────────────┐  ┌─────────────────────────────────┐
│ MonorepoWorkspace      │  │   GitHubApiClient               │
│ Detector               │  ├─────────────────────────────────┤
├────────────────────────┤  │ - createCoordinatedPullRequests│
│ - detectMavenModules() │  │ - mergeCoordinatedPullRequests  │
│ - detectNpmWorkspaces()│  │ - createPullRequestWithMetadata │
└────────────────────────┘  └─────────────────────────────────┘
```

## Database Schema

### `repository_graph` Table

```sql
CREATE TABLE repository_graph (
    id UUID PRIMARY KEY,
    repo_url VARCHAR(500) UNIQUE NOT NULL,
    dependencies JSONB NOT NULL DEFAULT '[]',
    workspace_type VARCHAR(50),
    workspace_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

**Fields:**
- `repo_url`: Normalized repository URL (e.g., `github.com/owner/repo`)
- `dependencies`: JSON array of upstream repository URLs
- `workspace_type`: `maven_modules`, `npm_workspaces`, or `null`
- `workspace_config`: JSON config with modules/workspaces list

## API Endpoints

### Register Repository Dependencies

```http
POST /api/multi-repo/repositories/register
Authorization: Bearer <token>
Content-Type: application/json

{
  "repoUrl": "github.com/myorg/infra",
  "dependencies": [
    "github.com/myorg/shared-config"
  ]
}
```

### Detect Monorepo Workspaces

```http
POST /api/multi-repo/workspaces/detect
Authorization: Bearer <token>
Content-Type: application/json

{
  "repoUrls": [
    "github.com/myorg/monorepo-backend",
    "github.com/myorg/monorepo-frontend"
  ]
}
```

**Response:**
```json
{
  "success": true,
  "workspaces": [
    {
      "repoUrl": "github.com/myorg/monorepo-backend",
      "workspaceType": "maven_modules",
      "workspaceConfig": "{\"modules\":[\"api\",\"service\",\"common\"],\"root\":\".\",\"buildFile\":\"pom.xml\"}"
    }
  ]
}
```

### Execute Multi-Repo Workflow

```http
POST /api/multi-repo/workflows/execute
Authorization: Bearer <token>
Content-Type: application/json

{
  "sourceRunId": "123e4567-e89b-12d3-a456-426614174000",
  "sourceRepoUrl": "github.com/myorg/security-infra",
  "targetRepoUrls": [
    "github.com/myorg/app-backend",
    "github.com/myorg/app-frontend",
    "github.com/myorg/docs"
  ],
  "agentName": "security-scanner-v1",
  "checkpoint": "ARCHITECT",
  "contextData": {
    "securityFindings": "Critical vulnerability detected in JWT library",
    "recommendedAction": "Upgrade to version 2.1.3"
  }
}
```

**Response:**
```json
{
  "allSuccess": true,
  "executionOrder": [
    "github.com/myorg/app-backend",
    "github.com/myorg/app-frontend",
    "github.com/myorg/docs"
  ],
  "graftResults": {
    "github.com/myorg/app-backend": {
      "success": true,
      "targetRunId": "456e7890-e89b-12d3-a456-426614174001",
      "artifactId": "789e0123-e89b-12d3-a456-426614174002",
      "errorMessage": null
    }
  },
  "errors": []
}
```

### Create Coordinated Pull Requests

```http
POST /api/multi-repo/pull-requests/create
Authorization: Bearer <token>
Content-Type: application/json

{
  "pullRequests": {
    "github.com/myorg/infra": {
      "title": "Upgrade JWT library to v2.1.3",
      "head": "security-jwt-upgrade",
      "base": "main",
      "body": "Security upgrade addressing CVE-2024-1234",
      "metadata": {
        "security": "critical",
        "coordinated": "true"
      }
    },
    "github.com/myorg/app-backend": {
      "title": "Update JWT usage after infra upgrade",
      "head": "jwt-api-update",
      "base": "main",
      "body": "Adapts to new JWT library interface",
      "metadata": {
        "coordinated": "true"
      }
    }
  }
}
```

**Response:**
```json
{
  "allSuccess": true,
  "prNumbers": {
    "github.com/myorg/infra": 42,
    "github.com/myorg/app-backend": 43
  },
  "mergeOrder": [
    "github.com/myorg/infra",
    "github.com/myorg/app-backend"
  ],
  "errors": []
}
```

### Merge Coordinated Pull Requests

```http
POST /api/multi-repo/pull-requests/merge
Authorization: Bearer <token>
Content-Type: application/json

{
  "repoPrMapping": {
    "github.com/myorg/infra": 42,
    "github.com/myorg/app-backend": 43
  },
  "mergeMethod": "squash"
}
```

**Merge Methods:**
- `merge`: Standard merge commit
- `squash`: Squash and merge
- `rebase`: Rebase and merge

### Compute Execution Plan

```http
POST /api/multi-repo/execution-plan
Authorization: Bearer <token>
Content-Type: application/json

{
  "repoUrls": [
    "github.com/myorg/infra",
    "github.com/myorg/app-backend",
    "github.com/myorg/app-frontend"
  ]
}
```

**Response:**
```json
{
  "success": true,
  "executionOrder": [
    "github.com/myorg/infra",
    "github.com/myorg/app-backend",
    "github.com/myorg/app-frontend"
  ],
  "dependencies": {
    "github.com/myorg/infra": [],
    "github.com/myorg/app-backend": ["github.com/myorg/infra"],
    "github.com/myorg/app-frontend": ["github.com/myorg/infra"]
  }
}
```

## Usage Examples

### Example 1: Security Scan Triggers Documentation Update

```java
// In security scan agent (repo-A)
MultiRepoOrchestrationService.MultiRepoWorkflowRequest request = 
    new MultiRepoOrchestrationService.MultiRepoWorkflowRequest(
        currentRunId,
        "github.com/myorg/security-infra",
        List.of("github.com/myorg/docs"),
        "documentation-updater-v1",
        "DEVELOPER",
        Map.of(
            "securityFindings", scanResults,
            "affectedModules", affectedComponents
        )
    );

MultiRepoWorkflowResult result = orchestrationService.executeMultiRepoWorkflow(request);
```

### Example 2: Monorepo Workspace Detection

```java
// Detect Maven modules
List<String> repos = List.of(
    "github.com/myorg/backend-monorepo",
    "github.com/myorg/frontend-monorepo"
);

orchestrationService.scanAndRegisterWorkspaces(repos);

// Query detected workspaces
RepositoryGraphEntity backend = scheduler.getRepositoryGraph(
    "github.com/myorg/backend-monorepo"
).get();

if ("maven_modules".equals(backend.getWorkspaceType())) {
    // Parse modules from workspaceConfig
    JsonNode config = objectMapper.readTree(backend.getWorkspaceConfig());
    List<String> modules = config.get("modules"); // ["api", "service", "common"]
}
```

### Example 3: Coordinated Multi-PR with Merge Ordering

```java
// Step 1: Register dependency graph
scheduler.registerRepository("github.com/myorg/infra", List.of());
scheduler.registerRepository("github.com/myorg/app", 
    List.of("github.com/myorg/infra"));

// Step 2: Create coordinated PRs
Map<String, PRCreationInfo> prInfo = Map.of(
    "github.com/myorg/infra", new PRCreationInfo(
        "Upgrade shared library",
        "lib-upgrade",
        "main",
        "Breaking change in v2.0",
        Map.of()
    ),
    "github.com/myorg/app", new PRCreationInfo(
        "Adapt to new library interface",
        "lib-integration",
        "main",
        "Updates API calls for v2.0",
        Map.of()
    )
);

CoordinatedPRCreationResult result = 
    orchestrationService.createCoordinatedPullRequests(prInfo);

// Step 3: Merge in correct order
orchestrationService.mergeCoordinatedPullRequests(
    result.prNumbers(),
    "merge"
);
```

## Topological Sort Algorithm

The dependency-aware scheduler uses **Kahn's algorithm** for topological sorting:

```
1. Compute in-degree for each repository (count of dependencies)
2. Enqueue all repositories with in-degree 0 (no dependencies)
3. While queue is not empty:
   a. Dequeue repository and add to execution order
   b. For each dependent of current repository:
      - Decrement its in-degree
      - If in-degree becomes 0, enqueue it
4. If not all repositories processed, cyclic dependency detected
```

**Time Complexity**: O(V + E) where V = repositories, E = dependencies

## Error Handling

### Cyclic Dependency Detection

```json
{
  "success": false,
  "executionOrder": [],
  "dependencies": {},
  "error": "Cyclic dependency detected involving: [github.com/myorg/repo-a, github.com/myorg/repo-b]"
}
```

**Resolution**: Review dependency declarations and break the cycle.

### Merge Conflicts

```json
{
  "allSuccess": false,
  "mergeResults": {
    "github.com/myorg/infra": true,
    "github.com/myorg/app": false
  },
  "errors": [
    "PR #43 has merge conflicts"
  ]
}
```

**Resolution**: Resolve conflicts manually or rebase branches.

## Best Practices

### 1. Define Clear Dependency Boundaries
- Keep dependency graph acyclic
- Prefer unidirectional dependencies (upstream → downstream)
- Document inter-repo contracts

### 2. Use Semantic Versioning
- Tag releases in upstream repos
- Pin dependencies to specific versions
- Use dependency ranges cautiously

### 3. Coordinate Breaking Changes
- Use coordinated PRs for breaking changes
- Merge order: infra → libraries → applications → docs
- Test downstream compatibility before merging upstream

### 4. Monitor Execution Health
- Check circuit breaker status for cross-repo grafts
- Review execution plans before running workflows
- Set appropriate timeouts for long-running operations

### 5. Workspace Organization
- Use monorepo workspaces for tightly coupled modules
- Separate repositories for independently deployable services
- Keep shared configuration in dedicated repo

## Metrics and Observability

All multi-repo operations emit the following metrics:

- `atlasia.multirepo.workflow.executions`: Counter of workflow executions
- `atlasia.multirepo.workflow.duration`: Histogram of execution time
- `atlasia.multirepo.pr.created`: Counter of PRs created
- `atlasia.multirepo.pr.merged`: Counter of PRs merged
- `atlasia.multirepo.cyclic_dependency_detected`: Counter of cyclic dependency errors

## Security Considerations

### Access Control
- Repository registration requires `WORKFLOW_MANAGER` role
- Workflow execution requires appropriate GitHub token permissions
- Cross-repo operations inherit source repository permissions

### Secret Management
- Never embed secrets in dependency metadata
- Use Vault for cross-repo secret sharing
- Rotate tokens periodically

### Audit Trail
- All multi-repo operations logged in audit trail
- Track which user/service initiated cross-repo workflows
- Monitor for unauthorized dependency modifications

## Limitations

1. **GitHub-Only**: Currently supports GitHub repositories only
2. **No GitLab/Bitbucket**: Other Git platforms not yet supported
3. **Linear Dependencies**: Complex DAG structures may have performance impact
4. **Workspace Detection**: Limited to Maven and NPM ecosystems

## Future Enhancements

- [ ] Support for GitLab and Bitbucket
- [ ] Parallel execution of independent repositories
- [ ] Rollback coordination for failed multi-repo workflows
- [ ] Integration with CI/CD pipelines (GitHub Actions, Jenkins)
- [ ] Visual dependency graph UI
- [ ] Automated dependency bump propagation
