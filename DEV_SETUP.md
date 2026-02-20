# Development Setup Guide

## Quick Start

### 1. Set up environment variables
```bash
cp .env.example .env
# Edit .env with your values if needed
```

### 2. Start full dev stack (database, backend, frontend)
```bash
docker compose -f docker-compose.dev.yml up
# Or detached mode:
docker compose -f docker-compose.dev.yml up -d
```

### 3. Start only specific services
```bash
# Backend + database only
docker compose -f docker-compose.dev.yml --profile backend up -d

# Frontend only (if backend already running)
docker compose -f docker-compose.dev.yml --profile frontend up
```

### 4. View logs
```bash
# All services
docker compose -f docker-compose.dev.yml logs -f

# Specific service
docker compose -f docker-compose.dev.yml logs -f ai-orchestrator
docker compose -f docker-compose.dev.yml logs -f ai-dashboard-dev
```

### 5. Stop all services
```bash
docker compose -f docker-compose.dev.yml down

# Also remove volumes (reset database)
docker compose -f docker-compose.dev.yml down -v
```

---

## Features

### ✓ Hot Reload
- **Frontend**: Angular code changes auto-reload (5-10s via file polling)
- **Backend**: Changes require service restart (Java needs recompile)

### ✓ Health Checks
- Services wait for dependencies (database ready before app starts)
- `ai-db` checks PostgreSQL is accepting connections
- `ai-orchestrator` checks Spring Boot health endpoint
- `ai-dashboard-dev` checks dev server is responding

### ✓ Volume Mounts (Frontend)
- `./frontend/src` → `/app/src` — code changes instant
- `frontend_node_modules` (named volume) — prevents host/container sync issues

### ✓ Debug Ports
- Backend: `5005` exposed for Java remote debugging (IntelliJ, VS Code)
- Frontend: `4200` (standard Angular dev server)

### ✓ Database Persistence
- `ai_db_dev_data` volume keeps data between restarts

### ✓ Profiles (selective startup)
- `full` — all services (default)
- `backend` — database + orchestrator only
- `frontend` — frontend only

---

## Common Tasks

### Debug Java Backend
1. Uncomment `JAVA_TOOL_OPTIONS` in `docker-compose.dev.yml` (already enabled)
2. In your IDE (IntelliJ/VS Code), connect remote debugger to `localhost:5005`
3. Set breakpoints and step through code

### Reset Database
```bash
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d ai-db
```

### Rebuild Without Cache
```bash
docker compose -f docker-compose.dev.yml build --no-cache
```

### Access Database from Host
```bash
psql -h localhost -U ai -d ai
# Password: ai (from .env)
```

### Clear Build Cache (free up disk)
```bash
docker builder prune
```

---

## Troubleshooting

**Port already in use:**
```bash
# Find what's using the port
lsof -i :4200  # Mac/Linux
netstat -ano | findstr :4200  # Windows
# Change port in docker-compose.dev.yml
```

**Services stuck in restart loop:**
```bash
docker compose -f docker-compose.dev.yml logs -f ai-orchestrator
# Check for startup errors
```

**File changes not syncing:**
- Ensure you're editing files in `./frontend/src` (mounted as volume)
- Angular rebuild should trigger automatically (check logs)
- If stuck, restart service: `docker compose -f docker-compose.dev.yml restart ai-dashboard-dev`

**npm install failures:**
```bash
# Rebuild from scratch
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml build --no-cache
docker compose -f docker-compose.dev.yml up
```
