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
- **Backend**: Spring Boot 3.3, Java 17, Maven, PostgreSQL, JPA
- **Frontend**: Node 20+, Angular (inferred), Playwright E2E
- **Infra**: Docker Compose, Postgres 16

## Gems & Personas
The orchestrator is powered by specialized AI "Gems":
- **Aabo**: Security & Infrastructure
- **Aksil**: Architecture & Performance
- **Imad**: Full-stack & Cloud-Native
- **Tiziri**: UX & Product Strategy

Access them directly via the "Chat Mode" in the UI.

## Architecture
Monorepo with `/ai-orchestrator` (Spring Boot API), `/frontend` (Angular UI), `/ai` (agent configurations), `/infra` (Docker setup). 
The system uses a **Dual-Mode** engine (Code vs Chat) to provide both autonomous engineering and lightweight AI dialogue.

## Code Style
- Java: Package structure `com.atlasia.ai.{controller,model,persistence,api,config}`
- No secrets/tokens committed; bearer token auth for `/runs` endpoint
- Coverage gates: Backend ≥70% line, Frontend ≥60% line (see `docs/QUALITY_GATES.md`)
