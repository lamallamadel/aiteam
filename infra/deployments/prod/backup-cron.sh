#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# SCHEDULED BACKUP CRON SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# This script is designed to be run via cron for automated scheduled backups
#
# Installation:
#   1. Copy this script to production server: /opt/atlasia/infra/deployments/prod/
#   2. Make executable: chmod +x backup-cron.sh
#   3. Add to crontab: crontab -e
#   
# Crontab Examples:
#   # Daily backup at 2:00 AM
#   0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
#
#   # Every 6 hours
#   0 */6 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
#
#   # Weekly on Sunday at 3:00 AM
#   0 3 * * 0 /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
#
# Environment Variables:
#   - DEPLOY_DIR: Deployment directory (default: /opt/atlasia)
#   - BACKUP_DIR: Backup directory (default: /opt/atlasia/backups)
#   - S3_BUCKET: Optional S3 bucket for remote backup
#   - HEALTHCHECK_URL: Optional healthcheck.io URL to ping on success
# ═════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# Configuration
readonly DEPLOY_DIR="${DEPLOY_DIR:-/opt/atlasia}"
readonly BACKUP_SCRIPT="$DEPLOY_DIR/infra/ci-cd/scripts/backup.sh"
readonly LOG_FILE="${LOG_FILE:-/var/log/atlasia-backup.log}"
readonly TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")

# Colors for output (even though this is for cron, useful when running manually)
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# ═════════════════════════════════════════════════════════════════════════════
# FUNCTIONS
# ═════════════════════════════════════════════════════════════════════════════

log() {
    echo -e "${BLUE}[$TIMESTAMP]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$TIMESTAMP] ✅ $1${NC}"
}

log_error() {
    echo -e "${RED}[$TIMESTAMP] ❌ $1${NC}"
}

# Send notification to healthcheck service
notify_healthcheck() {
    if [[ -n "${HEALTHCHECK_URL:-}" ]]; then
        curl -fsS --retry 3 "$HEALTHCHECK_URL" > /dev/null 2>&1 || true
    fi
}

# Send email notification (if mail is configured)
send_email_notification() {
    local subject=$1
    local message=$2
    
    if command -v mail &> /dev/null && [[ -n "${ADMIN_EMAIL:-}" ]]; then
        echo "$message" | mail -s "$subject" "$ADMIN_EMAIL" || true
    fi
}

# Check disk space before backup
check_disk_space() {
    local backup_dir="${BACKUP_DIR:-$DEPLOY_DIR/backups}"
    local available_space=$(df "$backup_dir" | tail -1 | awk '{print $4}')
    local required_space=1048576  # 1GB in KB
    
    if [[ $available_space -lt $required_space ]]; then
        log_error "Insufficient disk space: ${available_space}KB available, ${required_space}KB required"
        send_email_notification "Atlasia Backup Failed - Disk Space" \
            "Backup failed due to insufficient disk space.\nAvailable: ${available_space}KB\nRequired: ${required_space}KB"
        return 1
    fi
    
    log_success "Disk space check passed: ${available_space}KB available"
    return 0
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN LOGIC
# ═════════════════════════════════════════════════════════════════════════════

main() {
    echo "════════════════════════════════════════════════════════════════"
    log "Starting scheduled backup"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # Pre-flight checks
    if [[ ! -f "$BACKUP_SCRIPT" ]]; then
        log_error "Backup script not found: $BACKUP_SCRIPT"
        send_email_notification "Atlasia Backup Failed - Script Not Found" \
            "The backup script was not found at: $BACKUP_SCRIPT"
        exit 1
    fi
    
    if [[ ! -x "$BACKUP_SCRIPT" ]]; then
        log_error "Backup script is not executable: $BACKUP_SCRIPT"
        chmod +x "$BACKUP_SCRIPT" || {
            send_email_notification "Atlasia Backup Failed - Permission Error" \
                "The backup script is not executable and chmod failed."
            exit 1
        }
        log_success "Made backup script executable"
    fi
    
    # Check disk space
    if ! check_disk_space; then
        exit 1
    fi
    
    echo ""
    
    # Run backup
    log "Executing backup script..."
    if "$BACKUP_SCRIPT" prod; then
        log_success "Backup completed successfully"
        notify_healthcheck
        send_email_notification "Atlasia Backup Successful" \
            "Scheduled backup completed successfully at $TIMESTAMP"
        echo ""
        echo "════════════════════════════════════════════════════════════════"
        log_success "SCHEDULED BACKUP COMPLETED"
        echo "════════════════════════════════════════════════════════════════"
        exit 0
    else
        log_error "Backup script failed"
        send_email_notification "Atlasia Backup Failed" \
            "Scheduled backup failed at $TIMESTAMP\n\nPlease check the logs at: $LOG_FILE"
        echo ""
        echo "════════════════════════════════════════════════════════════════"
        log_error "SCHEDULED BACKUP FAILED"
        echo "════════════════════════════════════════════════════════════════"
        exit 1
    fi
}

# ═════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ═════════════════════════════════════════════════════════════════════════════

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
