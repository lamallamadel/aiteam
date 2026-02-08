# Runbook - Atlasia AI Orchestrator

## Prerequisites
- Java 17+
- Node.js 18+
- Docker & Docker Compose
- GitHub Token with repo/workflow scopes

## Backend Setup
```powershell
cd ai-orchestrator
mvn clean install
mvn spring-boot:run
```

## Frontend Setup
```powershell
cd frontend
npm install
npm run start
```

## AI Analytics & Learning
Advanced analytics endpoints are available:
- **Run Summary**: `GET /api/analytics/runs/summary`
- **Agent Performance**: `GET /api/analytics/agents/performance`
- **Escalation Insights**: `GET /api/analytics/escalations/insights`
- **Persona Effectiveness**: `GET /api/analytics/personas/effectiveness`

## E2E Testing
```powershell
cd frontend
npm run e2e:fast
```
