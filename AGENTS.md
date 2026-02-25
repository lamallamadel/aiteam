# AGENTS.md — Atlasia Developer Guide

## Setup
```bash
# Prerequisites: Java 17, Node 20+, Docker
cd ai-orchestrator && mvn clean install  # Install backend dependencies
cd frontend && npm ci                     # Install frontend dependencies
docker compose -f infra/docker-compose.ai.yml up -d  # Start infrastructure
```

## Commands
- **Build**: `cd ai-orchestrator && mvn clean verify`
- **Lint**: `cd frontend && npm run lint`
- **Test**: `cd ai-orchestrator && mvn test` | `cd frontend && npm test -- --watch=false`
- **Dev server**: `cd ai-orchestrator && mvn spring-boot:run` | `cd frontend && npm run start`

## Tech Stack
- **Backend**: Spring Boot 3.3, Java 17, Maven, PostgreSQL, JPA, Spring Cloud Vault
- **Frontend**: Node 20+, Angular (inferred), Playwright E2E
- **Infra**: Docker Compose, Postgres 16, HashiCorp Vault
- **Secrets**: HashiCorp Vault for secure secrets management

## Review Roles (formerly Gems & Personas)
The orchestrator is powered by specialized AI "Review Roles":
- **Security Engineer**: Security & Infrastructure
- **Code Quality Engineer**: Architecture & Performance
- **SRE Engineer**: Full-stack & Cloud-Native
- **Frontend UX Engineer**: UX & Product Strategy

Access them directly via the "Chat Mode" in the UI.

## Architecture
Monorepo with `/ai-orchestrator` (Spring Boot API), `/frontend` (Angular UI), `/ai` (agent configurations), `/infra` (Docker setup). 
The system uses a **Dual-Mode** engine (Code vs Chat) to provide both autonomous engineering and lightweight AI dialogue.

### Secrets Management (NEW)
HashiCorp Vault integration for secure secrets management:
- **Spring Cloud Vault**: Automatic secret injection at application startup
- **VaultSecretsService**: Programmatic secret access and rotation
- **SecretRotationScheduler**: Automated monthly JWT key rotation, quarterly OAuth2 rotation
- **VaultHealthIndicator**: Health monitoring via actuator endpoints
- **Secrets Path**: `secret/data/atlasia/*` (KV v2 engine)
- **Setup**: See `docs/VAULT_SETUP.md` and `infra/README.md`
- **Quick Start**: Run `infra/vault-init.sh` to initialize all secrets

### Multi-User Collaboration (NEW)
Real-time WebSocket-based collaboration for workflow runs:
- **WebSocket Endpoint**: `/ws/runs/{runId}/collaboration` (STOMP over SockJS)
- **Features**: Live graft/prune/flag mutations, presence indicators, cursor tracking
- **CRDT-Based Sync**: Automerge CRDT for conflict-free distributed state synchronization (replaces operational transformation)
- **Multi-Region Support**: WebSocket mesh topology for eventual consistency across global deployments
- **Partition Tolerance**: AP guarantee (Availability + Partition Tolerance) enables collaboration during network splits
- **Concurrent Editing**: Multiple users can graft agents at same position without conflicts - CRDT automatically merges deterministically
- **Audit**: All events stored in `collaboration_events` table with CRDT change logs and Lamport timestamps
- **Snapshots**: Periodic CRDT snapshots in `crdt_snapshots` table for fast recovery
- **Health Monitoring**: Connection quality metrics (latency, reconnections, delivery rate), Prometheus/Grafana dashboards
- **Resilience**: Client-side message queuing, automatic HTTP polling fallback, server-side message persistence
- **Admin API**: `/api/admin/websocket/*` endpoints for monitoring active connections and metrics
- **CRDT API**: `/api/crdt/*` endpoints for state queries and peer registration
- **Docs**: See `docs/COLLABORATION.md`, `docs/COLLABORATION_EXAMPLES.md`, and `docs/CRDT_COLLABORATION.md`

### Multi-Repository Workflow Orchestration (NEW)
Dependency-aware scheduling for cross-repository workflows:
- **Topological Sort**: Automatic execution ordering based on repository dependencies (Kahn's algorithm)
- **Cross-Repo Grafts**: Security scan in repo-A triggers documentation update graft in repo-B via A2A protocol
- **Monorepo Detection**: Auto-detect Maven modules (`pom.xml`) and NPM workspaces (`package.json`)
- **Coordinated PRs**: Create and merge PRs with dependency-aware ordering (merge infra before apps)
- **Cyclic Detection**: Prevents invalid dependency graphs with early validation
- **Repository Graph**: `repository_graph` table stores cross-repo dependencies as JSONB
- **API Endpoints**: `/api/multi-repo/*` for registration, execution, and PR coordination
- **Docs**: See `docs/MULTI_REPO_ORCHESTRATION.md` for complete guide with examples

## Container Security
- **Trivy Scanning**: Automated vulnerability scanning in CI/CD pipeline
- **Non-root Users**: All containers run as unprivileged users
- **Runtime Hardening**: Read-only filesystems, dropped capabilities, resource limits
- **Build-time Checks**: Secrets detection, misconfiguration scanning
- **Deployment Blocking**: CRITICAL vulnerabilities prevent deployment
- **Config**: `infra/trivy-config.yaml` for suppression rules
- **Docs**: See `docs/CONTAINER_SECURITY.md` for detailed security hardening guide

## Code Style
- Java: Package structure `com.atlasia.ai.{controller,model,persistence,api,config}`
- No secrets/tokens committed; bearer token auth for `/runs` endpoint
- Coverage gates: Backend ≥70% line, Frontend ≥60% line (see `docs/QUALITY_GATES.md`)
