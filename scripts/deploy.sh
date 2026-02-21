#!/bin/bash
# ============================================================================
# AITEAM PRODUCTION DEPLOYMENT SCRIPT
# ============================================================================
# Usage: ./scripts/deploy.sh [staging|prod]
# Example: ./scripts/deploy.sh prod

set -e

ENVIRONMENT=${1:-prod}
COMPOSE_FILE="docker-compose.${ENVIRONMENT}.yml"
ENV_FILE=".env.${ENVIRONMENT}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# VALIDATION
# ============================================================================

if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "prod" ]]; then
    echo -e "${RED}❌ Invalid environment. Use: staging or prod${NC}"
    exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo -e "${RED}❌ File not found: $COMPOSE_FILE${NC}"
    exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo -e "${RED}❌ File not found: $ENV_FILE${NC}"
    echo -e "${YELLOW}   Create from template: cp .env.example $ENV_FILE${NC}"
    exit 1
fi

# Check required environment variables
required_vars=("DB_PASSWORD" "ORCHESTRATOR_TOKEN" "LLM_API_KEY" "GITHUB_REPO" "DOMAIN")
for var in "${required_vars[@]}"; do
    if ! grep -q "^${var}=" "$ENV_FILE"; then
        echo -e "${RED}❌ Missing required variable in $ENV_FILE: $var${NC}"
        exit 1
    fi
done

# ============================================================================
# PRE-DEPLOYMENT CHECKS
# ============================================================================

echo -e "${BLUE}📋 Pre-deployment checks...${NC}"

# Check Docker is running
if ! docker ps > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running${NC}"
    exit 1
fi

# Check disk space (need at least 2GB free)
DISK_FREE=$(df . | awk 'NR==2 {print $4}')
if [[ $DISK_FREE -lt 2097152 ]]; then
    echo -e "${RED}❌ Not enough disk space (need 2GB, have $(numfmt --to=iec $((DISK_FREE * 1024))))${NC}"
    exit 1
fi

# Pull latest images
echo -e "${BLUE}🔄 Pulling latest images...${NC}"
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" pull || {
    echo -e "${RED}❌ Failed to pull images${NC}"
    exit 1
}

# ============================================================================
# BACKUP (PROD ONLY)
# ============================================================================

if [[ "$ENVIRONMENT" == "prod" ]]; then
    echo -e "${BLUE}💾 Creating database backup...${NC}"
    BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T ai-db \
        pg_dump -U aiteam aiteam | gzip > "$BACKUP_DIR/aiteam_backup.sql.gz"
    
    if [[ $? -eq 0 ]]; then
        echo -e "${GREEN}✅ Backup created: $BACKUP_DIR/aiteam_backup.sql.gz${NC}"
    else
        echo -e "${YELLOW}⚠️  Backup failed (continuing anyway)${NC}"
    fi
fi

# ============================================================================
# DEPLOYMENT
# ============================================================================

echo -e "${BLUE}🚀 Deploying aiteam ($ENVIRONMENT)...${NC}"

docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d || {
    echo -e "${RED}❌ Deployment failed${NC}"
    exit 1
}

# ============================================================================
# HEALTH CHECKS
# ============================================================================

echo -e "${BLUE}🏥 Running health checks...${NC}"

HEALTH_CHECK_RETRIES=30
HEALTH_CHECK_INTERVAL=5

# Check database
echo -n "  Database: "
for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
    if docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T ai-db \
        pg_isready -U aiteam > /dev/null 2>&1; then
        echo -e "${GREEN}✅${NC}"
        break
    fi
    if [[ $i -eq $HEALTH_CHECK_RETRIES ]]; then
        echo -e "${RED}❌ Timeout${NC}"
        exit 1
    fi
    sleep $HEALTH_CHECK_INTERVAL
done

# Check backend
echo -n "  Backend: "
for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
    if docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T ai-orchestrator \
        wget -qO- http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅${NC}"
        break
    fi
    if [[ $i -eq $HEALTH_CHECK_RETRIES ]]; then
        echo -e "${RED}❌ Timeout${NC}"
        exit 1
    fi
    sleep $HEALTH_CHECK_INTERVAL
done

# Check frontend
echo -n "  Frontend: "
for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
    if docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T ai-dashboard \
        wget -qO- http://localhost:80/index.html > /dev/null 2>&1; then
        echo -e "${GREEN}✅${NC}"
        break
    fi
    if [[ $i -eq $HEALTH_CHECK_RETRIES ]]; then
        echo -e "${RED}❌ Timeout${NC}"
        exit 1
    fi
    sleep $HEALTH_CHECK_INTERVAL
done

# ============================================================================
# POST-DEPLOYMENT
# ============================================================================

echo -e "${BLUE}📊 Deployment summary:${NC}"
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps

echo ""
echo -e "${GREEN}✅ Deployment successful!${NC}"
echo ""
echo -e "${BLUE}📍 Service URLs:${NC}"
echo "   Frontend: https://$(grep '^DOMAIN=' "$ENV_FILE" | cut -d= -f2)"
echo "   Backend API: https://$(grep '^DOMAIN=' "$ENV_FILE" | cut -d= -f2)/api"
echo ""
echo -e "${BLUE}📋 Next steps:${NC}"
echo "   - Monitor logs: docker-compose -f $COMPOSE_FILE logs -f"
echo "   - Check health: curl https://$(grep '^DOMAIN=' "$ENV_FILE" | cut -d= -f2)/health"
echo ""
