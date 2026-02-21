# AITEAM PRODUCTION DEPLOYMENT GUIDE

**Last Updated:** 2026-02-21  
**Status:** ✅ Ready for Single-Server Docker Compose Deployment

---

## 📋 Overview

This guide covers deploying aiteam to a single production server using Docker Compose. The setup is designed for:
- **Scale:** < 1,000 concurrent users
- **Uptime:** Best-effort (no HA required)
- **Cost:** < $100/month (single shared VM)
- **Complexity:** Low (simple Docker Compose)

### Architecture

```
┌─────────────────────────────────────────┐
│  Production Server (1x VM)              │
│  - 2-4 vCPU, 4-8GB RAM, 30-50GB SSD   │
│  - ~$10-30/month (DigitalOcean/AWS)   │
│                                         │
│  ┌────────────────────────────────┐   │
│  │  Docker Compose                │   │
│  │  ├─ ai-orchestrator (backend)  │   │
│  │  ├─ ai-dashboard (frontend)    │   │
│  │  ├─ ai-db (PostgreSQL)         │   │
│  │  └─ nginx (reverse proxy)      │   │
│  └────────────────────────────────┘   │
│                                         │
│  SSL (Let's Encrypt)                   │
│  Automated backups (daily)             │
└─────────────────────────────────────────┘
        ↑
        │ GitHub Actions CI/CD
   (builds & pushes images)
```

---

## 🚀 Quickstart Deployment

### Prerequisites

1. **Production Server**
   - Ubuntu 22.04 or later
   - 2+ vCPU, 4GB+ RAM, 30GB+ SSD
   - SSH access (key-based auth)
   - Public IP + domain name

2. **Install Docker & Docker Compose**
   ```bash
   # SSH into server
   ssh root@your-server-ip
   
   # Install Docker
   curl -fsSL https://get.docker.com -o get-docker.sh
   sudo sh get-docker.sh
   sudo usermod -aG docker $USER
   
   # Install Docker Compose
   sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   
   # Verify
   docker --version
   docker-compose --version
   ```

3. **SSL Certificate (Let's Encrypt)**
   ```bash
   # Install certbot
   sudo apt update
   sudo apt install certbot python3-certbot-nginx -y
   
   # Generate certificate
   sudo certbot certonly --standalone -d api.yourdomain.com --email your@email.com
   
   # Certificates stored in: /etc/letsencrypt/live/api.yourdomain.com/
   ```

### Deployment Steps

1. **Clone Repository**
   ```bash
   git clone https://github.com/your-org/aiteam.git
   cd aiteam
   ```

2. **Configure Environment**
   ```bash
   # Copy template
   cp .env.prod.template .env.prod
   
   # Edit with your values
   nano .env.prod
   ```

3. **Run Deployment**
   ```bash
   chmod +x scripts/deploy.sh
   ./scripts/deploy.sh prod
   ```

4. **Verify**
   ```bash
   # Check services are running
   docker-compose -f docker-compose.prod.yml ps
   
   # Test health endpoint
   curl https://api.yourdomain.com/health
   ```

---

## 📝 Configuration

### Environment Variables (.env.prod)

| Variable | Required | Example | Notes |
|----------|----------|---------|-------|
| `DB_PASSWORD` | ✅ Yes | `SecurePass123!@#` | Min 20 chars, strong password |
| `ORCHESTRATOR_TOKEN` | ✅ Yes | (32-char random) | Generate: `openssl rand -base64 32` |
| `LLM_API_KEY` | ✅ Yes | `sk-...` | Your LLM provider API key |
| `GITHUB_REPO` | ✅ Yes | `your-org/aiteam` | For image registry |
| `DOMAIN` | ✅ Yes | `api.example.com` | Your production domain |
| `SLACK_WEBHOOK` | ❌ No | (Slack webhook URL) | For deployment notifications |

### Generate Secure Passwords

```bash
# Linux/Mac
openssl rand -base64 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object {[byte](Get-Random -Min 0 -Max 256)}))
```

---

## 🔄 Deployment Pipeline (GitHub Actions)

The CI/CD pipeline automatically:
1. Builds Docker images on `git push` to main/develop
2. Pushes images to GitHub Container Registry (GHCR)
3. Scans for vulnerabilities (Trivy)
4. Sends Slack notification

### Setup GitHub Actions

1. **Create GitHub Secrets** (Settings > Secrets):
   - `DOCKER_USERNAME` = your GitHub username
   - `DOCKER_PASSWORD` = GitHub Personal Access Token (repo scope)
   - `SLACK_WEBHOOK` = (optional) Slack webhook URL

2. **Workflow File** (already created)
   - `.github/workflows/build-and-push.yml`

3. **Manual Deployment** (SSH to server and pull latest)
   ```bash
   docker-compose -f docker-compose.prod.yml pull
   docker-compose -f docker-compose.prod.yml up -d
   ```

---

## 📊 Monitoring & Logs

### View Logs

```bash
# All services
docker-compose -f docker-compose.prod.yml logs -f

# Specific service
docker-compose -f docker-compose.prod.yml logs -f ai-orchestrator
docker-compose -f docker-compose.prod.yml logs -f ai-dashboard
docker-compose -f docker-compose.prod.yml logs -f ai-db
```

### Health Checks

```bash
# Frontend
curl https://api.yourdomain.com/health

# Backend
curl https://api.yourdomain.com/api/actuator/health

# Database
docker-compose -f docker-compose.prod.yml exec ai-db pg_isready
```

### Resource Usage

```bash
# CPU/Memory per container
docker stats --no-stream

# Disk usage
df -h
du -sh /var/lib/docker
```

---

## 💾 Backups & Recovery

### Automated Backup (during deployment)

The `deploy.sh` script automatically:
1. Creates a database backup before deployment
2. Stores in `./backups/YYYYMMDD_HHMMSS/`
3. Compresses with gzip

### Manual Backup

```bash
# Backup database
docker-compose -f docker-compose.prod.yml exec ai-db pg_dump -U aiteam aiteam | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# Backup entire server (to local machine)
rsync -av -e ssh root@your-server:/opt/aiteam /local/backup/location
```

### Restore from Backup

```bash
# Use rollback script
./scripts/rollback.sh prod

# Or manual restore
zcat backup.sql.gz | docker-compose -f docker-compose.prod.yml exec -T ai-db psql -U aiteam -d aiteam
```

---

## 🚨 Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose -f docker-compose.prod.yml logs ai-orchestrator

# Check environment variables
docker-compose -f docker-compose.prod.yml config | grep -A 5 "environment:"

# Verify images exist
docker images | grep ghcr.io/your-org
```

### Out of Memory

```bash
# Check current usage
docker stats --no-stream

# Free space
docker system prune -a  # WARNING: removes unused images/containers

# Increase server RAM or optimize queries
```

### Database Connection Failed

```bash
# Check database is healthy
docker-compose -f docker-compose.prod.yml exec ai-db pg_isready

# Check credentials match .env.prod
grep "DB_" docker-compose.prod.yml
grep "DB_" .env.prod

# Reset database (dangerous!)
docker-compose -f docker-compose.prod.yml exec ai-db psql -U aiteam -d aiteam -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

### SSL Certificate Expired

```bash
# Renew certificate
sudo certbot renew

# Test renewal
sudo certbot renew --dry-run

# Auto-renewal every day (via cron)
sudo systemctl enable certbot.timer
```

---

## 🔐 Security Checklist

- [ ] Change all default passwords in `.env.prod`
- [ ] Store `.env.prod` in secure vault (not in Git)
- [ ] Enable SSH key-based auth only (disable password)
- [ ] Configure firewall (allow only 80/443)
- [ ] Enable automatic SSL renewal (certbot)
- [ ] Rotate secrets quarterly
- [ ] Enable database backups (daily)
- [ ] Monitor logs for suspicious activity
- [ ] Keep Docker/system packages updated

### Firewall Rules

```bash
# Allow SSH, HTTP, HTTPS only
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## 📈 Scaling Path

If you outgrow single-server setup:

1. **Add database replication** (PostgreSQL primary-replica)
2. **Add Redis caching** (improve performance)
3. **Multiple backend replicas** (load balance with nginx)
4. **CDN for static files** (CloudFlare/Cloudfront)
5. **Migrate to Docker Swarm** (multi-node cluster) — see `SCALING.md`
6. **Eventually: Kubernetes** (if exceeding 50k users)

---

## 📞 Support & Escalation

| Issue | Action |
|-------|--------|
| **Service crashed** | Check logs, run `deploy.sh prod` to restart |
| **Out of disk** | SSH to server, run `docker system prune -a` |
| **Certificate expired** | Run `sudo certbot renew` |
| **Performance degradation** | Check `docker stats`, optimize queries |
| **Database corruption** | Restore from latest backup using `rollback.sh` |

---

## ✅ Deployment Checklist

Before going live:

- [ ] .env.prod configured with all REQUIRED variables
- [ ] SSL certificate installed (/etc/letsencrypt/live/domain/)
- [ ] GitHub Actions workflow passing (build-and-push.yml)
- [ ] Images pushed to GHCR
- [ ] Server specs confirmed (2+ vCPU, 4+ GB RAM)
- [ ] Docker & Docker Compose installed
- [ ] Firewall configured (80/443 open)
- [ ] Backup strategy in place
- [ ] Monitoring/logs accessible
- [ ] Team trained on deploy/rollback procedures
- [ ] DNS pointing to server
- [ ] Load tests passed (< 1000 users)

---

## 📚 Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Let's Encrypt Certbot](https://certbot.eff.org/)
- [PostgreSQL Backups](https://www.postgresql.org/docs/16/backup-dump.html)
- [nginx Best Practices](https://nginx.org/en/docs/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)

---

## 🎯 Next Steps

1. **This Week:**
   - [ ] Provision server
   - [ ] Install Docker
   - [ ] Generate SSL certificate
   - [ ] Configure .env.prod

2. **Next Week:**
   - [ ] First production deployment
   - [ ] Verify health checks
   - [ ] Test backup/restore
   - [ ] Train team

3. **Following Week:**
   - [ ] Monitor in production
   - [ ] Collect metrics
   - [ ] Plan scaling (if needed)

---

**Questions?** Check [DEV_SETUP.md](./DEV_SETUP.md) for development setup or reach out to the team.
