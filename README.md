# Atlasia AI Orchestrator

Autonomous AI-powered software development orchestration platform. Transforms GitHub Issues into production-ready Pull Requests through a specialized multi-agent pipeline.

## Dual-Mode Architecture

```
CODE MODE (Autonomous Pipeline)
Issue -> [PM] -> [Qualifier] -> [Architect] -> [Developer] -> [Review] -> [Tester] -> [Writer] -> PR

CHAT MODE (Interactive AI Dialogue)
User <-> [Review Role] (Security Engineer | Code Quality Engineer | SRE Engineer | Frontend UX Engineer)
```

**Code Mode** — Triggered by the `ai:run` GitHub label via webhook. Executes the full pipeline autonomously: requirement analysis, architecture validation, code generation, persona-based review, testing with CI fix loops, and documentation updates.

**Chat Mode** — Direct conversations with specialized AI personas (Review Roles) for brainstorming, code review, and research.

### Event-Driven Webhooks

The orchestrator supports GitHub webhooks for instant, event-driven workflow execution. When an issue is labeled with `ai:run`, a workflow run starts immediately without polling. See [docs/WEBHOOKS.md](docs/WEBHOOKS.md) for setup instructions.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3, Java 17, Maven |
| Frontend | Angular 21, TypeScript, Chart.js |
| Database | PostgreSQL 16, Flyway migrations |
| Resilience | Resilience4j circuit breakers, exponential backoff |
| Observability | Prometheus, Micrometer, correlation ID tracing |
| Infrastructure | Docker multi-stage builds, Nginx, Docker Compose |

## Quickstart

```bash
# Prerequisites: Java 17, Node 20+, Docker

# 1. Start infrastructure (PostgreSQL)
docker compose -f infra/docker-compose.ai.yml up -d

# 2. Build and run backend
cd ai-orchestrator && mvn clean verify && mvn spring-boot:run

# 3. Build and run frontend
cd frontend && npm ci && npm start
```

**Production deployment:**
```bash
docker compose up -d
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/ai` |
| `DB_USER` | Database username | `ai` |
| `DB_PASSWORD` | Database password | `ai` |
| `ORCHESTRATOR_TOKEN` | API authentication token | `changeme` |
| `LLM_ENDPOINT` | Primary LLM API endpoint | `https://api.openai.com/v1` |
| `LLM_MODEL` | LLM model identifier | `gpt-4o-mini` |
| `LLM_API_KEY` | LLM API key | — |
| `LLM_FALLBACK_ENDPOINT` | Fallback LLM endpoint | `https://api.deepseek.com/v1` |
| `LLM_FALLBACK_API_KEY` | Fallback LLM key | — |
| `GITHUB_APP_ID` | GitHub App ID | — |
| `GITHUB_PRIVATE_KEY_PATH` | Path to GitHub App private key | — |
| `GITHUB_INSTALLATION_ID` | GitHub App installation ID | — |
| `GITHUB_WEBHOOK_SECRET` | GitHub webhook HMAC secret | — |
| `REPO_ALLOWLIST` | Modifiable paths (CSV) | `backend/,frontend/,docs/,infra/,ai/` |
| `WORKFLOW_PROTECT` | Protected path prefix | `.github/workflows/` |

## Project Structure

```
atlasia-ai-pack/
  ai-orchestrator/     Spring Boot backend (controllers, services, entities)
  frontend/            Angular 21 dashboard (components, services, models)
  ai/
    agents/            Agent YAML contracts (orchestrator, pm, qualifier, ...)
    agents/personas/   Review Role definitions (security-engineer, code-quality-engineer, sre-engineer, frontend-ux-engineer)
    schemas/           JSON schema validation (ticket_plan, work_plan, ...)
    playbooks/         Operational playbooks (branching, CI fix loops, ...)
  docs/                Quality gates, domain glossary, runbooks
  infra/               Docker Compose for local development
  .github/             CI workflows, issue/PR templates
```

## Quality Gates

- **Backend coverage**: >= 70% (JaCoCo)
- **Frontend coverage**: >= 60%
- **CI fix loops**: max 3 iterations
- **E2E fix loops**: max 2 iterations
- **No direct commits to main** — PR required
- **No workflow modifications** without human escalation

See [docs/QUALITY_GATES.md](docs/QUALITY_GATES.md) for full details.

## Documentation

- [Domain Glossary](docs/DOMAIN_GLOSSARY.md) — Standardized terminology
- [Quality Gates](docs/QUALITY_GATES.md) — Acceptance criteria and thresholds
- [Runbook](docs/RUNBOOK.md) — Operational procedures
- [GitHub Webhooks](docs/WEBHOOKS.md) — Event-driven workflow integration
- [AI Governance Pack](ai/README.md) — Agent contracts and schemas
- [Developer Setup](AGENTS.md) — Build commands and architecture overview
