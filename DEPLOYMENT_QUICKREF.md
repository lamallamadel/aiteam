# AITEAM DEPLOYMENT QUICK REFERENCE

## 📍 One-Command Cheat Sheet

### First-Time Setup
```bash
# 1. SSH into production server
ssh root@your-server-ip

# 2. Clone and prepare
git clone https://github.com/your-org/aiteam.git && cd aiteam

# 3. Setup environment
cp .env.prod.template .env.prod
# Edit .env.prod with your secrets

# 4. Deploy
chmod +x scripts/deploy.sh
./scripts/deploy.sh prod
```

### Regular Operations
```bash
# View logs (all services)
docker-compose -f docker-compose.prod.yml logs -f

# View specific service logs
docker-compose -f docker-compose.prod.yml logs -f ai-orchestrator

# Check status
docker-compose -f docker-compose.prod.yml ps

# Restart a service
docker-compose -f docker-compose.prod.yml restart ai-orchestrator

# Stop all services
docker-compose -f docker-compose.prod.yml down

# Start all services
docker-compose -f docker-compose.prod.yml up -d

# Pull latest images and restart
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

### Backup & Recovery
```bash
# Manual backup
docker-compose -f docker-compose.prod.yml exec ai-db pg_dump -U aiteam aiteam | gzip > backup.sql.gz

# Automatic rollback (restores from latest backup)
./scripts/rollback.sh prod

# Restore specific backup
zcat backup_20260221_103000.sql.gz | docker-compose -f docker-compose.prod.yml exec -T ai-db psql -U aiteam -d aiteam
```

### Monitoring
```bash
# Real-time resource usage
docker stats --no-stream

# Check disk space
df -h

# Health endpoints
curl https://api.yourdomain.com/health          # Frontend
curl https://api.yourdomain.com/api/actuator/health  # Backend
docker-compose -f docker-compose.prod.yml exec ai-db pg_isready  # Database
```

### Updates
```bash
# Deploy latest version (from GitHub Actions)
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d

# Renew SSL certificate
sudo certbot renew

# Update Docker/system packages
sudo apt update && sudo apt upgrade -y
```

### Troubleshooting
```bash
# Show all errors
docker-compose -f docker-compose.prod.yml logs | grep ERROR

# Database connection test
docker-compose -f docker-compose.prod.yml exec ai-db psql -U aiteam -c "SELECT 1"

# Check environment variables
docker-compose -f docker-compose.prod.yml config

# Remove unused images/containers to free space
docker system prune -a

# Full system status
docker-compose -f docker-compose.prod.yml ps
docker stats --no-stream
df -h
```

---

## 📋 Environment Variables

| Variable | Where to Set | How to Generate |
|----------|--------------|-----------------|
| `DB_PASSWORD` | `.env.prod` | `openssl rand -base64 32` |
| `ORCHESTRATOR_TOKEN` | `.env.prod` | `openssl rand -base64 32` |
| `LLM_API_KEY` | `.env.prod` | From your LLM provider |
| `GITHUB_REPO` | `.env.prod` | Your org/repo name |
| `DOMAIN` | `.env.prod` | Your domain name |

---

## 🔧 Critical Files

```
aiteam/
├── .github/workflows/build-and-push.yml    # CI/CD (auto-build on push)
├── docker-compose.prod.yml                  # Production config
├── .env.prod                                # Secrets (NEVER commit)
├── nginx-prod.conf                          # Reverse proxy config
├── scripts/
│   ├── deploy.sh                           # Deploy to production
│   └── rollback.sh                          # Rollback to previous version
├── DEPLOYMENT.md                            # Full guide
└── DEV_SETUP.md                            # Development setup
```

---

## 🚨 Emergency Procedures

### Service Down
```bash
# 1. Check what failed
docker-compose -f docker-compose.prod.yml logs | tail -50

# 2. Restart everything
docker-compose -f docker-compose.prod.yml restart

# 3. If still down, rollback
./scripts/rollback.sh prod
```

### Database Corrupted
```bash
# 1. Stop services
docker-compose -f docker-compose.prod.yml down

# 2. Restore from backup
./scripts/rollback.sh prod
```

### Server Out of Disk
```bash
# 1. Check usage
docker system df

# 2. Clean up
docker system prune -a  # Removes unused images

# 3. Monitor
docker stats --no-stream
```

### SSL Certificate Expired
```bash
# 1. Renew immediately
sudo certbot renew --force-renewal

# 2. Restart nginx
docker-compose -f docker-compose.prod.yml restart ai-dashboard
```

---

## 📊 Key Metrics to Monitor

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU Usage | > 80% | Check logs for slow queries |
| Memory Usage | > 85% | Restart service or scale up |
| Disk Usage | > 90% | Run `docker system prune -a` |
| HTTP Errors | > 5/min | Check backend logs |
| Response Time | > 2s | Optimize queries or scale |

---

## 📞 Quick Links

- **Frontend:** https://api.yourdomain.com
- **Backend API Docs:** https://api.yourdomain.com/swagger-ui.html
- **Health Check:** https://api.yourdomain.com/health
- **Logs:** `docker-compose -f docker-compose.prod.yml logs -f`

---

## ✅ Common Tasks

| Task | Command |
|------|---------|
| Deploy latest | `docker-compose -f docker-compose.prod.yml pull && up -d` |
| Check status | `docker-compose -f docker-compose.prod.yml ps` |
| View logs | `docker-compose -f docker-compose.prod.yml logs -f` |
| Backup DB | `./scripts/deploy.sh prod` (auto-backs up) |
| Restore DB | `./scripts/rollback.sh prod` |
| Restart service | `docker-compose -f docker-compose.prod.yml restart ai-orchestrator` |
| Free disk space | `docker system prune -a` |
| Update SSL | `sudo certbot renew` |
| SSH to server | `ssh root@your-server-ip` |

---

**For detailed documentation, see [DEPLOYMENT.md](./DEPLOYMENT.md)**
