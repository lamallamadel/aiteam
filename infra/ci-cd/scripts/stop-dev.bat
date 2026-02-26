@echo off
REM Stop development services (Windows)
REM Usage: stop-dev.bat

setlocal enabledelayedexpansion
chcp 65001 >nul

echo.
echo Stopping AITEAM Development Services
echo =============================================================================
echo.

REM Get script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%..\..\..\"
set PROJECT_ROOT=%cd%
set DEV_DIR=%PROJECT_ROOT%\infra\deployments\dev

cd /d "%DEV_DIR%"

echo Stopping Docker Compose services...
echo.

docker compose -p aiteam-dev -f docker-compose.yml down

echo.
echo OK: Development services stopped
echo.

pause
