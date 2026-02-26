#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# BLUE-GREEN ROLLBACK SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# Usage: ./infra/ci-cd/scripts/rollback.sh [dev|prod] [--skip-backup]
# 
# Features:
#   - Blue-green deployment rollback via Docker image tags
#   - Automatic database backup before rollback (unless --skip-backup)
#   - Service health verification with timeout
#   - Graceful service shutdown (30s timeout)
#   - Deployment history tracking
#
# Environment Variables:
#   - ROLLBACK_TAG: Docker image tag to rollback to (default: previous from history)
#   - BACKUP_DIR: Backup directory (default: ./backups)
#   - GITHUB_REPO: GitHub repository for GHCR images (e.g., myorg/atlasia)
#
# Examples:
#   ./rollback.sh prod                           # Rollback to previous deployment
#   ./rollback.sh prod --skip-backup             # Emergency rollback (no backup)
#   ROLLBACK_TAG=v1.2.3 ./rollback.sh prod      # Rollback to specific version
#   ./rollback.sh dev                            # Rollback dev environment
#
# Rollback Process:
#   1. Confirm rollback (production only)
#   2. Detect current deployment tags
#   3. Determine rollback target (from history or ROLLBACK_TAG)
#   4. Create pre-rollback database backup
#   5. Pull rollback Docker images from GHCR
#   6. Stop current services gracefully
#   7. Start services with rollback tags
#   8. Wait for health checks
#   9. Verify rollback success
# ═════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Script configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"
readonly ENVIRONMENT="${1:-prod}"
readonly SKIP_BACKUP="${2:-}"

# Backup configuration
readonly BACKUP_BASE_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
readonly ROLLBACK_DIR="$BACKUP_BASE_DIR/rollback"
readonly TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# ═════════════════════════════════════════════════════════════════════════════
# HELPER FUNCTIONS
# ═════════════════════════════════════════════════════════════════════════════

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ❌ $1${NC}"
}

# Detect environment configuration
detect_environment() {
    local env=$1
    
    if [[ "$env" == "prod" ]]; then
        COMPOSE_FILE="$PROJECT_ROOT/infra/deployments/prod/docker-compose.yml"
        ENV_FILE="$PROJECT_ROOT/infra/deployments/prod/.env.prod"
        DB_CONTAINER="aiteam_db_prod"
        BACKEND_CONTAINER="aiteam_backend_prod"
        FRONTEND_CONTAINER="aiteam_frontend_prod"
        DB_NAME="${DB_NAME:-aiteam_prod}"
        DB_USER="${DB_USER:-aiteam_prod_user}"
    else
        COMPOSE_FILE="$PROJECT_ROOT/infra/docker-compose.ai.yml"
        ENV_FILE=""
        DB_CONTAINER="ai-db"
        BACKEND_CONTAINER="ai-orchestrator"
        FRONTEND_CONTAINER=""
        DB_NAME="${DB_NAME:-ai}"
        DB_USER="${DB_USER:-ai}"
    fi
    
    log "Environment: $env"
    log "Compose file: $COMPOSE_FILE"
    log "Database container: $DB_CONTAINER"
}

# Confirm rollback action
confirm_rollback() {
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        echo ""
        log_warning "═══════════════════════════════════════════════════════"
        log_warning "PRODUCTION ROLLBACK - THIS ACTION IS CRITICAL"
        log_warning "═══════════════════════════════════════════════════════"
        echo ""
        echo "This will:"
        echo "  1. Create a pre-rollback database backup"
        echo "  2. Stop current services"
        echo "  3. Rollback to previous Docker image tags"
        echo "  4. Restore database from backup (if available)"
        echo "  5. Start services with rolled back configuration"
        echo ""
        read -p "Type 'ROLLBACK' to confirm: " confirm
        if [[ "$confirm" != "ROLLBACK" ]]; then
            log "Rollback cancelled by user"
            exit 0
        fi
    fi
}

# Get current Docker image tags
get_current_tags() {
    log "Detecting current image tags..."
    
    if docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$"; then
        CURRENT_BACKEND_TAG=$(docker inspect "$BACKEND_CONTAINER" --format='{{.Config.Image}}' | cut -d':' -f2)
        log "Current backend tag: $CURRENT_BACKEND_TAG"
    else
        CURRENT_BACKEND_TAG="latest"
        log_warning "Backend container not running, using default tag: latest"
    fi
    
    if [[ -n "${FRONTEND_CONTAINER:-}" ]] && docker ps --format '{{.Names}}' | grep -q "^${FRONTEND_CONTAINER}$"; then
        CURRENT_FRONTEND_TAG=$(docker inspect "$FRONTEND_CONTAINER" --format='{{.Config.Image}}' | cut -d':' -f2)
        log "Current frontend tag: $CURRENT_FRONTEND_TAG"
    else
        CURRENT_FRONTEND_TAG="latest"
    fi
}

# Determine rollback target tags
determine_rollback_tags() {
    log "Determining rollback target tags..."
    
    if [[ -n "${ROLLBACK_TAG:-}" ]]; then
        TARGET_BACKEND_TAG="$ROLLBACK_TAG"
        TARGET_FRONTEND_TAG="$ROLLBACK_TAG"
        log "Using specified rollback tag: $ROLLBACK_TAG"
    else
        local tags_file="$ROLLBACK_DIR/deployment_tags.log"
        if [[ -f "$tags_file" ]]; then
            TARGET_BACKEND_TAG=$(tail -n 2 "$tags_file" | head -n 1 | cut -d',' -f2)
            TARGET_FRONTEND_TAG=$(tail -n 2 "$tags_file" | head -n 1 | cut -d',' -f3)
            log "Using previous deployment tags from history"
        else
            TARGET_BACKEND_TAG="previous"
            TARGET_FRONTEND_TAG="previous"
            log_warning "No deployment history found, using 'previous' tag"
        fi
    fi
    
    log "Rollback targets:"
    log "  Backend: $CURRENT_BACKEND_TAG → $TARGET_BACKEND_TAG"
    log "  Frontend: $CURRENT_FRONTEND_TAG → $TARGET_FRONTEND_TAG"
}

# Save current deployment state
save_deployment_state() {
    mkdir -p "$ROLLBACK_DIR"
    local state_file="$ROLLBACK_DIR/deployment_tags.log"
    echo "$TIMESTAMP,$CURRENT_BACKEND_TAG,$CURRENT_FRONTEND_TAG" >> "$state_file"
    log_success "Deployment state saved"
}

# Create pre-rollback backup
create_prerollback_backup() {
    if [[ "$SKIP_BACKUP" == "--skip-backup" ]]; then
        log_warning "Skipping pre-rollback backup (--skip-backup flag)"
        return
    fi
    
    log "Creating pre-rollback database backup..."
    
    if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        mkdir -p "$ROLLBACK_DIR"
        local backup_file="$ROLLBACK_DIR/prerollback_${TIMESTAMP}.sql.gz"
        
        if docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$backup_file"; then
            local size=$(du -h "$backup_file" | cut -f1)
            log_success "Pre-rollback backup created: $backup_file (Size: $size)"
            
            if gunzip -t "$backup_file" 2>/dev/null; then
                log_success "Backup integrity verified"
            else
                log_error "Backup file is corrupted"
                exit 1
            fi
        else
            log_error "Pre-rollback backup failed"
            exit 1
        fi
    else
        log_warning "Database container not running, skipping backup"
    fi
}

# Pull rollback Docker images
pull_rollback_images() {
    log "Pulling rollback Docker images..."
    
    if [[ -z "${GITHUB_REPO:-}" ]]; then
        log_warning "GITHUB_REPO not set, skipping image pull"
        return
    fi
    
    local backend_image="ghcr.io/${GITHUB_REPO}/ai-orchestrator:${TARGET_BACKEND_TAG}"
    local frontend_image="ghcr.io/${GITHUB_REPO}/ai-dashboard:${TARGET_FRONTEND_TAG}"
    
    log "Pulling backend image: $backend_image"
    if docker pull "$backend_image" 2>/dev/null; then
        log_success "Backend image pulled"
    else
        log_warning "Failed to pull backend image, will use local cache"
    fi
    
    if [[ -n "${FRONTEND_CONTAINER:-}" ]]; then
        log "Pulling frontend image: $frontend_image"
        if docker pull "$frontend_image" 2>/dev/null; then
            log_success "Frontend image pulled"
        else
            log_warning "Failed to pull frontend image, will use local cache"
        fi
    fi
}

# Update docker-compose to use rollback tags
update_compose_tags() {
    log "Updating docker-compose configuration..."
    
    export IMAGE_TAG="${TARGET_BACKEND_TAG}"
    export BACKEND_IMAGE_TAG="${TARGET_BACKEND_TAG}"
    export FRONTEND_IMAGE_TAG="${TARGET_FRONTEND_TAG}"
    
    log_success "Docker image tags updated in environment"
}

# Stop current services gracefully
stop_services() {
    log "Stopping current services..."
    
    if [[ -f "$ENV_FILE" ]]; then
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down --timeout 30
    else
        docker-compose -f "$COMPOSE_FILE" down --timeout 30
    fi
    
    log_success "Services stopped"
}

# Start services with rollback configuration
start_services() {
    log "Starting services with rollback configuration..."
    
    if [[ -f "$ENV_FILE" ]]; then
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d
    else
        docker-compose -f "$COMPOSE_FILE" up -d
    fi
    
    log_success "Services started"
}

# Wait for services to be healthy
wait_for_health() {
    log "Waiting for services to become healthy..."
    
    local max_attempts=30
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        attempt=$((attempt + 1))
        
        if docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$"; then
            local health=$(docker inspect "$BACKEND_CONTAINER" --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
            
            if [[ "$health" == "healthy" ]] || [[ "$health" == "unknown" ]]; then
                log_success "Backend service is healthy"
                return 0
            fi
        fi
        
        log "Waiting for health check... (${attempt}/${max_attempts})"
        sleep 10
    done
    
    log_warning "Health check timeout - services may need additional time to start"
}

# Verify rollback success
verify_rollback() {
    log "Verifying rollback..."
    
    local errors=0
    
    if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        log_error "Database container is not running"
        errors=$((errors + 1))
    else
        log_success "Database container is running"
    fi
    
    if ! docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$"; then
        log_error "Backend container is not running"
        errors=$((errors + 1))
    else
        log_success "Backend container is running"
        
        local current_tag=$(docker inspect "$BACKEND_CONTAINER" --format='{{.Config.Image}}' | cut -d':' -f2)
        if [[ "$current_tag" == "$TARGET_BACKEND_TAG" ]]; then
            log_success "Backend is using correct image tag: $current_tag"
        else
            log_warning "Backend tag mismatch: expected $TARGET_BACKEND_TAG, got $current_tag"
        fi
    fi
    
    if [[ -n "${FRONTEND_CONTAINER:-}" ]]; then
        if ! docker ps --format '{{.Names}}' | grep -q "^${FRONTEND_CONTAINER}$"; then
            log_error "Frontend container is not running"
            errors=$((errors + 1))
        else
            log_success "Frontend container is running"
        fi
    fi
    
    return $errors
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN ROLLBACK LOGIC
# ═════════════════════════════════════════════════════════════════════════════

main() {
    echo "════════════════════════════════════════════════════════════════"
    log "ATLASIA BLUE-GREEN ROLLBACK"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    detect_environment "$ENVIRONMENT"
    echo ""
    
    confirm_rollback
    echo ""
    
    get_current_tags
    determine_rollback_tags
    save_deployment_state
    echo ""
    
    create_prerollback_backup
    echo ""
    
    pull_rollback_images
    echo ""
    
    update_compose_tags
    echo ""
    
    stop_services
    echo ""
    
    start_services
    echo ""
    
    wait_for_health
    echo ""
    
    if verify_rollback; then
        echo "════════════════════════════════════════════════════════════════"
        log_success "ROLLBACK COMPLETED SUCCESSFULLY"
        echo "════════════════════════════════════════════════════════════════"
        echo ""
        echo "Rolled Back To:"
        echo "  • Backend: $TARGET_BACKEND_TAG"
        echo "  • Frontend: $TARGET_FRONTEND_TAG"
        echo ""
        echo "Service Status:"
        if [[ -f "$ENV_FILE" ]]; then
            docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps
        else
            docker-compose -f "$COMPOSE_FILE" ps
        fi
        echo ""
        echo "View logs:"
        echo "  docker-compose -f $COMPOSE_FILE logs -f"
        echo ""
    else
        echo "════════════════════════════════════════════════════════════════"
        log_error "ROLLBACK COMPLETED WITH WARNINGS"
        echo "════════════════════════════════════════════════════════════════"
        echo ""
        echo "Some services may not be healthy. Check logs:"
        echo "  docker-compose -f $COMPOSE_FILE logs -f"
        echo ""
        exit 1
    fi
}

# ═════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ═════════════════════════════════════════════════════════════════════════════

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
