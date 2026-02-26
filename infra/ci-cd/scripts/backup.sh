#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# POSTGRESQL BACKUP SCRIPT WITH RETENTION POLICY
# ═════════════════════════════════════════════════════════════════════════════
# Usage: ./infra/ci-cd/scripts/backup.sh [dev|prod]
# 
# Retention Policy:
#   - Daily backups: 7 days
#   - Weekly backups: 4 weeks (Sunday backups)
#   - Monthly backups: Keep indefinitely (1st of month backups)
#
# Environment Variables:
#   - BACKUP_DIR: Base backup directory (default: ./backups)
#   - DB_CONTAINER: Database container name (auto-detected or override)
#   - DB_NAME: Database name (default: ai or aiteam_prod)
#   - DB_USER: Database user (default: ai or aiteam_prod_user)
#   - S3_BUCKET: Optional S3 bucket for remote backup
#
# Examples:
#   ./backup.sh prod                          # Production backup
#   ./backup.sh dev                           # Development backup
#   S3_BUCKET=my-bucket ./backup.sh prod     # Backup with S3 upload
#   BACKUP_DIR=/custom/path ./backup.sh prod # Custom backup directory
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

# Backup configuration
readonly BACKUP_BASE_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
readonly DAILY_DIR="$BACKUP_BASE_DIR/daily"
readonly WEEKLY_DIR="$BACKUP_BASE_DIR/weekly"
readonly MONTHLY_DIR="$BACKUP_BASE_DIR/monthly"
readonly TIMESTAMP=$(date +%Y%m%d_%H%M%S)
readonly DATE_ONLY=$(date +%Y%m%d)
readonly DAY_OF_WEEK=$(date +%u)
readonly DAY_OF_MONTH=$(date +%d)

# Retention settings
readonly DAILY_RETENTION=7
readonly WEEKLY_RETENTION=4

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

# Detect database configuration based on environment
detect_db_config() {
    local env=$1
    
    if [[ "$env" == "prod" ]]; then
        DB_CONTAINER="${DB_CONTAINER:-aiteam_db_prod}"
        DB_NAME="${DB_NAME:-aiteam_prod}"
        DB_USER="${DB_USER:-aiteam_prod_user}"
        COMPOSE_FILE="$PROJECT_ROOT/infra/deployments/prod/docker-compose.yml"
    else
        DB_CONTAINER="${DB_CONTAINER:-ai-db}"
        DB_NAME="${DB_NAME:-ai}"
        DB_USER="${DB_USER:-ai}"
        COMPOSE_FILE="$PROJECT_ROOT/infra/docker-compose.ai.yml"
    fi
    
    log "Environment: $env"
    log "Database Container: $DB_CONTAINER"
    log "Database Name: $DB_NAME"
    log "Database User: $DB_USER"
}

# Check if database container is running
check_db_running() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
        log_error "Database container '$DB_CONTAINER' is not running"
        exit 1
    fi
    log_success "Database container is running"
}

# Create backup directories
create_backup_dirs() {
    mkdir -p "$DAILY_DIR" "$WEEKLY_DIR" "$MONTHLY_DIR"
    log_success "Backup directories created"
}

# Perform database backup
backup_database() {
    local backup_file=$1
    
    log "Creating database backup: $backup_file"
    
    if docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$backup_file"; then
        local size=$(du -h "$backup_file" | cut -f1)
        log_success "Backup created successfully (Size: $size)"
        
        # Verify backup integrity
        if gunzip -t "$backup_file" 2>/dev/null; then
            log_success "Backup integrity verified"
        else
            log_error "Backup file is corrupted"
            rm -f "$backup_file"
            exit 1
        fi
    else
        log_error "Database backup failed"
        rm -f "$backup_file"
        exit 1
    fi
}

# Backup database schema only (for documentation)
backup_schema() {
    local schema_file=$1
    
    log "Creating schema backup: $schema_file"
    
    if docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" --schema-only > "$schema_file"; then
        log_success "Schema backup created"
    else
        log_warning "Schema backup failed (non-critical)"
    fi
}

# Apply retention policy for daily backups
cleanup_daily_backups() {
    log "Applying retention policy for daily backups (keep last $DAILY_RETENTION days)"
    
    find "$DAILY_DIR" -name "*.sql.gz" -type f -mtime +$DAILY_RETENTION -delete 2>/dev/null || true
    
    local count=$(find "$DAILY_DIR" -name "*.sql.gz" -type f | wc -l)
    log_success "Daily backups retained: $count"
}

# Apply retention policy for weekly backups
cleanup_weekly_backups() {
    log "Applying retention policy for weekly backups (keep last $WEEKLY_RETENTION weeks)"
    
    find "$WEEKLY_DIR" -name "*.sql.gz" -type f -mtime +$((WEEKLY_RETENTION * 7)) -delete 2>/dev/null || true
    
    local count=$(find "$WEEKLY_DIR" -name "*.sql.gz" -type f | wc -l)
    log_success "Weekly backups retained: $count"
}

# Upload to S3 if configured
upload_to_s3() {
    local backup_file=$1
    
    if [[ -n "${S3_BUCKET:-}" ]]; then
        log "Uploading backup to S3: s3://$S3_BUCKET/"
        
        if command -v aws &> /dev/null; then
            local s3_path="s3://$S3_BUCKET/atlasia-backups/$(basename $backup_file)"
            if aws s3 cp "$backup_file" "$s3_path" --storage-class STANDARD_IA; then
                log_success "Backup uploaded to S3: $s3_path"
            else
                log_warning "S3 upload failed (backup still available locally)"
            fi
        else
            log_warning "AWS CLI not installed, skipping S3 upload"
        fi
    fi
}

# Generate backup report
generate_report() {
    local backup_file=$1
    local report_file="${backup_file%.sql.gz}.report.txt"
    
    cat > "$report_file" << EOF
════════════════════════════════════════════════════════════════
ATLASIA DATABASE BACKUP REPORT
════════════════════════════════════════════════════════════════

Backup Information:
  - Timestamp: $(date +'%Y-%m-%d %H:%M:%S %Z')
  - Environment: $ENVIRONMENT
  - Database: $DB_NAME
  - Container: $DB_CONTAINER
  
Backup File:
  - Path: $backup_file
  - Size: $(du -h "$backup_file" | cut -f1)
  - MD5: $(md5sum "$backup_file" | cut -d' ' -f1)

Database Statistics:
$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" "$DB_NAME" -c "\dt+" 2>/dev/null || echo "  Unable to fetch statistics")

Retention Status:
  - Daily backups: $(find "$DAILY_DIR" -name "*.sql.gz" -type f | wc -l) files
  - Weekly backups: $(find "$WEEKLY_DIR" -name "*.sql.gz" -type f | wc -l) files
  - Monthly backups: $(find "$MONTHLY_DIR" -name "*.sql.gz" -type f | wc -l) files

════════════════════════════════════════════════════════════════
EOF
    
    log_success "Backup report generated: $report_file"
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN BACKUP LOGIC
# ═════════════════════════════════════════════════════════════════════════════

main() {
    echo "════════════════════════════════════════════════════════════════"
    log "ATLASIA DATABASE BACKUP"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # Detect configuration
    detect_db_config "$ENVIRONMENT"
    echo ""
    
    # Pre-flight checks
    log "Running pre-flight checks..."
    check_db_running
    create_backup_dirs
    echo ""
    
    # Determine backup type and file paths
    local backup_type="daily"
    local backup_dir="$DAILY_DIR"
    
    # Weekly backup (Sunday)
    if [[ "$DAY_OF_WEEK" == "7" ]]; then
        backup_type="weekly"
        backup_dir="$WEEKLY_DIR"
    fi
    
    # Monthly backup (1st of month)
    if [[ "$DAY_OF_MONTH" == "01" ]]; then
        backup_type="monthly"
        backup_dir="$MONTHLY_DIR"
    fi
    
    local backup_file="$backup_dir/backup_${DATE_ONLY}_${TIMESTAMP}.sql.gz"
    local schema_file="$backup_dir/schema_${DATE_ONLY}_${TIMESTAMP}.sql"
    
    log "Backup type: $backup_type"
    echo ""
    
    # Perform backup
    log "Starting backup process..."
    backup_database "$backup_file"
    backup_schema "$schema_file"
    echo ""
    
    # Upload to remote storage
    upload_to_s3 "$backup_file"
    echo ""
    
    # Apply retention policies
    log "Applying retention policies..."
    cleanup_daily_backups
    cleanup_weekly_backups
    echo ""
    
    # Generate report
    generate_report "$backup_file"
    echo ""
    
    # Summary
    echo "════════════════════════════════════════════════════════════════"
    log_success "BACKUP COMPLETED SUCCESSFULLY"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    echo "Backup Details:"
    echo "  • Type: $backup_type"
    echo "  • File: $backup_file"
    echo "  • Size: $(du -h "$backup_file" | cut -f1)"
    echo ""
    echo "To restore this backup:"
    echo "  zcat $backup_file | docker exec -i $DB_CONTAINER psql -U $DB_USER $DB_NAME"
    echo ""
}

# ═════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ═════════════════════════════════════════════════════════════════════════════

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
