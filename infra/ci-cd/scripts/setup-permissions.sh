#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# SETUP SCRIPT PERMISSIONS
# ═════════════════════════════════════════════════════════════════════════════
# This script makes all backup and deployment scripts executable
# Usage: ./infra/ci-cd/scripts/setup-permissions.sh
# ═════════════════════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"

echo "════════════════════════════════════════════════════════════════"
echo "Setting up script permissions for ATLASIA"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Make CI/CD scripts executable
echo "🔧 Setting permissions for CI/CD scripts..."
chmod +x "$SCRIPT_DIR/backup.sh"
chmod +x "$SCRIPT_DIR/rollback.sh"
chmod +x "$SCRIPT_DIR/deploy-prod.sh"
chmod +x "$SCRIPT_DIR/deploy-dev.sh"
echo "✅ CI/CD scripts: executable"

# Make deployment scripts executable
echo "🔧 Setting permissions for deployment scripts..."
chmod +x "$PROJECT_ROOT/infra/deployments/prod/backup-cron.sh"
echo "✅ Deployment scripts: executable"

# Make infrastructure scripts executable
echo "🔧 Setting permissions for infrastructure scripts..."
chmod +x "$PROJECT_ROOT/infra/vault-init.sh" 2>/dev/null || true
chmod +x "$PROJECT_ROOT/infra/gen-ssl-cert.sh" 2>/dev/null || true
chmod +x "$PROJECT_ROOT/infra/postgres-ssl-init.sh" 2>/dev/null || true
echo "✅ Infrastructure scripts: executable"

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "✅ All script permissions set successfully"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Available scripts:"
echo "  • Backup:     ./infra/ci-cd/scripts/backup.sh [dev|prod]"
echo "  • Rollback:   ./infra/ci-cd/scripts/rollback.sh [dev|prod]"
echo "  • Deploy Dev: ./infra/ci-cd/scripts/deploy-dev.sh"
echo "  • Deploy Prod: ./infra/ci-cd/scripts/deploy-prod.sh"
echo "  • Cron Backup: ./infra/deployments/prod/backup-cron.sh"
echo ""
