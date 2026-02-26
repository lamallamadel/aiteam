@echo off
REM Check development status (Windows)
REM Usage: status-dev.bat

setlocal enabledelayedexpansion
chcp 65001 >nul

echo.
echo AITEAM Development Status
echo =============================================================================
echo.

REM Get script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%..\..\..\"
set PROJECT_ROOT=%cd%
set DEV_DIR=%PROJECT_ROOT%\infra\deployments\dev

cd /d "%DEV_DIR%"

echo Docker Compose Services:
echo.
docker compose -p aiteam-dev -f docker-compose.yml ps

echo.
echo Access Points:
echo    - Backend API:     http://localhost:8080
echo    - Frontend:        http://localhost:4200
echo    - Database:        localhost:5432
echo    - Debug Port:      localhost:5005
echo.

echo Health Checks:
echo.

REM Check backend
echo Checking Backend API...
curl -s http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo    X Backend API: Not responding
) else (
    echo    OK Backend API: Healthy
)

REM Check frontend
echo Checking Frontend...
curl -s http://localhost:4200 >nul 2>&1
if errorlevel 1 (
    echo    X Frontend: Not responding
) else (
    echo    OK Frontend: Healthy
)

REM Check database
echo Checking Database...
docker compose -p aiteam-dev -f docker-compose.yml exec -T ai-db pg_isready -U ai_dev >nul 2>&1
if errorlevel 1 (
    echo    X Database: Not responding
) else (
    echo    OK Database: Healthy
)

echo.
echo Useful commands:
echo    deploy-dev.bat      - Start development environment
echo    stop-dev.bat        - Stop all services
echo    logs-dev.bat        - View logs
echo    status-dev.bat      - Check status (this command)
echo.
