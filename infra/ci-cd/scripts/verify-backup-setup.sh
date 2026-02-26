#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# BACKUP & ROLLBACK SETUP VERIFICATION SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# This script verifies that backup and rollback systems are properly configured
# Usage: ./infra/ci-cd/scripts/verify-backup-setup.sh [dev|prod]
# ═════════════════════════════════════════════════════════════════════════════

set -e

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

# Test results
TESTS_PASSED=0
TESTS_FAILED=0
WARNINGS=0

# ═════════════════════════════════════════════════════════════════════════════
# HELPER FUNCTIONS
# ═════════════════════════════════════════════════════════════════════════════

log() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    WARNINGS=$((WARNINGS + 1))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

# ═════════════════════════════════════════════════════════════════════════════
# VERIFICATION TESTS
# ═════════════════════════════════════════════════════════════════════════════

test_script_permissions() {
    log "Testing script permissions..."
    
    if [[ -x "$SCRIPT_DIR/backup.sh" ]]; then
        log_success "backup.sh is executable"
    else
        log_error "backup.sh is not executable"
    fi
    
    if [[ -x "$SCRIPT_DIR/rollback.sh" ]]; then
        log_success "rollback.sh is executable"
    else
        log_error "rollback.sh is not executable"
    fi
    
    if [[ -x "$SCRIPT_DIR/deploy-prod.sh" ]]; then
        log_success "deploy-prod.sh is executable"
    else
        log_error "deploy-prod.sh is not executable"
    fi
    
    if [[ -x "$PROJECT_ROOT/infra/deployments/prod/backup-cron.sh" ]]; then
        log_success "backup-cron.sh is executable"
    else
        log_error "backup-cron.sh is not executable"
    fi
}

test_backup_directories() {
    log "Testing backup directory structure..."
    
    local backup_dir="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
    
    if [[ -d "$backup_dir" ]]; then
        log_success "Backup base directory exists: $backup_dir"
    else
        log_warning "Backup directory doesn't exist (will be created on first backup): $backup_dir"
    fi
    
    # Check subdirectories (not critical, created automatically)
    for subdir in daily weekly monthly predeployment rollback; do
        if [[ -d "$backup_dir/$subdir" ]]; then
            log_success "Backup subdirectory exists: $subdir"
        else
            log "Subdirectory will be created on first use: $subdir"
        fi
    done
}

test_database_connection() {
    log "Testing database connection..."
    
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        local db_container="aiteam_db_prod"
        local db_user="aiteam_prod_user"
        local db_name="aiteam_prod"
    else
        local db_container="ai-db"
        local db_user="ai"
        local db_name="ai"
    fi
    
    if docker ps --format '{{.Names}}' | grep -q "^${db_container}$"; then
        log_success "Database container is running: $db_container"
        
        if docker exec "$db_container" pg_isready -U "$db_user" > /dev/null 2>&1; then
            log_success "Database is accepting connections"
        else
            log_error "Database is not ready"
        fi
        
        # Test pg_dump command
        if docker exec "$db_container" pg_dump --version > /dev/null 2>&1; then
            log_success "pg_dump is available"
        else
            log_error "pg_dump is not available"
        fi
    else
        log_error "Database container is not running: $db_container"
    fi
}

test_docker_compose() {
    log "Testing docker-compose configuration..."
    
    if command -v docker-compose &> /dev/null; then
        log_success "docker-compose is installed"
        
        if [[ "$ENVIRONMENT" == "prod" ]]; then
            local compose_file="$PROJECT_ROOT/infra/deployments/prod/docker-compose.yml"
        else
            local compose_file="$PROJECT_ROOT/infra/docker-compose.ai.yml"
        fi
        
        if [[ -f "$compose_file" ]]; then
            log_success "docker-compose file exists: $compose_file"
            
            if docker-compose -f "$compose_file" config > /dev/null 2>&1; then
                log_success "docker-compose configuration is valid"
            else
                log_error "docker-compose configuration has errors"
            fi
        else
            log_error "docker-compose file not found: $compose_file"
        fi
    else
        log_error "docker-compose is not installed"
    fi
}

test_optional_tools() {
    log "Testing optional tools..."
    
    if command -v aws &> /dev/null; then
        log_success "AWS CLI is installed (S3 backups available)"
    else
        log "AWS CLI not installed (S3 backups not available)"
    fi
    
    if command -v mail &> /dev/null; then
        log_success "mail command is available (email notifications enabled)"
    else
        log "mail command not available (email notifications disabled)"
    fi
    
    if command -v gzip &> /dev/null; then
        log_success "gzip is available"
    else
        log_error "gzip is not available (required for backups)"
    fi
    
    if command -v gunzip &> /dev/null; then
        log_success "gunzip is available (backup verification enabled)"
    else
        log_warning "gunzip not available (backup verification disabled)"
    fi
}

test_deployment_history() {
    log "Testing deployment history..."
    
    local tags_file="$PROJECT_ROOT/backups/rollback/deployment_tags.log"
    
    if [[ -f "$tags_file" ]]; then
        local entry_count=$(wc -l < "$tags_file")
        if [[ $entry_count -gt 0 ]]; then
            log_success "Deployment history exists with $entry_count entries"
        else
            log_warning "Deployment history file is empty"
        fi
    else
        log "No deployment history yet (will be created on first deployment)"
    fi
}

test_environment_variables() {
    log "Testing environment variables..."
    
    # Optional but recommended
    if [[ -n "${GITHUB_REPO:-}" ]]; then
        log_success "GITHUB_REPO is set: $GITHUB_REPO"
    else
        log_warning "GITHUB_REPO not set (required for image pull during rollback)"
    fi
    
    if [[ -n "${S3_BUCKET:-}" ]]; then
        log_success "S3_BUCKET is set: $S3_BUCKET"
    else
        log "S3_BUCKET not set (local backups only)"
    fi
    
    if [[ -n "${HEALTHCHECK_URL:-}" ]]; then
        log_success "HEALTHCHECK_URL is set (monitoring enabled)"
    else
        log "HEALTHCHECK_URL not set (no healthcheck monitoring)"
    fi
    
    if [[ -n "${ADMIN_EMAIL:-}" ]]; then
        log_success "ADMIN_EMAIL is set: $ADMIN_EMAIL"
    else
        log "ADMIN_EMAIL not set (no email notifications)"
    fi
}

test_disk_space() {
    log "Testing disk space..."
    
    local backup_dir="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
    
    if [[ -d "$backup_dir" ]]; then
        local available_space=$(df "$backup_dir" | tail -1 | awk '{print $4}')
        local required_space=1048576  # 1GB in KB
        
        if [[ $available_space -gt $required_space ]]; then
            local available_gb=$((available_space / 1024 / 1024))
            log_success "Sufficient disk space available: ${available_gb}GB"
        else
            local available_gb=$((available_space / 1024 / 1024))
            log_warning "Low disk space: ${available_gb}GB available, recommend at least 1GB"
        fi
    else
        log "Backup directory doesn't exist yet, checking parent directory"
        local parent_dir=$(dirname "$backup_dir")
        if [[ -d "$parent_dir" ]]; then
            local available_space=$(df "$parent_dir" | tail -1 | awk '{print $4}')
            local available_gb=$((available_space / 1024 / 1024))
            log "Available space in parent directory: ${available_gb}GB"
        fi
    fi
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN VERIFICATION LOGIC
# ═════════════════════════════════════════════════════════════════════════════

main() {
    echo "════════════════════════════════════════════════════════════════"
    log "ATLASIA BACKUP & ROLLBACK VERIFICATION"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    log "Environment: $ENVIRONMENT"
    log "Project Root: $PROJECT_ROOT"
    echo ""
    
    # Run all tests
    test_script_permissions
    echo ""
    
    test_backup_directories
    echo ""
    
    test_database_connection
    echo ""
    
    test_docker_compose
    echo ""
    
    test_optional_tools
    echo ""
    
    test_deployment_history
    echo ""
    
    test_environment_variables
    echo ""
    
    test_disk_space
    echo ""
    
    # Summary
    echo "════════════════════════════════════════════════════════════════"
    log "VERIFICATION SUMMARY"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    echo -e "${GREEN}Passed:${NC}   $TESTS_PASSED"
    echo -e "${YELLOW}Warnings:${NC} $WARNINGS"
    echo -e "${RED}Failed:${NC}   $TESTS_FAILED"
    echo ""
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        echo -e "${GREEN}✅ All critical tests passed!${NC}"
        echo ""
        echo "Next steps:"
        echo "  1. Run a test backup: ./infra/ci-cd/scripts/backup.sh $ENVIRONMENT"
        echo "  2. Setup cron job: crontab -e"
        echo "  3. Configure S3 backups (optional): export S3_BUCKET=my-bucket"
        echo "  4. Setup monitoring (optional): export HEALTHCHECK_URL=https://hc-ping.com/xxx"
        echo ""
        exit 0
    else
        echo -e "${RED}❌ Some tests failed. Please fix the issues above.${NC}"
        echo ""
        echo "Common fixes:"
        echo "  • Set permissions: ./infra/ci-cd/scripts/setup-permissions.sh"
        echo "  • Start database: docker-compose -f infra/deployments/prod/docker-compose.yml up -d ai-db"
        echo "  • Install docker-compose: curl -L https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose"
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
