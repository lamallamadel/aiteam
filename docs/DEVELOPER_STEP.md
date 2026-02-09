# DeveloperStep Implementation

## Overview

The `DeveloperStep` is a production-ready agent step that generates and applies code changes to GitHub repositories using LLM-based code generation. It integrates with OpenAI-compatible LLM services to generate multi-file code changes, validates them against security policies, and creates pull requests with proper attribution.

## Key Features

### 1. LLM Integration
- **Code Generation**: Uses LLM with structured output (JSON schema) to generate complete code implementations
- **Retry Logic**: Implements Reactor-based exponential backoff retry mechanism (`Retry.backoff(3, 1s)`) for transient network errors (e.g. "Connection reset") and 5xx server responses.
- **Network Stability**: Leverages tuned Netty `HttpClient` with proactive connection eviction to prevent use of stale sockets.
- **Fallback Mode**: If LLM fails completely, generates a fallback documentation file with implementation plan
- **Context-Aware**: Provides comprehensive repository context and architecture notes to LLM

### 2. Multi-File Changes
- **Tree Creation API**: Uses GitHub's tree creation API for atomic multi-file commits
- **Operations Supported**: Create, modify, and delete files in a single commit
- **Blob Management**: Creates blobs for each file, handles encoding properly
- **Efficient**: Avoids multiple individual commits for multiple file changes

### 3. Conflict Detection and Resolution
- **Branch Existence Check**: Detects if the branch already exists
- **Conflict Analysis**: Compares existing branch with main to detect conflicts
- **Automatic Resolution**: Force updates branch if it's behind main
- **Logging**: Comprehensive logging of conflict resolution actions

### 4. Security Validation

#### File Path Validation
- **Path Traversal Protection**: Rejects paths containing `..`, `/`, or `\` at the start
- **Null Byte Protection**: Rejects paths with null bytes, newlines, or carriage returns
- **Length Limits**: Maximum path length of 4096 characters
- **Double Slash Detection**: Prevents paths with double slashes

#### Allowlist Enforcement
- **Configurable**: Uses `atlasia.orchestrator.repo-allowlist` property
- **Prefix-Based**: Only allows files within specified prefixes (e.g., `src/,docs/,pom.xml`)
- **Strict Enforcement**: Throws exception for any file not in allowlist

#### Workflow Protection
- **Protected Paths**: Prevents modifications to workflow files (configurable via `workflow-protect-prefix`)
- **Default**: Protects `.github/workflows/` directory
- **Immutable Workflows**: Ensures CI/CD pipelines cannot be tampered with

#### Content Security
- **Private Key Detection**: Rejects files containing private keys (PEM format)
- **Secret Detection**: Warns about potential hardcoded secrets (API keys, tokens, passwords)
- **Size Limits**: Maximum file size of 1MB per file
- **File Count Limits**: Maximum 100 files per commit
- **Dangerous Script Detection**: Warns about potentially dangerous shell scripts

### 5. Commit Attribution
- **Proper Author**: Sets author to "Atlasia AI Bot" with email `ai-bot@atlasia.io`
- **Proper Committer**: Sets committer to "Atlasia AI Bot" with email `ai-bot@atlasia.io`
- **Timestamps**: Includes ISO-8601 timestamps in author and committer info
- **Conventional Commits**: Uses conventional commit format (feat:, fix:, docs:, etc.)

### 6. Pull Request Generation
- **Title**: Prefixed with `[AI]` and uses issue title
- **Body**: Comprehensive PR description including:
  - Summary of changes
  - Link to original issue (with `Closes #` syntax)
  - Issue description excerpt
  - Categorized list of file changes (created, modified, deleted)
  - Testing notes
  - Implementation notes
  - Checklist of validation steps
  - AI attribution footer

### 7. Reasoning Trace Storage
- **Artifact Storage**: Stores LLM reasoning trace as database artifact
- **Metadata**: Includes timestamp, issue number, analysis type
- **File Summary**: Lists all files with operations and sizes
- **Notes**: Captures testing and implementation notes
- **JSON Format**: Structured JSON for easy querying and analysis

## Architecture

### System Prompt Design
The developer system prompt is carefully crafted to instruct the LLM to:
- Write clean, production-ready code following existing patterns
- Implement complete solutions with proper error handling
- Follow SOLID principles and design patterns
- Create comprehensive tests (unit, integration, e2e)
- Handle edge cases and validation
- Add appropriate logging and documentation
- Ensure backward compatibility
- Follow security best practices

### User Prompt Construction
The user prompt includes:
- Issue information (number, title, description)
- Ticket plan from PM step
- Architecture notes from Architect step
- Work plan (if available)
- Repository structure and key file contents
- Implementation requirements and guidelines

### JSON Schema
The LLM response uses a strict JSON schema with:
- `summary`: Brief summary of implementation
- `files`: Array of file changes (path, operation, content, explanation)
- `testingNotes`: Documentation of test coverage
- `implementationNotes`: Important implementation details

## Configuration

### Required Properties
```yaml
atlasia:
  orchestrator:
    repo-allowlist: "src/,docs/,pom.xml"  # Comma-separated allowed prefixes
    workflow-protect-prefix: ".github/workflows/"  # Protected workflow directory
```

### Dependencies
- `GitHubApiClient`: For GitHub API operations
- `LlmService`: For LLM code generation
- `ObjectMapper`: For JSON parsing
- `OrchestratorProperties`: For configuration

## Error Handling

### Custom Exception
- `DeveloperStepException`: Wraps all errors with context
- Provides clear error messages for debugging
- Includes original exception as cause

### Failure Modes
1. **Branch Name Missing**: Throws `IllegalArgumentException`
2. **Base SHA Missing**: Throws `IllegalStateException`
3. **LLM Failures**: Retries 3 times with exponential backoff, then uses fallback
4. **Validation Failures**: Throws `IllegalArgumentException` with specific reason
5. **Blob Creation Failures**: Throws `DeveloperStepException` with file context
6. **Tree/Commit Failures**: Throws `DeveloperStepException` with operation context

### Logging
- **INFO**: Major steps (start, branch creation, commit creation, PR creation)
- **DEBUG**: Detailed operations (blob creation, tree entries, file details)
- **WARN**: Non-fatal issues (LLM retries, dangerous scripts, potential secrets)
- **ERROR**: Failures with full stack traces

## Testing

### Unit Tests (`DeveloperStepTest`)
- End-to-end successful implementation
- Conflict handling on existing branches
- Validation rejection scenarios (protected files, allowlist, path traversal)
- Commit message type determination
- Multi-file tree structure creation
- LLM fallback behavior
- Commit attribution verification
- Reasoning trace storage

### Integration Tests (`DeveloperStepIntegrationTest`)
- End-to-end code generation flow
- LLM retry mechanism
- Complete LLM failure handling
- Security validation (invalid paths, private keys)
- Conflict resolution on existing branches
- Proper commit attribution in integration context
- Reasoning trace artifact storage

## Usage Example

```java
@Autowired
private DeveloperStep developerStep;

// Setup context
RunEntity runEntity = new RunEntity(uuid, "owner/repo", 123, "full", RunStatus.DEVELOPER, now);
RunContext context = new RunContext(runEntity, "owner", "repo");
context.setBranchName("ai/issue-123");
context.setIssueData(issueData);
context.setArchitectureNotes(architectureNotes);

// Execute
String prUrl = developerStep.execute(context);
System.out.println("Created PR: " + prUrl);
```

## Best Practices

1. **Always set branch name** before calling execute
2. **Provide architecture notes** for better code generation
3. **Configure allowlist** to restrict file access
4. **Protect workflow files** to prevent CI/CD tampering
5. **Monitor LLM usage** via reasoning traces
6. **Review generated PRs** before merging
7. **Check logs** for security warnings
8. **Use conventional commits** for better changelog generation

## Limitations

1. Maximum 100 files per commit
2. Maximum 1MB per file
3. LLM token limits may restrict context size
4. Fallback mode creates documentation only, not code
5. Only supports UTF-8 encoding
6. Supports initial commits for empty repositories (no `main` branch required)

## Future Enhancements

- [ ] Support for custom base branches (not just main)
- [ ] Incremental updates to existing PRs
- [ ] Code review integration (auto-request reviewers)
- [ ] Automatic test execution before PR creation
- [ ] Support for binary files
- [ ] Custom commit message templates
- [ ] Integration with code quality tools (SonarQube, etc.)
- [ ] Support for monorepo file scoping
