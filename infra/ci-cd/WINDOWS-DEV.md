# AITEAM Development on Windows

Quick start scripts for Windows development.

## 🚀 Quick Start

### Option 1: Batch Script (Recommended for Windows)

```batch
# Navigate to project root
cd C:\Users\PRO\work\aiteam

# Start development
infra\ci-cd\scripts\deploy-dev.bat

# In another terminal, view logs
infra\ci-cd\scripts\logs-dev.bat

# Check status
infra\ci-cd\scripts\status-dev.bat

# Stop services
infra\ci-cd\scripts\stop-dev.bat
```

### Option 2: PowerShell

```powershell
# Navigate to project root
cd C:\Users\PRO\work\aiteam

# Start development
.\infra\ci-cd\scripts\deploy-dev.ps1

# View logs
docker compose -f infra\deployments\dev\docker-compose.yml logs -f

# Stop services
docker compose -f infra\deployments\dev\docker-compose.yml down
```

### Option 3: Manual Docker Compose

```batch
cd C:\Users\PRO\work\aiteam\infra\deployments\dev

# Start (build and run)
docker compose -p aiteam-dev -f docker-compose.yml --env-file .env.dev up -d --build

# View logs
docker compose -p aiteam-dev -f docker-compose.yml logs -f

# Stop
docker compose -p aiteam-dev -f docker-compose.yml down
```

## 📋 Available Scripts

All scripts are in `infra\ci-cd\scripts\`

| Script | Purpose |
|--------|---------|
| `deploy-dev.bat` | Start development (build + run) |
| `deploy-dev.ps1` | PowerShell version of deploy |
| `stop-dev.bat` | Stop all services |
| `logs-dev.bat` | View logs (all or specific service) |
| `status-dev.bat` | Check service status and health |

## 🔗 Access Points

Once running:

- **Backend API**: http://localhost:8080
  - Swagger UI: http://localhost:8080/swagger-ui.html
  - Health: http://localhost:8080/actuator/health
  
- **Frontend**: http://localhost:4200
  
- **Database**: localhost:5432
  - User: ai_dev
  - Password: dev_password_change_me_12345
  - Database: ai_dev
  
- **Debug Port**: localhost:5005
  - For IDE debugging (IntelliJ, VS Code, Eclipse)

## 📝 Common Tasks

### View All Logs

```batch
infra\ci-cd\scripts\logs-dev.bat
```

### View Specific Service Logs

```batch
# Backend only
infra\ci-cd\scripts\logs-dev.bat ai-orchestrator

# Frontend only
infra\ci-cd\scripts\logs-dev.bat ai-dashboard

# Database only
infra\ci-cd\scripts\logs-dev.bat ai-db
```

### Check Status

```batch
infra\ci-cd\scripts\status-dev.bat
```

### Stop Services

```batch
infra\ci-cd\scripts\stop-dev.bat
```

### Restart a Service

```batch
cd infra\deployments\dev

# Restart backend
docker compose -p aiteam-dev -f docker-compose.yml restart ai-orchestrator

# Restart frontend
docker compose -p aiteam-dev -f docker-compose.yml restart ai-dashboard

# Restart database
docker compose -p aiteam-dev -f docker-compose.yml restart ai-db
```

### Rebuild Services

```batch
cd infra\deployments\dev

docker compose -p aiteam-dev -f docker-compose.yml up -d --build
```

## 🐛 Troubleshooting

### Docker Desktop Not Running

**Error:** `error during connect: This error may indicate the docker daemon is not running`

**Solution:**
1. Open Docker Desktop application
2. Wait for it to start (check system tray)
3. Try again

### Port Already in Use

**Error:** `bind: address already in use`

**Solution:**
```batch
# Find what's using port 8080
netstat -ano | findstr :8080

# Kill process (replace PID with actual process ID)
taskkill /PID <PID> /F

# Then try starting again
infra\ci-cd\scripts\deploy-dev.bat
```

### Services Won't Start

```batch
# Check logs
infra\ci-cd\scripts\logs-dev.bat

# Try rebuilding
cd infra\deployments\dev
docker compose -p aiteam-dev -f docker-compose.yml up -d --build

# Clean and restart
docker compose -p aiteam-dev -f docker-compose.yml down
docker system prune -a
docker compose -p aiteam-dev -f docker-compose.yml up -d --build
```

### Database Connection Failed

```batch
# Check if database is healthy
infra\ci-cd\scripts\status-dev.bat

# Check database logs
infra\ci-cd\scripts\logs-dev.bat ai-db

# Restart database
cd infra\deployments\dev
docker compose -p aiteam-dev -f docker-compose.yml restart ai-db
```

### Frontend Not Compiling

```batch
# Check frontend logs
infra\ci-cd\scripts\logs-dev.bat ai-dashboard

# Clear and rebuild
cd infra\deployments\dev
docker compose -p aiteam-dev -f docker-compose.yml restart ai-dashboard
```

### Backend Not Starting

```batch
# Check backend logs
infra\ci-cd\scripts\logs-dev.bat ai-orchestrator

# Check health
curl http://localhost:8080/actuator/health

# Restart
cd infra\deployments\dev
docker compose -p aiteam-dev -f docker-compose.yml restart ai-orchestrator
```

## 💡 Tips

### Hot Reload Frontend

Frontend code is automatically reloaded:
- Edit files in `frontend\src\`
- Changes appear in browser without restart

### Remote Debug Backend

Connect IDE debugger to `localhost:5005`:

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Add → Remote JVM Debug
3. Host: localhost
4. Port: 5005

**VS Code:**
1. Install "Debugger for Java"
2. Add configuration:
```json
{
  "name": "Attach to Java",
  "type": "java",
  "name": "Attach",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

### View Database with pgAdmin

```batch
# Start pgAdmin in another container
docker run -d -p 5050:80 -e PGADMIN_DEFAULT_EMAIL=admin@admin.com -e PGADMIN_DEFAULT_PASSWORD=admin dpage/pgadmin4
```

Then access http://localhost:5050 and connect to:
- Host: host.docker.internal (or docker host IP)
- Port: 5432
- User: ai_dev
- Password: dev_password_change_me_12345

## 🔄 Typical Dev Workflow

```batch
# 1. Start development environment
infra\ci-cd\scripts\deploy-dev.bat

# 2. In another terminal, watch logs
infra\ci-cd\scripts\logs-dev.bat ai-orchestrator

# 3. Make code changes
#    - Frontend: edit frontend\src\...
#    - Backend: edit ai-orchestrator\src\...

# 4. Frontend hot-reloads automatically
#    Backend may need restart if major changes
cd infra\deployments\dev
docker compose -p aiteam-dev -f docker-compose.yml restart ai-orchestrator

# 5. Check status
infra\ci-cd\scripts\status-dev.bat

# 6. When done, stop
infra\ci-cd\scripts\stop-dev.bat
```

## ✅ Checklist

Before starting:
- [ ] Docker Desktop installed
- [ ] Docker Desktop running (check system tray)
- [ ] Git cloned to C:\Users\PRO\work\aiteam
- [ ] Ports 5432, 8080, 4200, 5005 available
- [ ] At least 4GB RAM available for Docker

## 📚 Related Documentation

- `infra/ci-cd/README.md` - Full CI/CD documentation
- `DEPLOYMENT.md` - Production deployment
- `DEV_SETUP.md` - Development setup (Unix/Linux)

---

**Questions?** Check the main docs or reach out to the team.
