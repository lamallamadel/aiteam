# ═════════════════════════════════════════════════════════════════════════════
# DEVELOPMENT DEPLOYMENT SCRIPT FOR WINDOWS (PowerShell)
# ═════════════════════════════════════════════════════════════════════════════
# Usage: .\deploy-dev.ps1
# ═════════════════════════════════════════════════════════════════════════════

param(
    [switch]$NoWait = $false
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "🚀 AITEAM Development Deployment (Windows PowerShell)" -ForegroundColor Cyan
Write-Host "═════════════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Get script directory
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_ROOT = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $SCRIPT_DIR))
$DEV_DIR = Join-Path $PROJECT_ROOT "infra" "deployments" "dev"

Write-Host "📍 Project Root: $PROJECT_ROOT"
Write-Host "📍 Dev Config: $DEV_DIR"
Write-Host ""

# Check if Docker is installed
Write-Host "🔍 Checking Docker installation..." -ForegroundColor Yellow
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker found: $dockerVersion"
} catch {
    Write-Host "❌ Docker is not installed or not in PATH" -ForegroundColor Red
    Write-Host "   Please install Docker Desktop from https://www.docker.com/products/docker-desktop"
    exit 1
}
Write-Host ""

# Check if Docker Compose is available
Write-Host "🔍 Checking Docker Compose..." -ForegroundColor Yellow
try {
    $composeVersion = docker compose version
    Write-Host "✅ Docker Compose found"
} catch {
    Write-Host "❌ Docker Compose is not available" -ForegroundColor Red
    Write-Host "   Please ensure Docker Desktop is running and has Compose plugin installed"
    exit 1
}
Write-Host ""

# Check if .env.dev exists
if (-not (Test-Path "$DEV_DIR\.env.dev")) {
    Write-Host "⚠️  .env.dev not found" -ForegroundColor Yellow
    Write-Host "   Environment file missing"
    exit 1
}

Write-Host "📋 Loading .env.dev..." -ForegroundColor Yellow
Write-Host "✅ Environment variables ready"
Write-Host ""

# Start services
Write-Host "🐳 Starting development services..." -ForegroundColor Cyan
Write-Host ""

Push-Location $DEV_DIR

try {
    Write-Host "Starting services with Docker Compose..." -ForegroundColor Yellow
    Write-Host ""
    
    & docker compose -p aiteam-dev `
        -f docker-compose.yml `
        --env-file .env.dev `
        up -d --build
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "❌ Failed to start services" -ForegroundColor Red
        Write-Host "   Check Docker is running and ports are available"
        exit 1
    }
    
    Write-Host ""
    Write-Host "✅ Development environment started" -ForegroundColor Green
    Write-Host ""
    
    # Wait for services
    if (-not $NoWait) {
        Write-Host "⏳ Waiting for services to be healthy..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3
    }
    
    # Show service status
    Write-Host ""
    Write-Host "📊 Service Status:" -ForegroundColor Cyan
    Write-Host ""
    & docker compose -p aiteam-dev -f docker-compose.yml ps
    
    Write-Host ""
    Write-Host "🔗 Access Points:" -ForegroundColor Cyan
    Write-Host "   • Backend API:     http://localhost:8080"
    Write-Host "   • Frontend:        http://localhost:4200"
    Write-Host "   • Database:        localhost:5432"
    Write-Host "   • Debug Port:      localhost:5005"
    Write-Host ""
    
    Write-Host "📝 Useful commands:" -ForegroundColor Yellow
    Write-Host "   View logs:       docker compose -f '$DEV_DIR\docker-compose.yml' logs -f"
    Write-Host "   Stop:            docker compose -f '$DEV_DIR\docker-compose.yml' down"
    Write-Host "   Rebuild:         docker compose -f '$DEV_DIR\docker-compose.yml' up -d --build"
    Write-Host "   View backend:    docker compose -f '$DEV_DIR\docker-compose.yml' logs -f ai-orchestrator"
    Write-Host "   View frontend:   docker compose -f '$DEV_DIR\docker-compose.yml' logs -f ai-dashboard"
    Write-Host "   View database:   docker compose -f '$DEV_DIR\docker-compose.yml' logs -f ai-db"
    Write-Host ""
    
    Write-Host "✅ Development environment is ready!" -ForegroundColor Green
    Write-Host ""
    
} finally {
    Pop-Location
}
