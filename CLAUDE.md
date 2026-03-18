# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Atlasia AI Orchestrator** — an autonomous AI-powered software development platform. It transforms GitHub Issues into production-ready Pull Requests through a multi-agent pipeline.

Two modes:
- **Code Mode**: Triggered by the `ai:run` GitHub label. Runs a fully autonomous pipeline: Issue → PM → Qualifier → Architect → Developer → Review → Tester → Writer → PR
- **Chat Mode**: Interactive dialogue with 8 AI personas (Architect, Backend Developer, QA Engineer, Frontend Designer, Security Engineer, Code Quality Engineer, SRE Engineer, Frontend UX Engineer)

## Commands

### Backend (`ai-orchestrator/` — Spring Boot 3.3, Java 17, Maven)

```bash
mvn clean install          # Install dependencies
mvn clean verify           # Build + run tests
mvn test                   # Run tests only
mvn spring-boot:run        # Start dev server

# Run a single test class or method
mvn test -Dtest=DeveloperStepTest
mvn test -Dtest=DeveloperStepTest#testExecute_createsSuccessfulImplementation
```

### Frontend (`frontend/` — Angular 21)

```bash
npm ci                     # Install dependencies
npm start                  # Dev server (ng serve)
npm run build              # Production build
npm test                   # Unit tests (Vitest)
npm run e2e                # E2E tests (Playwright, playwright.fast.config.ts)
npm run lint               # Lint

# Run a single test file (Vitest)
npx vitest run src/app/services/auth.service.spec.ts
```

### Infrastructure

```bash
docker compose -f infra/docker-compose.ai.yml up -d   # Start local PostgreSQL
docker compose up -d                                    # Production deployment
./scripts/deploy.sh [staging|prod]                     # Deploy
./scripts/rollback.sh                                  # Rollback
infra/vault-init.sh                                    # Initialize HashiCorp Vault secrets
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/ai` |
| `DB_USER` / `DB_PASSWORD` | Database credentials | `ai` / `ai` |
| `ORCHESTRATOR_TOKEN` | API authentication token | `changeme` |
| `LLM_ENDPOINT` | Primary LLM API endpoint | `https://api.openai.com/v1` |
| `LLM_MODEL` | LLM model identifier | `gpt-4o-mini` |
| `LLM_API_KEY` | LLM API key | — |
| `LLM_FALLBACK_ENDPOINT` / `LLM_FALLBACK_API_KEY` | Fallback LLM (DeepSeek) | — |
| `GITHUB_APP_ID` / `GITHUB_PRIVATE_KEY_PATH` / `GITHUB_INSTALLATION_ID` | GitHub App credentials | — |
| `GITHUB_WEBHOOK_SECRET` | GitHub webhook HMAC secret | — |
| `REPO_ALLOWLIST` | Modifiable paths (CSV) | `backend/,frontend/,docs/,infra/,ai/` |
| `WORKFLOW_PROTECT` | Protected path prefix | `.github/workflows/` |

## Architecture

### Monorepo Structure

```
ai-orchestrator/     Spring Boot backend
frontend/            Angular dashboard
ai/
  agents/            Agent YAML contracts (orchestrator, pm, qualifier, architect, developer, review, tester, writer, judge)
  agents/personas/   **Single source of truth for all 8 personas** (4 Code Mode review + 4 Chat Mode). Merged schema: Code Mode fields (persona, review_checklist, severity_levels…) + Chat Mode fields (id, identity, communication_style, skills, constraints, handoff…). Maven copies this directory to classpath:ai/agents/personas/ at build time.
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

### Standalone Demo Projects (`aipersona-*`)

Eight self-contained Spring Boot projects in the repo root (package `com.yourproject.orchestrator`) demonstrating Java 21 features applied to the persona domain. They are independent of `ai-orchestrator` and are not part of its build.

| Directory | Java 21 Feature Demonstrated |
|---|---|
| `aipersona-restclient/` | Multi-provider `RestClient` (OpenAI, Anthropic, Ollama) with `@ConfigurationProperties` |
| `aipersona-sealed/` | Sealed types for domain results (`CodeGenerationResult`, `HandoffResult`) |
| `aipersona-pattern-matching/` | Pattern matching (`switch` expressions) for routing persona requests |
| `aipersona-structured-concurrency/` | `StructuredTaskScope` for parallel persona calls |
| `aipersona-vthreads/` | Virtual threads for async persona orchestration |
| `aipersona-memory/` | Conversation session persistence (JPA `ConversationSession`/`ConversationTurn`) |
| `aipersona-codegen/` | Structured code generation with JPA artifact storage |
| `aipersona-handoff/` | Agent-to-agent handoff with `PersonaHandoff` persistence |
| `aipersona-personas/` | Core persona YAML loader (reference implementation) |

Each demo has its own `pom.xml`, HTTP scratch file (e.g. `codegen.http`, `persona-chat.http`), and test class. Run them independently with `mvn spring-boot:run` from their directory.

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

`com.atlasia.ai.{controller,model,persistence,api,config,service,domain}`

### Key API Endpoints

- `POST /api/runs` — Trigger a full orchestration pipeline
- `GET /api/runs` — List all runs
- `GET /api/personas` — List available AI Review Roles
- `POST /api/chat/{personaName}` — Chat directly with a persona
- `GET /actuator/health` — Health check
- `WS /ws/runs/{runId}/collaboration` — Real-time STOMP/SockJS collaboration (CRDT-based)
- `GET/POST /api/admin/websocket/*` — WebSocket admin monitoring
- `GET/POST /api/multi-repo/*` — Multi-repository orchestration

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

### Règle anti-décharge sur l'environnement

- Si une commande échoue pour cause d'environnement (JDK, dépendances, services), vérifier d'abord que tous les scripts et procédures du projet ont été exécutés (`scripts/*.ps1`, `*.sh`, README/AGENTS.md/CLAUDE.md).
- Ne jamais conclure « tu dois lancer X sur ta machine » si le dépôt contient un script ou une instruction pour faire X : l'assistant doit exécuter cette étape avant de reporter l'échec ou de déléguer à l'utilisateur.
- En cas d'échec après avoir suivi les procédures du projet, le message doit indiquer explicitement : « J'ai exécuté [script/commande] puis [commande] ; l'échec persiste parce que [raison]. »
