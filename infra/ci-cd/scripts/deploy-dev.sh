#!/bin/bash

# ═════════════════════════════════════════════════════════════════════════════
# DEVELOPMENT DEPLOYMENT SCRIPT
# ═════════════════════════════════════════════════════════════════════════════
# Usage: ./infra/ci-cd/scripts/deploy-dev.sh
# ═════════════════════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"
DEV_DIR="$PROJECT_ROOT/infra/deployments/dev"

echo "🚀 AITEAM Development Deployment"
echo "================================="
echo "📍 Project Root: $PROJECT_ROOT"
echo "📍 Dev Config: $DEV_DIR"
echo ""

# Load environment variables
if [ -f "$DEV_DIR/.env.dev" ]; then
    echo "📋 Loading .env.dev..."
    set -a
    source "$DEV_DIR/.env.dev"
    set +a
else
    echo "⚠️  .env.dev not found at $DEV_DIR/.env.dev"
    echo "    Creating from template..."
    cp "$DEV_DIR/.env.dev.example" "$DEV_DIR/.env.dev" 2>/dev/null || echo "    Please configure .env.dev manually"
    exit 1
fi

# Start services
echo ""
echo "🐳 Starting development services..."
cd "$DEV_DIR"

# Start with profiles: backend + frontend (full stack)
docker-compose -p aiteam-dev \
    -f docker-compose.yml \
    --env-file .env.dev \
    up -d --build

echo ""
echo "✅ Development environment started"
echo ""
echo "📊 Service Status:"
docker-compose -p aiteam-dev \
    -f docker-compose.yml \
    ps

echo ""
echo "🔗 Access Points:"
echo "   • Backend API:     http://localhost:8080"
echo "   • Frontend:        http://localhost:4200"
echo "   • Database:        localhost:5432"
echo "   • Debug Port:      localhost:5005"
echo ""
echo "📝 Useful commands:"
echo "   View logs:     docker-compose -f $DEV_DIR/docker-compose.yml logs -f"
echo "   Stop:          docker-compose -f $DEV_DIR/docker-compose.yml down"
echo "   Rebuild:       docker-compose -f $DEV_DIR/docker-compose.yml up -d --build"
echo ""
