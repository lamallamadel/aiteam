# Runbook — Atlasia (template)

## Prérequis
- Java 17
- Node 20+
- Docker Desktop / Docker Engine

## Backend
- Build & tests: `cd backend && ./mvnw clean verify`
- Run local (exemple): `cd backend && ./mvnw spring-boot:run`

## Frontend
- Install: `cd frontend && npm ci`
- Run: `cd frontend && npm run start`
- Lint: `cd frontend && npm run lint`
- Unit tests: `cd frontend && npm test -- --watch=false`

## E2E (fast)
- `cd frontend && npm run e2e:fast`

## Infra
- (À compléter selon votre `infra/docker-compose.yml`)
