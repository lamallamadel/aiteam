# QualifierStep Enhancement Implementation Summary

## Overview
Enhanced the `QualifierStep` with intelligent task decomposition that analyzes the ticket plan and repository structure, uses LLM to generate realistic task breakdowns with file paths and test requirements, infers commands from AGENTS.md, and validates work_plan schema conformance.

## Key Components Implemented

### 1. Enhanced QualifierStep (`ai-orchestrator/src/main/java/com/atlasia/ai/service/QualifierStep.java`)

#### Main Features:
- **Ticket Plan Analysis**: Parses the ticket plan from PmStep to understand requirements, acceptance criteria, and risks
- **Repository Structure Analysis**: Fetches and analyzes the repository tree from GitHub API to understand:
  - Backend files (.java files in ai-orchestrator)
  - Frontend files (.ts, .js, .html, .css, .scss in frontend)
  - Test files (files containing "test" or "spec")
  - Documentation files (.md files, docs directory)
  - Infrastructure files (docker, yml, yaml files in infra)
  
- **AGENTS.md Parsing**: Reads and parses AGENTS.md to extract:
  - Backend build command (`mvn clean verify`)
  - Backend test command (`mvn test`)
  - Frontend lint command (`npm run lint`)
  - Frontend test command (`npm test`)
  - E2E test command (`npm run e2e`)

- **LLM-based Task Decomposition**: Uses LLM with structured output to generate:
  - Minimum 3 tasks with specific areas (backend, frontend, infra, docs)
  - Realistic file paths based on repository structure
  - Test requirements for each task
  - Definition of done criteria

- **Fallback Strategy**: When LLM fails, generates a sensible fallback plan based on:
  - Ticket summary keyword analysis (backend/frontend/api/ui)
  - Repository structure
  - Default task templates

- **Work Plan Validation**: Ensures the generated work plan conforms to the schema:
  - Required fields: branchName, tasks, commands, definitionOfDone
  - Minimum 3 tasks required
  - Valid task areas (backend, frontend, infra, docs)
  - Each task has id, area, description, filesLikely, tests

#### Inner Classes:
- **RepoStructure**: Organizes and categorizes repository files
- **AgentsMdInfo**: Stores and parses command information from AGENTS.md

### 2. Enhanced GitHubApiClient (`ai-orchestrator/src/main/java/com/atlasia/ai/service/GitHubApiClient.java`)

Added three new methods:
- `getRepoContent(owner, repo, path)`: Fetches a single file content (for AGENTS.md)
- `listRepoContents(owner, repo, path)`: Lists directory contents
- `getRepoTree(owner, repo, sha, recursive)`: Fetches the entire repository tree recursively

### 3. Comprehensive Tests (`ai-orchestrator/src/test/java/com/atlasia/ai/service/QualifierStepTest.java`)

Created 25 comprehensive test cases covering:
- Successful LLM-based work plan generation
- Fallback strategy when LLM fails
- Ticket plan parsing
- Repository structure analysis
- AGENTS.md parsing with custom commands
- Fallback to default commands when AGENTS.md is unavailable
- Repository structure fallback when GitHub API fails
- Work plan enhancement with missing fields
- Schema validation for:
  - Minimum task count (3)
  - Valid task areas
  - Required fields
- Task enrichment (files, tests, definition of done)
- Markdown code block cleaning
- Backend-only and frontend-only tickets
- Inclusion of acceptance criteria and risks in LLM prompt

## Schema Conformance

The implementation strictly validates against `ai/schemas/work_plan.schema.json`:

```json
{
  "branchName": "string",
  "tasks": [
    {
      "id": "string",
      "area": "backend|frontend|infra|docs",
      "description": "string",
      "filesLikely": ["string"],
      "tests": ["string"]
    }
  ],
  "commands": {
    "backendVerify": "string",
    "frontendLint": "string",
    "frontendTest": "string",
    "e2e": "string"
  },
  "definitionOfDone": ["string"]
}
```

## LLM Integration

### System Prompt:
Instructs the LLM to act as a technical qualifier breaking down work into:
- Logical, sequential tasks across different areas
- Specific file paths based on repository structure
- Appropriate test types
- Granular, actionable tasks

### User Prompt:
Provides comprehensive context including:
- Ticket information (issue #, title, summary)
- Acceptance criteria
- Risks and considerations
- Repository structure samples (backend, frontend, tests, docs)
- Available commands from AGENTS.md
- Clear instructions for task breakdown

### Structured Output Schema:
Defines strict JSON schema for LLM response to ensure:
- Type safety
- Required fields
- Enum constraints for task areas
- Minimum array lengths

## Error Handling

### Multiple Fallback Layers:
1. **LLM Failure**: Falls back to heuristic-based task generation
2. **GitHub API Failure**: Uses default repository structure assumptions
3. **AGENTS.md Missing**: Uses hardcoded default commands
4. **Invalid Response**: Attempts to enhance incomplete LLM responses

### Logging:
- Info logs for successful operations
- Warn logs for fallback scenarios
- Error logs for LLM failures
- Debug logs for LLM responses

## Intelligence Features

### Keyword Analysis:
The fallback strategy analyzes ticket summaries for keywords:
- `backend`, `api`, `service` → Creates backend tasks
- `frontend`, `ui`, `interface` → Creates frontend tasks
- Falls back to both if no keywords found

### File Path Inference:
When LLM doesn't provide file paths:
- Backend area → Uses backend files from repo structure
- Frontend area → Uses frontend files from repo structure
- Infra area → Uses infrastructure files
- Docs area → Uses documentation files

### Test Requirement Inference:
Automatically adds "Unit tests" when tasks don't specify test requirements

### Command Extraction:
Parses AGENTS.md with regex to extract commands from markdown:
- Pattern: `- **{Label}**: \`{command}\``
- Supports multiple commands per type (e.g., backend test | frontend test)

## Integration Points

### Inputs:
- `RunContext` with ticket plan from PmStep
- GitHub repository via GitHubApiClient
- LLM service for structured output generation

### Outputs:
- JSON work plan conforming to schema
- Branch name set in RunContext
- Validated and ready for WorkflowEngine

### Dependencies:
- `ObjectMapper` for JSON serialization
- `LlmService` for AI-powered task generation
- `GitHubApiClient` for repository analysis
- `TicketPlan` model from PmStep output

## Test Coverage

The test suite validates:
- Happy path with successful LLM generation
- All fallback scenarios
- Schema validation enforcement
- Enhancement of incomplete responses
- Error handling for external service failures
- Context propagation (branch name)
- Different ticket types (backend-only, frontend-only, full-stack)

All tests use mocking to avoid external dependencies and ensure fast, reliable execution.
