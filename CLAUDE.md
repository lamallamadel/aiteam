# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Atlasia AI Orchestrator** — an autonomous AI-powered software development platform. It transforms GitHub Issues into production-ready Pull Requests through a multi-agent pipeline.

Two modes:
- **Code Mode**: Triggered by the `ai:run` GitHub label. Runs a fully autonomous pipeline: Issue → PM → Qualifier → Architect → Developer → Review → Tester → Writer → PR
- **Chat Mode**: Interactive dialogue with specialized AI personas (Security Engineer, Code Quality Engineer, SRE Engineer, Frontend UX Engineer)

## Commands

### Backend (`ai-orchestrator/` — Spring Boot 3.3, Java 17, Maven)

```bash
mvn clean install          # Install dependencies
mvn clean verify           # Build + run tests
mvn test                   # Run tests only
mvn spring-boot:run        # Start dev server
```

### Frontend (`frontend/` — Angular 21)

```bash
npm ci                     # Install dependencies
npm start                  # Dev server (ng serve)
npm run build              # Production build
npm test                   # Unit tests (Vitest)
npm run e2e                # E2E tests (Playwright, playwright.fast.config.ts)
npm run lint               # Lint
```

### Infrastructure

```bash
docker compose -f infra/docker-compose.ai.yml up -d   # Start local PostgreSQL
docker compose up -d                                    # Production deployment
./scripts/deploy.sh [staging|prod]                     # Deploy
./scripts/rollback.sh                                  # Rollback
```

## Architecture

### Monorepo Structure

```
ai-orchestrator/     Spring Boot backend
frontend/            Angular dashboard
ai/
  agents/            Agent YAML contracts (orchestrator, pm, qualifier, architect, developer, review, tester, writer, judge)
  agents/personas/   Review role definitions (security-engineer, code-quality-engineer, sre-engineer, frontend-ux-engineer)
  schemas/           JSON schema validation for agent artifacts
  orchestration/     9 hybrid orchestration patterns (blackboard, critic_loop, task_ledger, guardrails, gates, etc.)
  playbooks/         Operational playbooks (branching, CI fix loops, issue intake, E2E policy)
  protocols/a2a/     Agent-to-Agent protocol and registry
  evaluation/        Eval suites and adversarial test sets
  governance/        Shadow mode governance config
infra/               Docker Compose, CI/CD configs, SSL, Vault, Trivy
docs/                Quality gates, runbooks, domain glossary, security docs
scripts/             Deployment, rollback, security scan scripts
monitoring/          Prometheus + Grafana dashboards
.agent/skills/       Agent skills (devops-cicd, security-review, testing)
.github/             GitHub Actions (ai-run.yml), issue/PR templates
```

### Key Technologies

| Layer | Tech |
|---|---|
| Backend | Spring Boot 3.3, Java 17, Maven |
| Frontend | Angular 21, TypeScript 5.9, Chart.js |
| Frontend Testing | Vitest 4, Playwright |
| Database | PostgreSQL 16, Flyway, Hibernate/JPA |
| Security | Spring Security, OAuth2, JWT (jjwt 0.12.5), MFA/TOTP, BCrypt, OWASP HTML Sanitizer |
| Secrets | HashiCorp Vault (Spring Cloud Vault) |
| Resilience | Resilience4j circuit breakers, Caffeine cache |
| Real-time | Spring WebSocket (STOMP/SockJS), Automerge CRDT |
| Observability | Prometheus, Micrometer, Grafana |
| LLM | OpenAI-compatible API (default: `gpt-4o-mini`), DeepSeek fallback |

### Backend Package Structure

`com.atlasia.ai.{controller,model,persistence,api,config}`

### Orchestration Patterns

9 hybrid patterns defined in `ai/orchestration/architecture.yaml`:
1. Sequential pipeline (PM → Writer backbone)
2. Graph/state machine with retry loops (test failures loop back to Developer — max **3** CI fix iterations, max **2** E2E fix iterations)
3. Hierarchical (Review agent supervises 4 specialist personas)
4. Critic Loop (dual-agent verification)
5. Task Ledger (shared execution plan)
6. A2A Protocol (capability-based agent discovery via Agent Cards)
7. Blackboard (versioned shared memory)
8. Dynamic Interrupts (runtime guardrails: critical/high/medium/low)
9. LLM-as-a-Judge (veto power, majority voting)

## Conventions & Quality Gates

### Branch / PR Rules

- **No direct commits to `main`** — PRs required for all changes
- **No modifications to `.github/workflows/**`** without human escalation

### Coverage Gates

- Backend (JaCoCo): >= 70% line coverage
- Frontend (Vitest): >= 60% line coverage
- A drop > 2 points requires justification in the PR

### Definition of Done

- Acceptance criteria met
- Unit tests added/updated (backend + frontend if applicable)
- Integration tests added if endpoint/persistence is affected
- DB migration versioned (Flyway) if schema changed
- Errors formatted as `ProblemDetails` per Atlasia conventions
- Docs updated (README/RUNBOOK/endpoints) if applicable

### DevOps / Docker Conventions

- Spring Boot Dockerfiles: multi-stage, `eclipse-temurin:21-jre-alpine`, non-root user, `ENTRYPOINT` (not `CMD`), health check via `/actuator/health`
- Angular Dockerfiles: multi-stage (Node build + Nginx runtime), production build, SPA routing fallback, Gzip enabled
- CI pipeline order: Build → Test → Code Quality (SonarQube) → Security Scan (OWASP + Trivy) → Build Image → Deploy Staging → E2E Tests → Deploy Production (manual approval)

### Frontend Formatting (Prettier)

- `printWidth: 100`, `singleQuote: true`, Angular HTML parser for `.html` files

### Escalation Triggers (Human Required)

- New product/architecture decisions (ADR required)
- Persistent flaky tests (>= 2 runs)
- Conflicts between ADR / business rules / naming conventions
- Quality gate impossible to satisfy without arbitration
