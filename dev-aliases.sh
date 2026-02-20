#!/bin/bash
# Dev Compose Quick Commands

# Start everything
alias devup="docker compose -f docker-compose.dev.yml up"
alias devdown="docker compose -f docker-compose.dev.yml down"
alias devlogs="docker compose -f docker-compose.dev.yml logs -f"

# Start specific services
alias backend="docker compose -f docker-compose.dev.yml --profile backend up -d"
alias frontend="docker compose -f docker-compose.dev.yml --profile frontend up"

# Restart services
alias restart-all="docker compose -f docker-compose.dev.yml restart"
alias restart-api="docker compose -f docker-compose.dev.yml restart ai-orchestrator"
alias restart-ui="docker compose -f docker-compose.dev.yml restart ai-dashboard-dev"

# Database
alias db-reset="docker compose -f docker-compose.dev.yml down -v && docker compose -f docker-compose.dev.yml up -d ai-db"
alias db-shell="docker exec -it $(docker compose -f docker-compose.dev.yml ps -q ai-db) psql -U ai -d ai"

# Rebuild
alias rebuild="docker compose -f docker-compose.dev.yml build"
alias rebuild-no-cache="docker compose -f docker-compose.dev.yml build --no-cache"

# Status
alias devps="docker compose -f docker-compose.dev.yml ps"

# View specific service logs
alias logs-api="docker compose -f docker-compose.dev.yml logs -f ai-orchestrator"
alias logs-ui="docker compose -f docker-compose.dev.yml logs -f ai-dashboard-dev"
alias logs-db="docker compose -f docker-compose.dev.yml logs -f ai-db"
