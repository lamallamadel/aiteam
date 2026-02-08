# Runbook - Atlasia AI Orchestrator

## Prerequisites
- Java 17+
- Node.js 18+
- Docker & Docker Compose
- GitHub Token with repo/workflow scopes

## Quick Start with Docker
The easiest way to start the stack is using Docker Compose.

1.  **Configure Environment**: Edit the `.env` file at the root and provide your `ORCHESTRATOR_TOKEN`.
2.  **Choose your Mode**:

### 🛠️ Development Mode (Hot-Reload)
Perfect for active coding. Frontend uses `ng serve` (port 4200) and code changes are reflected instantly.
```powershell
docker compose -f docker-compose.dev.yml up --build
```
- **Dashboard (Dev)**: `http://localhost:4200`
- **Backend**: `http://localhost:8080`

### 🚀 Production Mode
Optimized build. Frontend is served via Nginx (port 80).
```powershell
docker compose up --build
```
- **Dashboard (Prod)**: `http://localhost:80`
- **Backend**: `http://localhost:8088`

## Manual Setup
### Backend Setup
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
