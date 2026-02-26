@echo off
REM View development logs (Windows)
REM Usage: logs-dev.bat [service]

setlocal enabledelayedexpansion
chcp 65001 >nul

set SERVICE=%1

if "%SERVICE%"=="" (
    set SERVICE_TEXT=all services
) else (
    set SERVICE_TEXT=%SERVICE%
)

echo.
echo Viewing logs for: %SERVICE_TEXT%
echo =============================================================================
echo.

REM Get script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%..\..\..\"
set PROJECT_ROOT=%cd%
set DEV_DIR=%PROJECT_ROOT%\infra\deployments\dev

cd /d "%DEV_DIR%"

if "%SERVICE%"=="" (
    docker compose -p aiteam-dev -f docker-compose.yml logs -f
) else (
    docker compose -p aiteam-dev -f docker-compose.yml logs -f %SERVICE%
)
