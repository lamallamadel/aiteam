@echo off
REM ═════════════════════════════════════════════════════════════════════════════
REM DEVELOPMENT DEPLOYMENT SCRIPT FOR WINDOWS
REM ═════════════════════════════════════════════════════════════════════════════
REM Usage: deploy-dev.bat
REM ═════════════════════════════════════════════════════════════════════════════

setlocal enabledelayedexpansion
chcp 65001 >nul

echo.
echo Development Deployment (Windows)
echo =============================================================================
echo.

REM Get script directory
set SCRIPT_DIR=%~dp0
REM Navigate to project root (from scripts folder)
cd /d "%SCRIPT_DIR%..\..\..\"
set PROJECT_ROOT=%cd%
set DEV_DIR=%PROJECT_ROOT%\infra\deployments\dev

echo Project Root: %PROJECT_ROOT%
echo Dev Config: %DEV_DIR%
echo.

REM Check if Docker is installed
echo Checking Docker installation...
docker --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not installed or not in PATH
    echo Please install Docker Desktop from https://www.docker.com/products/docker-desktop
    exit /b 1
)
echo OK: Docker found
echo.

REM Check if Docker Compose is installed
echo Checking Docker Compose...
docker compose version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker Compose is not available
    echo Please ensure Docker Desktop is running and has Compose plugin installed
    exit /b 1
)
echo OK: Docker Compose found
echo.

REM Check if .env.dev exists
if not exist "%DEV_DIR%\.env.dev" (
    echo ERROR: .env.dev not found
    exit /b 1
)

echo Loading .env.dev...
echo OK: Environment variables ready
echo.

REM Check if port 5432 is in use
echo Checking if port 5432 is available...
netstat -ano | findstr :5432 >nul 2>&1
if not errorlevel 1 (
    echo WARNING: Port 5432 is already in use!
    echo.
    echo Do you want to:
    echo   1. Kill the process using port 5432 and continue
    echo   2. Stop existing containers and continue
    echo   3. Exit
    echo.
    set /p choice="Enter your choice (1-3): "
    
    if "!choice!"=="1" (
        for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5432') do (
            taskkill /PID %%a /F >nul 2>&1
        )
        echo Killed process on port 5432
    ) else if "!choice!"=="2" (
        echo Stopping existing Docker containers...
        docker compose -p aiteam-dev -f "%DEV_DIR%\docker-compose.yml" down >nul 2>&1
        echo Stopped containers
    ) else (
        exit /b 1
    )
)

echo.
echo Starting development services...
echo.

cd /d "%DEV_DIR%"

REM Build and start all services
docker compose -p aiteam-dev ^
    -f docker-compose.yml ^
    --env-file .env.dev ^
    up -d --build

if errorlevel 1 (
    echo.
    echo ERROR: Failed to start services
    echo Check Docker is running and ports are available
    exit /b 1
)

echo.
echo OK: Development environment started
echo.

REM Wait for services
echo Waiting for services to be healthy...
timeout /t 5 /nobreak >nul

REM Show service status
echo.
echo Service Status:
echo.
docker compose -p aiteam-dev -f docker-compose.yml ps

echo.
echo Access Points:
echo   - Backend API:     http://localhost:8080
echo   - Frontend:        http://localhost:4200
echo   - Database:        localhost:5432
echo   - Debug Port:      localhost:5005
echo.

echo Useful commands:
echo   View logs:       docker compose -f "%DEV_DIR%\docker-compose.yml" logs -f
echo   Stop:            docker compose -f "%DEV_DIR%\docker-compose.yml" down
echo   Rebuild:         docker compose -f "%DEV_DIR%\docker-compose.yml" up -d --build
echo   View backend:    docker compose -f "%DEV_DIR%\docker-compose.yml" logs -f ai-orchestrator
echo   View frontend:   docker compose -f "%DEV_DIR%\docker-compose.yml" logs -f ai-dashboard
echo   View database:   docker compose -f "%DEV_DIR%\docker-compose.yml" logs -f ai-db
echo.

echo OK: Development environment is ready!
echo.

pause
