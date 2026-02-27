# GEMINI.md - Project Context & Instructions

This file serves as the foundational context for Gemini CLI interactions within the **Atlasia AI Orchestrator** project.

## Project Overview

**Atlasia AI Orchestrator** is an autonomous AI-powered software development platform. It transforms GitHub Issues into production-ready Pull Requests through a specialized multi-agent pipeline and provides an interactive "Chat Mode" for specialized AI dialogues.

### Key Capabilities
- **Code Mode (Autonomous):** Pipeline: Issue -> [PM] -> [Qualifier] -> [Architect] -> [Developer] -> [Review] -> [Tester] -> [Writer] -> PR.
- **Chat Mode (Interactive):** Specialized AI personas (Security, Code Quality, SRE, Frontend UX) for brainstorming and research.
- **Event-Driven:** Uses GitHub webhooks to trigger workflows via the `ai:run` label.

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.3, Maven, PostgreSQL 16, Flyway.
- **Frontend:** Angular 21, TypeScript, Chart.js, TailwindCSS (for some components).
- **Observability:** Prometheus, Micrometer, OpenTelemetry, Grafana.
- **Resilience:** Resilience4j (circuit breakers, retries).
- **Infrastructure:** Docker, Docker Compose, Nginx.

## Project Structure

- `ai-orchestrator/`: Spring Boot backend (core logic, controllers, services).
- `frontend/`: Angular 21 dashboard and UI.
- `ai/`:
  - `agents/`: Agent YAML contracts and prompt definitions.
  - `schemas/`: JSON schemas for internal data exchange (ticket_plan, work_plan).
  - `playbooks/`: Operational guidelines for agents.
- `infra/`: Docker Compose configurations and infrastructure scripts.
- `docs/`: Technical documentation, quality gates, and runbooks.
- `monitoring/`: Prometheus and Grafana configurations.

## Documentation Reference

The `docs/` directory contains critical architectural and operational documentation. Always refer to these when making significant changes:
- `DOMAIN_GLOSSARY.md`: Project-specific terminology.
- `QUALITY_GATES.md`: Mandatory thresholds for merging.
- `SECURITY.md`: Security policies and threat models.
- `RUNBOOK.md`: Troubleshooting and maintenance steps.
- `VAULT_SETUP.md`: Secrets management configuration.

## Building and Running

### Prerequisites
- Java 17
- Node.js 20+
- Docker & Docker Compose

### Local Development
1. **Infrastructure (PostgreSQL):**
   ```bash
   docker compose -f infra/docker-compose.ai.yml up -d
   ```
2. **Backend:**
   ```bash
   cd ai-orchestrator
   mvn clean verify
   mvn spring-boot:run
   ```
3. **Frontend:**
   ```bash
   cd frontend
   npm ci
   npm start
   ```

### Production Deployment
```bash
docker compose up -d
```

## Testing and Quality Gates

### Backend
- **Run Tests:** `mvn test`
- **E2E Tests:** `mvn verify -Pe2e`
- **Quality Gate:** Line coverage >= **70%** (JaCoCo).

### Frontend
- **Unit Tests:** `npm test` (Vitest)
- **E2E Tests:** `npm run e2e` (Playwright)
- **Quality Gate:** Line coverage >= **60%**.

### Global Standards
- **No direct commits to `main`**; all changes via PR.
- **No modification of `.github/workflows/**`** without human escalation.
- **Auto-correction:** Agents have limits on fix loops (3 for CI, 2 for E2E).

## Development Conventions

- **Branching:** Use descriptive branch names (e.g., `feat/`, `fix/`, `docs/`). AI-generated branches use `ai/issue-<number>`.
- **Commits:** Follow Conventional Commits (e.g., `feat: ...`, `fix: ...`).
- **Security:** Strict allowlist enforcement for file modifications (configured in `repo-allowlist`). Path traversal and secret detection are mandatory.
- **API Standards:** Backend follows Spring Boot best practices with `ProblemDetails` for error handling.

## AI Agent Integration

When working on agent logic or prompts:
- Refer to `ai/agents/` for agent definitions.
- Ensure any new JSON schemas are placed in `ai/schemas/`.
- Respect the boundaries defined in `docs/QUALITY_GATES.md`.
