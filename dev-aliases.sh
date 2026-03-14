#!/bin/bash
# Dev Compose Quick Commands

# Start everything
alias devup="docker compose -f infra/deployments/dev/docker-compose.yml --profile full up"
alias devdown="docker compose -f infra/deployments/dev/docker-compose.yml down"
alias devlogs="docker compose -f infra/deployments/dev/docker-compose.yml logs -f"

# Start specific services
alias backend="docker compose -f infra/deployments/dev/docker-compose.yml --profile backend up -d"
alias frontend="docker compose -f infra/deployments/dev/docker-compose.yml --profile frontend up"

# Restart services
alias restart-all="docker compose -f infra/deployments/dev/docker-compose.yml restart"
alias restart-api="docker compose -f infra/deployments/dev/docker-compose.yml restart ai-orchestrator"
alias restart-ui="docker compose -f infra/deployments/dev/docker-compose.yml restart ai-dashboard"

# Database
alias db-reset="docker compose -f infra/deployments/dev/docker-compose.yml down -v && docker compose -f infra/deployments/dev/docker-compose.yml up -d ai-db"
alias db-shell="docker exec -it $(docker compose -f infra/deployments/dev/docker-compose.yml ps -q ai-db) psql -U ai_dev -d ai_dev"

# Rebuild
alias rebuild="docker compose -f infra/deployments/dev/docker-compose.yml build"
alias rebuild-no-cache="docker compose -f infra/deployments/dev/docker-compose.yml build --no-cache"

# Status
alias devps="docker compose -f infra/deployments/dev/docker-compose.yml ps"

# View specific service logs
alias logs-api="docker compose -f infra/deployments/dev/docker-compose.yml logs -f ai-orchestrator"
alias logs-ui="docker compose -f infra/deployments/dev/docker-compose.yml logs -f ai-dashboard"
alias logs-db="docker compose -f infra/deployments/dev/docker-compose.yml logs -f ai-db"
