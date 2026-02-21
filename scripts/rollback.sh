#!/bin/bash
# ============================================================================
# AITEAM ROLLBACK SCRIPT
# ============================================================================
# Usage: ./scripts/rollback.sh [staging|prod]
# Restores previous version of services

set -e

ENVIRONMENT=${1:-prod}
COMPOSE_FILE="docker-compose.${ENVIRONMENT}.yml"
ENV_FILE=".env.${ENVIRONMENT}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ============================================================================
# VALIDATION
# ============================================================================

if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "prod" ]]; then
    echo -e "${RED}❌ Invalid environment. Use: staging or prod${NC}"
    exit 1
fi

if [[ "$ENVIRONMENT" == "prod" ]]; then
    echo -e "${RED}⚠️  WARNING: Rolling back PRODUCTION!${NC}"
    echo -e "${RED}This will stop services and restore from backup.${NC}"
    read -p "Type 'yes' to confirm: " confirm
    if [[ "$confirm" != "yes" ]]; then
        echo "Rollback cancelled."
        exit 0
    fi
fi

# ============================================================================
# ROLLBACK STEPS
# ============================================================================

echo -e "${BLUE}🔄 Rolling back aiteam ($ENVIRONMENT)...${NC}"

# 1. Stop current services
echo -e "${BLUE}1️⃣  Stopping services...${NC}"
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down

# 2. Find most recent backup
LATEST_BACKUP=$(ls -t ./backups/*/aiteam_backup.sql.gz 2>/dev/null | head -1)

if [[ -z "$LATEST_BACKUP" ]]; then
    echo -e "${YELLOW}⚠️  No database backup found. Skipping database restore.${NC}"
    echo -e "${BLUE}Starting services without database restore...${NC}"
else
    # 3. Start database only
    echo -e "${BLUE}2️⃣  Starting database...${NC}"
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d ai-db
    
    # Wait for database to be ready
    sleep 10
    
    # 4. Restore from backup
    echo -e "${BLUE}3️⃣  Restoring database from: $LATEST_BACKUP${NC}"
    zcat "$LATEST_BACKUP" | docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T ai-db \
        psql -U aiteam -d aiteam
    
    if [[ $? -eq 0 ]]; then
        echo -e "${GREEN}✅ Database restored${NC}"
    else
        echo -e "${RED}❌ Database restore failed${NC}"
        exit 1
    fi
fi

# 5. Start all services
echo -e "${BLUE}4️⃣  Starting all services...${NC}"
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

# 6. Wait for health checks
echo -e "${BLUE}5️⃣  Waiting for services to be healthy...${NC}"
sleep 15

# 7. Verify
echo -e "${BLUE}📊 Service status:${NC}"
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps

echo ""
echo -e "${GREEN}✅ Rollback complete!${NC}"
echo ""
echo -e "${YELLOW}⚠️  Review logs to ensure all services are running:${NC}"
echo "   docker-compose -f $COMPOSE_FILE logs -f"
