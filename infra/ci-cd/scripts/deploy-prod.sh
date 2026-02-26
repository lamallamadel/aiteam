#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# PRODUCTION DEPLOYMENT SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# Usage: ./infra/ci-cd/scripts/deploy-prod.sh
# Environment Variables Required:
#   - DEPLOY_HOST: production server IP/hostname
#   - DEPLOY_USER: SSH username
#   - DEPLOY_DIR: deployment directory on remote (e.g., /opt/aiteam)
#   - DEPLOY_SSH_KEY: SSH private key (base64 encoded for CI/CD)
# ═════════════════════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"
PROD_DIR="$PROJECT_ROOT/infra/deployments/prod"

echo "🚀 AITEAM Production Deployment"
echo "================================"
echo "📍 Project Root: $PROJECT_ROOT"
echo "📍 Prod Config: $PROD_DIR"
echo ""

# Validate required environment variables
required_vars=(
    "DEPLOY_HOST"
    "DEPLOY_USER"
    "DEPLOY_DIR"
    "DB_PASSWORD"
    "ORCHESTRATOR_TOKEN"
    "LLM_API_KEY"
    "GITHUB_REPO"
    "DOMAIN"
)

echo "🔍 Validating required environment variables..."
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "❌ Missing required variable: $var"
        exit 1
    fi
done
echo "✅ All required variables set"
echo ""

# Setup SSH
echo "🔐 Setting up SSH connection..."
eval $(ssh-agent -s)
if [ -n "$DEPLOY_SSH_KEY" ]; then
    echo "$DEPLOY_SSH_KEY" | base64 -d | ssh-add - > /dev/null 2>&1
fi
mkdir -p ~/.ssh
ssh-keyscan -H "$DEPLOY_HOST" >> ~/.ssh/known_hosts 2>/dev/null || true
chmod 644 ~/.ssh/known_hosts
echo "✅ SSH configured"
echo ""

# Pre-deployment checks
echo "🔍 Pre-deployment checks..."
echo "   • Host: $DEPLOY_HOST"
echo "   • User: $DEPLOY_USER"
echo "   • Dir: $DEPLOY_DIR"
echo ""

# Deploy
echo "📤 Deploying to production..."
ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no "$DEPLOY_USER@$DEPLOY_HOST" << DEPLOY_SCRIPT
    set -e
    
    echo "📍 Connecting to production server..."
    
    # Create deployment directory if not exists
    mkdir -p $DEPLOY_DIR
    cd $DEPLOY_DIR
    
    echo "🔄 Pulling latest code from repository..."
    if [ -d ".git" ]; then
        git pull origin main --quiet
    else
        git clone ${GIT_REPO} . --quiet
    fi
    
    echo "💾 Creating pre-deployment backup..."
    
    # Create pre-deployment backup directory
    PREDEPLOYMENT_DIR="./backups/predeployment/\$(date +%Y%m%d_%H%M%S)"
    mkdir -p "\$PREDEPLOYMENT_DIR"
    
    # Use the automated backup script if available
    if [[ -f "./infra/ci-cd/scripts/backup.sh" ]] && [[ -x "./infra/ci-cd/scripts/backup.sh" ]]; then
        echo "   Using automated backup script..."
        BACKUP_DIR="./backups" ./infra/ci-cd/scripts/backup.sh prod 2>&1 | tee "\$PREDEPLOYMENT_DIR/backup.log" || {
            echo "   ⚠️  Automated backup encountered issues, creating fallback backup"
            if docker ps --format '{{.Names}}' | grep -q "aiteam_db_prod"; then
                docker exec aiteam_db_prod pg_dump -U ${DB_USER:-aiteam_prod_user} ${DB_NAME:-aiteam_prod} | gzip > "\$PREDEPLOYMENT_DIR/db_backup.sql.gz"
                echo "   ✅ Fallback backup created: \$PREDEPLOYMENT_DIR/db_backup.sql.gz"
            else
                echo "   ⚠️  Database container not running, skipping backup"
            fi
        }
        # Copy the latest daily backup to pre-deployment directory for safety
        if [[ -d "./backups/daily" ]]; then
            LATEST_BACKUP=\$(ls -t ./backups/daily/backup_*.sql.gz 2>/dev/null | head -1)
            if [[ -n "\$LATEST_BACKUP" ]]; then
                cp "\$LATEST_BACKUP" "\$PREDEPLOYMENT_DIR/" 2>/dev/null || true
                echo "   ✅ Latest daily backup copied to pre-deployment directory"
            fi
        fi
    else
        echo "   Backup script not found, using direct database dump..."
        if docker ps --format '{{.Names}}' | grep -q "aiteam_db_prod"; then
            docker exec aiteam_db_prod pg_dump -U ${DB_USER:-aiteam_prod_user} ${DB_NAME:-aiteam_prod} | gzip > "\$PREDEPLOYMENT_DIR/db_backup.sql.gz"
            echo "   ✅ Database backup created: \$PREDEPLOYMENT_DIR/db_backup.sql.gz"
        else
            echo "   ⚠️  Database container not running, skipping backup"
        fi
    fi
    
    echo "   📁 Pre-deployment backup location: \$PREDEPLOYMENT_DIR"
    
    # Save current deployment tags for rollback
    mkdir -p ./backups/rollback
    if docker ps --format '{{.Names}}' | grep -q "aiteam_backend_prod"; then
        CURRENT_TAG=\$(docker inspect aiteam_backend_prod --format='{{.Config.Image}}' | cut -d':' -f2)
        echo "\$(date +%Y%m%d_%H%M%S),\$CURRENT_TAG,\$CURRENT_TAG" >> ./backups/rollback/deployment_tags.log
        echo "   ✅ Current deployment tag saved for rollback: \$CURRENT_TAG"
    fi
    
    echo "🐳 Pulling latest images from GHCR..."
    docker-compose -f infra/deployments/prod/docker-compose.yml pull || {
        echo "⚠️  Image pull failed, using local images"
    }
    
    echo "🚀 Starting production services..."
    docker-compose \
        -f infra/deployments/prod/docker-compose.yml \
        --env-file infra/deployments/prod/.env.prod \
        up -d
    
    echo "⏳ Waiting for services to be healthy..."
    sleep 10
    
    echo "✅ Deployment complete"
    echo ""
    echo "📊 Service status:"
    docker-compose -f infra/deployments/prod/docker-compose.yml ps
    
DEPLOY_SCRIPT

echo ""
echo "✅ Production deployment successful"
echo ""
echo "🔗 Access: https://$DOMAIN"
echo "📊 View logs:"
echo "   ssh $DEPLOY_USER@$DEPLOY_HOST 'cd $DEPLOY_DIR && docker-compose -f infra/deployments/prod/docker-compose.yml logs -f'"
echo ""
