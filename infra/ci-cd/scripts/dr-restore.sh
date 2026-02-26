#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# DISASTER RECOVERY RESTORATION SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# Usage: ./infra/ci-cd/scripts/dr-restore.sh [dev|prod] [backup-file] [--skip-verification]
# 
# Purpose:
#   Rapid restoration of Atlasia AI Orchestrator from backups following a disaster.
#   Supports restoration from local backups or S3, with automated verification.
#
# RTO (Recovery Time Objective): 4 hours
# RPO (Recovery Point Objective): 24 hours
#
# Environment Variables:
#   - BACKUP_FILE: Path to backup file (local or S3 URI)
#   - BACKUP_DIR: Base backup directory (default: ./backups)
#   - DB_CONTAINER: Database container name (auto-detected or override)
#   - DB_NAME: Database name (default: ai or aiteam_prod)
#   - DB_USER: Database user (default: ai or aiteam_prod_user)
#   - S3_BUCKET: S3 bucket for remote backup retrieval
#   - VAULT_BACKUP: Path to Vault backup (optional)
#   - SKIP_VERIFICATION: Skip post-restore verification (default: false)
#
# Examples:
#   ./dr-restore.sh prod /backups/daily/backup_20240115.sql.gz
#   ./dr-restore.sh prod s3://my-bucket/atlasia-backups/backup.sql.gz
#   VAULT_BACKUP=/vault/backup.snap ./dr-restore.sh prod backup.sql.gz
#   ./dr-restore.sh dev ./backups/daily/backup_latest.sql.gz --skip-verification
#
# Restoration Process:
#   1. Pre-flight checks (backup file exists, services accessible)
#   2. Create pre-restore backup (safety measure)
#   3. Stop application services (keep database running)
#   4. Restore database from backup
#   5. Restore Vault secrets (if backup provided)
#   6. Restart services
#   7. Verify restoration success
#   8. Run smoke tests
# ═════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

# Script configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"
readonly ENVIRONMENT="${1:-prod}"
readonly BACKUP_FILE_ARG="${2:-}"
readonly SKIP_VERIFICATION="${3:-}"

# Backup configuration
readonly BACKUP_BASE_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
readonly RESTORE_TEMP_DIR="$BACKUP_BASE_DIR/restore_temp"
readonly TIMESTAMP=$(date +%Y%m%d_%H%M%S)
readonly RESTORE_LOG="$BACKUP_BASE_DIR/restore_${TIMESTAMP}.log"

# Restoration tracking
RESTORE_START_TIME=$(date +%s)

# ═════════════════════════════════════════════════════════════════════════════
# HELPER FUNCTIONS
# ═════════════════════════════════════════════════════════════════════════════

log() {
    local msg="[$(date +'%Y-%m-%d %H:%M:%S')] $1"
    echo -e "${BLUE}${msg}${NC}"
    echo "$msg" >> "$RESTORE_LOG"
}

log_success() {
    local msg="[$(date +'%Y-%m-%d %H:%M:%S')] ✅ $1"
    echo -e "${GREEN}${msg}${NC}"
    echo "$msg" >> "$RESTORE_LOG"
}

log_warning() {
    local msg="[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️  $1"
    echo -e "${YELLOW}${msg}${NC}"
    echo "$msg" >> "$RESTORE_LOG"
}

log_error() {
    local msg="[$(date +'%Y-%m-%d %H:%M:%S')] ❌ $1"
    echo -e "${RED}${msg}${NC}"
    echo "$msg" >> "$RESTORE_LOG"
}

log_step() {
    local msg="[$(date +'%Y-%m-%d %H:%M:%S')] ▶ $1"
    echo -e "${CYAN}${msg}${NC}"
    echo "$msg" >> "$RESTORE_LOG"
}

# Calculate elapsed time
elapsed_time() {
    local end_time=$(date +%s)
    local elapsed=$((end_time - RESTORE_START_TIME))
    local hours=$((elapsed / 3600))
    local minutes=$(((elapsed % 3600) / 60))
    local seconds=$((elapsed % 60))
    printf "%02d:%02d:%02d" $hours $minutes $seconds
}

# Detect database configuration based on environment
detect_db_config() {
    local env=$1
    
    if [[ "$env" == "prod" ]]; then
        DB_CONTAINER="${DB_CONTAINER:-aiteam_db_prod}"
        DB_NAME="${DB_NAME:-aiteam_prod}"
        DB_USER="${DB_USER:-aiteam_prod_user}"
        BACKEND_CONTAINER="aiteam_backend_prod"
        FRONTEND_CONTAINER="aiteam_frontend_prod"
        VAULT_CONTAINER="aiteam_vault_prod"
        COMPOSE_FILE="$PROJECT_ROOT/infra/deployments/prod/docker-compose.yml"
        ENV_FILE="$PROJECT_ROOT/infra/deployments/prod/.env.prod"
        HEALTH_ENDPOINT="https://api.atlasia.ai/api/health/ready"
    else
        DB_CONTAINER="${DB_CONTAINER:-ai-db}"
        DB_NAME="${DB_NAME:-ai}"
        DB_USER="${DB_USER:-ai}"
        BACKEND_CONTAINER="ai-orchestrator"
        FRONTEND_CONTAINER=""
        VAULT_CONTAINER="ai-vault"
        COMPOSE_FILE="$PROJECT_ROOT/infra/docker-compose.ai.yml"
        ENV_FILE=""
        HEALTH_ENDPOINT="http://localhost:8080/api/health/ready"
    fi
    
    log "Environment: $env"
    log "Database Container: $DB_CONTAINER"
    log "Database Name: $DB_NAME"
    log "Backend Container: $BACKEND_CONTAINER"
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    # Check if Docker is running
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running"
        exit 1
    fi
    log_success "Docker is running"
    
    # Check if docker-compose is available
    if ! command -v docker-compose &> /dev/null; then
        log_error "docker-compose is not installed"
        exit 1
    fi
    log_success "docker-compose is available"
    
    # Check if database container exists
    if ! docker ps -a --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        log_error "Database container '$DB_CONTAINER' does not exist"
        exit 1
    fi
    log_success "Database container exists"
    
    # Create temp directory
    mkdir -p "$RESTORE_TEMP_DIR"
    log_success "Prerequisites check completed"
}

# Determine backup file location
determine_backup_file() {
    if [[ -z "$BACKUP_FILE_ARG" ]]; then
        log_error "No backup file specified. Usage: $0 [env] [backup-file]"
        exit 1
    fi
    
    # Check if it's an S3 URI
    if [[ "$BACKUP_FILE_ARG" =~ ^s3:// ]]; then
        log "Backup is stored in S3: $BACKUP_FILE_ARG"
        
        if ! command -v aws &> /dev/null; then
            log_error "AWS CLI is not installed, cannot retrieve S3 backup"
            exit 1
        fi
        
        local filename=$(basename "$BACKUP_FILE_ARG")
        BACKUP_FILE="$RESTORE_TEMP_DIR/$filename"
        
        log "Downloading backup from S3..."
        if aws s3 cp "$BACKUP_FILE_ARG" "$BACKUP_FILE"; then
            log_success "Backup downloaded to: $BACKUP_FILE"
        else
            log_error "Failed to download backup from S3"
            exit 1
        fi
    else
        # Local file
        BACKUP_FILE="$BACKUP_FILE_ARG"
        
        # Convert to absolute path if relative
        if [[ ! "$BACKUP_FILE" =~ ^/ ]]; then
            BACKUP_FILE="$PROJECT_ROOT/$BACKUP_FILE"
        fi
    fi
    
    # Verify backup file exists
    if [[ ! -f "$BACKUP_FILE" ]]; then
        log_error "Backup file not found: $BACKUP_FILE"
        exit 1
    fi
    
    log_success "Backup file located: $BACKUP_FILE"
    
    # Verify backup integrity
    log "Verifying backup integrity..."
    if [[ "$BACKUP_FILE" =~ \.gz$ ]]; then
        if gunzip -t "$BACKUP_FILE" 2>/dev/null; then
            log_success "Backup integrity verified (gzip)"
        else
            log_error "Backup file is corrupted"
            exit 1
        fi
    else
        log_success "Backup file is uncompressed SQL"
    fi
}

# Create pre-restore safety backup
create_prerestore_backup() {
    log_step "Creating pre-restore safety backup..."
    
    if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        log_warning "Database container not running, skipping pre-restore backup"
        return
    fi
    
    local prerestore_backup="$RESTORE_TEMP_DIR/prerestore_${TIMESTAMP}.sql.gz"
    
    if docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" 2>/dev/null | gzip > "$prerestore_backup"; then
        local size=$(du -h "$prerestore_backup" | cut -f1)
        log_success "Pre-restore backup created: $prerestore_backup (Size: $size)"
    else
        log_warning "Pre-restore backup failed (database may be empty or inaccessible)"
    fi
}

# Stop application services
stop_application_services() {
    log_step "Stopping application services..."
    
    # Stop backend
    if docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$"; then
        log "Stopping backend container: $BACKEND_CONTAINER"
        docker stop "$BACKEND_CONTAINER" --time 30 || true
        log_success "Backend stopped"
    fi
    
    # Stop frontend (if exists)
    if [[ -n "${FRONTEND_CONTAINER:-}" ]] && docker ps --format '{{.Names}}' | grep -q "^${FRONTEND_CONTAINER}$"; then
        log "Stopping frontend container: $FRONTEND_CONTAINER"
        docker stop "$FRONTEND_CONTAINER" --time 30 || true
        log_success "Frontend stopped"
    fi
    
    log_success "Application services stopped"
}

# Ensure database is running
ensure_database_running() {
    log_step "Ensuring database is running..."
    
    if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        log_success "Database is already running"
        return
    fi
    
    log "Starting database container..."
    if [[ -f "$ENV_FILE" ]]; then
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d "$DB_CONTAINER"
    else
        docker-compose -f "$COMPOSE_FILE" up -d "$DB_CONTAINER"
    fi
    
    # Wait for database to be ready
    local max_attempts=30
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        attempt=$((attempt + 1))
        
        if docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" > /dev/null 2>&1; then
            log_success "Database is ready"
            return
        fi
        
        log "Waiting for database to be ready... (${attempt}/${max_attempts})"
        sleep 2
    done
    
    log_error "Database failed to become ready"
    exit 1
}

# Terminate existing database connections
terminate_connections() {
    log_step "Terminating existing database connections..."
    
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -c "
        SELECT pg_terminate_backend(pid) 
        FROM pg_stat_activity 
        WHERE datname = '$DB_NAME' 
        AND pid <> pg_backend_pid();
    " > /dev/null 2>&1 || true
    
    log_success "Database connections terminated"
}

# Drop and recreate database
recreate_database() {
    log_step "Recreating database..."
    
    # Drop database
    log "Dropping existing database: $DB_NAME"
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" || {
        log_error "Failed to drop database"
        exit 1
    }
    
    # Create database
    log "Creating fresh database: $DB_NAME"
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;" || {
        log_error "Failed to create database"
        exit 1
    }
    
    log_success "Database recreated"
}

# Restore database from backup
restore_database() {
    log_step "Restoring database from backup..."
    
    local start_time=$(date +%s)
    
    if [[ "$BACKUP_FILE" =~ \.gz$ ]]; then
        log "Restoring from compressed backup..."
        if zcat "$BACKUP_FILE" | docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" > /dev/null 2>&1; then
            local end_time=$(date +%s)
            local duration=$((end_time - start_time))
            log_success "Database restored successfully in ${duration}s"
        else
            log_error "Database restoration failed"
            exit 1
        fi
    else
        log "Restoring from uncompressed backup..."
        if cat "$BACKUP_FILE" | docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" > /dev/null 2>&1; then
            local end_time=$(date +%s)
            local duration=$((end_time - start_time))
            log_success "Database restored successfully in ${duration}s"
        else
            log_error "Database restoration failed"
            exit 1
        fi
    fi
}

# Restore Vault secrets (if backup provided)
restore_vault_secrets() {
    if [[ -z "${VAULT_BACKUP:-}" ]]; then
        log_warning "No Vault backup provided, skipping Vault restoration"
        return
    fi
    
    log_step "Restoring Vault secrets..."
    
    if [[ ! -f "$VAULT_BACKUP" ]]; then
        log_error "Vault backup file not found: $VAULT_BACKUP"
        return
    fi
    
    if ! docker ps --format '{{.Names}}' | grep -q "^${VAULT_CONTAINER}$"; then
        log_warning "Vault container not running, skipping Vault restoration"
        return
    fi
    
    # Copy backup to container and restore
    docker cp "$VAULT_BACKUP" "$VAULT_CONTAINER:/tmp/vault_backup.snap"
    
    if docker exec "$VAULT_CONTAINER" vault operator raft snapshot restore /tmp/vault_backup.snap 2>/dev/null; then
        log_success "Vault secrets restored"
    else
        log_warning "Vault restoration failed (manual intervention may be required)"
    fi
}

# Start application services
start_application_services() {
    log_step "Starting application services..."
    
    if [[ -f "$ENV_FILE" ]]; then
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d
    else
        docker-compose -f "$COMPOSE_FILE" up -d
    fi
    
    log_success "Application services started"
}

# Wait for services to be healthy
wait_for_health() {
    log_step "Waiting for services to become healthy..."
    
    local max_attempts=60
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        attempt=$((attempt + 1))
        
        # Check if backend is running
        if ! docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$"; then
            log "Waiting for backend to start... (${attempt}/${max_attempts})"
            sleep 5
            continue
        fi
        
        # Check health endpoint
        if curl -f -s -k "$HEALTH_ENDPOINT" > /dev/null 2>&1; then
            log_success "Services are healthy"
            return 0
        fi
        
        log "Waiting for health check... (${attempt}/${max_attempts})"
        sleep 5
    done
    
    log_warning "Health check timeout - services may need additional time"
    return 1
}

# Verify database restoration
verify_database() {
    log_step "Verifying database restoration..."
    
    # Check table count
    local table_count=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null | xargs)
    
    if [[ -z "$table_count" ]] || [[ "$table_count" -eq 0 ]]; then
        log_error "Database verification failed: no tables found"
        return 1
    fi
    
    log_success "Database contains $table_count tables"
    
    # Check if key tables exist
    local key_tables=("workflow_runs" "users" "personas")
    for table in "${key_tables[@]}"; do
        if docker exec "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" -t -c "SELECT 1 FROM information_schema.tables WHERE table_name = '$table';" 2>/dev/null | grep -q 1; then
            log_success "Table '$table' exists"
        else
            log_warning "Table '$table' not found (may not have been created yet)"
        fi
    done
    
    return 0
}

# Run smoke tests
run_smoke_tests() {
    if [[ "$SKIP_VERIFICATION" == "--skip-verification" ]]; then
        log_warning "Skipping smoke tests (--skip-verification flag)"
        return
    fi
    
    log_step "Running smoke tests..."
    
    # Test 1: Database connectivity
    if docker exec "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" -c "SELECT 1;" > /dev/null 2>&1; then
        log_success "✓ Database connectivity test passed"
    else
        log_error "✗ Database connectivity test failed"
        return 1
    fi
    
    # Test 2: Health endpoint
    if curl -f -s -k "$HEALTH_ENDPOINT" > /dev/null 2>&1; then
        log_success "✓ Health endpoint test passed"
    else
        log_error "✗ Health endpoint test failed"
        return 1
    fi
    
    # Test 3: Container status
    local running_containers=$(docker-compose -f "$COMPOSE_FILE" ps --services --filter "status=running" 2>/dev/null | wc -l)
    if [[ $running_containers -gt 0 ]]; then
        log_success "✓ Container status test passed ($running_containers containers running)"
    else
        log_error "✗ Container status test failed"
        return 1
    fi
    
    log_success "All smoke tests passed"
    return 0
}

# Generate restoration report
generate_restoration_report() {
    local status=$1
    local report_file="$BACKUP_BASE_DIR/restore_report_${TIMESTAMP}.txt"
    
    cat > "$report_file" << EOF
════════════════════════════════════════════════════════════════
ATLASIA DISASTER RECOVERY RESTORATION REPORT
════════════════════════════════════════════════════════════════

Restoration Status: $status
Timestamp: $(date +'%Y-%m-%d %H:%M:%S %Z')
Environment: $ENVIRONMENT
Elapsed Time: $(elapsed_time)

Backup Information:
  - Source File: $BACKUP_FILE
  - Backup Size: $(du -h "$BACKUP_FILE" 2>/dev/null | cut -f1)
  - Backup MD5: $(md5sum "$BACKUP_FILE" 2>/dev/null | cut -d' ' -f1)

Database Information:
  - Container: $DB_CONTAINER
  - Database: $DB_NAME
  - User: $DB_USER
  - Tables Restored: $(docker exec "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null | xargs || echo "N/A")

Service Status:
$(docker-compose -f "$COMPOSE_FILE" ps 2>/dev/null || echo "Unable to fetch service status")

Health Check:
$(curl -s -k "$HEALTH_ENDPOINT" 2>/dev/null || echo "Health endpoint not accessible")

RTO Target: 4 hours
Actual RTO: $(elapsed_time)

RPO Target: 24 hours
Backup Age: $(stat -c %y "$BACKUP_FILE" 2>/dev/null || echo "N/A")

Restoration Log:
  Full log available at: $RESTORE_LOG

════════════════════════════════════════════════════════════════
EOF
    
    log_success "Restoration report generated: $report_file"
    
    # Display report summary
    cat "$report_file"
}

# Cleanup temp files
cleanup() {
    if [[ "$SKIP_VERIFICATION" == "--skip-verification" ]]; then
        log "Skipping cleanup of temporary files"
        return
    fi
    
    log "Cleaning up temporary files..."
    
    # Remove downloaded S3 files
    if [[ "$BACKUP_FILE_ARG" =~ ^s3:// ]] && [[ -f "$BACKUP_FILE" ]]; then
        rm -f "$BACKUP_FILE"
        log_success "Removed downloaded S3 backup"
    fi
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN RESTORATION LOGIC
# ═════════════════════════════════════════════════════════════════════════════

main() {
    echo "════════════════════════════════════════════════════════════════"
    log "ATLASIA DISASTER RECOVERY RESTORATION"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    log "RTO Target: 4 hours | RPO Target: 24 hours"
    echo ""
    
    # Initialize log file
    mkdir -p "$BACKUP_BASE_DIR"
    echo "Disaster Recovery Restoration - $(date)" > "$RESTORE_LOG"
    
    # Detect configuration
    detect_db_config "$ENVIRONMENT"
    echo ""
    
    # Pre-flight checks
    check_prerequisites
    determine_backup_file
    echo ""
    
    # Confirm restoration
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        echo ""
        log_warning "═══════════════════════════════════════════════════════"
        log_warning "PRODUCTION RESTORATION - CRITICAL OPERATION"
        log_warning "═══════════════════════════════════════════════════════"
        echo ""
        echo "This will:"
        echo "  1. Stop all application services"
        echo "  2. Drop and recreate the database"
        echo "  3. Restore from backup: $BACKUP_FILE"
        echo "  4. Restart all services"
        echo ""
        read -p "Type 'RESTORE' to confirm: " confirm
        if [[ "$confirm" != "RESTORE" ]]; then
            log "Restoration cancelled by user"
            exit 0
        fi
        echo ""
    fi
    
    # Create safety backup
    create_prerestore_backup
    echo ""
    
    # Stop services
    stop_application_services
    echo ""
    
    # Ensure database is running
    ensure_database_running
    echo ""
    
    # Terminate connections and recreate DB
    terminate_connections
    recreate_database
    echo ""
    
    # Restore database
    restore_database
    echo ""
    
    # Restore Vault (optional)
    restore_vault_secrets
    echo ""
    
    # Start services
    start_application_services
    echo ""
    
    # Wait for health
    wait_for_health
    echo ""
    
    # Verify restoration
    local verification_status="SUCCESS"
    if ! verify_database; then
        verification_status="WARNING"
    fi
    
    if ! run_smoke_tests; then
        verification_status="WARNING"
    fi
    echo ""
    
    # Generate report
    generate_restoration_report "$verification_status"
    echo ""
    
    # Cleanup
    cleanup
    echo ""
    
    # Final summary
    echo "════════════════════════════════════════════════════════════════"
    if [[ "$verification_status" == "SUCCESS" ]]; then
        log_success "DISASTER RECOVERY RESTORATION COMPLETED SUCCESSFULLY"
    else
        log_warning "DISASTER RECOVERY RESTORATION COMPLETED WITH WARNINGS"
    fi
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    echo "Restoration Summary:"
    echo "  • Environment: $ENVIRONMENT"
    echo "  • Backup File: $BACKUP_FILE"
    echo "  • Elapsed Time: $(elapsed_time)"
    echo "  • RTO Target: 4 hours"
    echo "  • Status: $verification_status"
    echo ""
    echo "Next Steps:"
    echo "  1. Verify application functionality"
    echo "  2. Review restoration log: $RESTORE_LOG"
    echo "  3. Monitor services: docker-compose -f $COMPOSE_FILE logs -f"
    echo "  4. Run full system tests"
    echo ""
    
    if [[ "$verification_status" != "SUCCESS" ]]; then
        exit 1
    fi
}

# ═════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ═════════════════════════════════════════════════════════════════════════════

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
