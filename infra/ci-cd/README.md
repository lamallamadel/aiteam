# AITEAM CI/CD Infrastructure

This directory contains clean CI/CD configurations for **Development** and **Production** deployments.

## 📁 Directory Structure

```
infra/ci-cd/
├── gitlab-ci.yml              # GitLab CI/CD pipeline (imported into root .gitlab-ci.yml)
├── scripts/
│   ├── deploy-dev.sh          # Local dev deployment script
│   └── deploy-prod.sh         # Remote prod deployment script
└── README.md                  # This file

infra/deployments/
├── dev/
│   ├── .env.dev               # Dev environment variables
│   ├── docker-compose.yml     # Dev services (PostgreSQL, Backend, Frontend)
│   └── config/                # Dev config files (nginx, etc.)
└── prod/
    ├── .env.prod              # Prod environment variables (secrets)
    ├── docker-compose.yml     # Prod services (PostgreSQL, Backend, Frontend)
    └── config/
        └── nginx-prod.conf    # Prod nginx configuration with SSL
```

## 🚀 Quick Start

### Development (Local)

```bash
# Deploy development environment locally
./infra/ci-cd/scripts/deploy-dev.sh

# Access points:
#   - Backend API:  http://localhost:8080
#   - Frontend:     http://localhost:4200
#   - Database:     localhost:5432
#   - Debug Port:   localhost:5005
```

### Production (Remote SSH)

```bash
# Deploy to production server via SSH
./infra/ci-cd/scripts/deploy-prod.sh

# Required environment variables:
#   - DEPLOY_HOST           # Server IP/hostname
#   - DEPLOY_USER           # SSH username
#   - DEPLOY_DIR            # e.g., /opt/aiteam
#   - DB_PASSWORD           # Database password
#   - ORCHESTRATOR_TOKEN    # Orchestrator token
#   - LLM_API_KEY          # LLM provider API key
#   - GITHUB_REPO          # e.g., org/aiteam
#   - DOMAIN               # Production domain
```

## 🔄 CI/CD Pipeline (GitLab)

### Stages

1. **Build** (automatic on push)
   - Build backend JAR (Maven)
   - Build frontend dist (Angular)

2. **Test** (automatic on push)
   - Run backend tests (Maven verify)
   - Run frontend E2E tests (Playwright)

3. **Deploy** (manual trigger)
   - Deploy to development (develop branch)
   - Deploy to production (main branch)
   - Rollback production (manual trigger)

### GitLab CI/CD Variables

Set these in GitLab Settings → CI/CD → Variables:

**Build & Push:**
- `GHCR_USERNAME` - GitHub username
- `GHCR_TOKEN` - GitHub Personal Access Token (base64 encoded)

**Production Deployment:**
- `DEPLOY_HOST` - Production server IP
- `DEPLOY_USER` - SSH username (e.g., `root`, `ubuntu`, `deploy`)
- `DEPLOY_DIR` - Remote deployment dir (e.g., `/opt/aiteam`)
- `DEPLOY_SSH_KEY` - SSH private key (ed25519, base64 encoded)
- `GIT_REPO` - Full git clone URL

**Production Secrets:**
- `DB_PASSWORD` - Strong database password
- `ORCHESTRATOR_TOKEN` - Random orchestrator token
- `LLM_API_KEY` - LLM provider API key (OpenAI, DeepSeek, etc.)
- `GITHUB_REPO` - GitHub repo path (e.g., `myorg/aiteam`)
- `DOMAIN` - Production domain (e.g., `api.example.com`)

### Generate Base64 Encoded Values

```bash
# SSH key (convert to base64)
cat ~/.ssh/id_ed25519 | base64 | tr -d '\n'

# GitHub token (already base64-safe)
echo -n "ghp_1234567890abcdef..." | base64 | tr -d '\n'

# Random tokens
openssl rand -base64 32
```

## 📋 Development Environment

### Using Docker Compose Directly

```bash
# Start development stack
docker-compose -f infra/deployments/dev/docker-compose.yml \
  --env-file infra/deployments/dev/.env.dev \
  up -d

# View logs
docker-compose -f infra/deployments/dev/docker-compose.yml logs -f

# Stop services
docker-compose -f infra/deployments/dev/docker-compose.yml down

# Start specific profile (backend only)
docker-compose -f infra/deployments/dev/docker-compose.yml \
  --profile backend up -d
```

### Services

| Service | Port | URL | Profile |
|---------|------|-----|---------|
| PostgreSQL | 5432 | - | - |
| Backend (Spring) | 8080 | http://localhost:8080 | full, backend |
| Frontend (Angular) | 4200 | http://localhost:4200 | full, frontend |
| Debug Port | 5005 | localhost:5005 | full, backend |

### Hot Reload

Frontend source is bind-mounted for hot reload:
```yaml
volumes:
  - ./frontend/src:/app/src
```

Changes to `src/` are automatically reflected.

## 🏭 Production Environment

### Docker Compose (Remote)

```bash
# SSH to production server
ssh deploy@prod-server

# Navigate to deployment directory
cd /opt/aiteam

# View status
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# View logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f

# Restart service
docker-compose -f infra/deployments/prod/docker-compose.yml restart ai-orchestrator
```

### Services

| Service | Port | URL | Notes |
|---------|------|-----|-------|
| PostgreSQL | 5432 | - | Not exposed externally |
| Backend | 8080 | http://localhost:8080 | Behind nginx proxy |
| Frontend (nginx) | 80/443 | https://domain.com | SSL with Let's Encrypt |

### Backup & Recovery

**Automated backups** run before each deployment:

```bash
# Location: /opt/aiteam/backups/YYYYMMDD_HHMMSS/db_backup.sql.gz
ls -la /opt/aiteam/backups/
```

**Manual backup:**

```bash
docker-compose -f infra/deployments/prod/docker-compose.yml exec ai-db \
  pg_dump -U aiteam_prod_user aiteam_prod | \
  gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

**Restore from backup:**

```bash
zcat backup_20260226_120000.sql.gz | \
  docker-compose -f infra/deployments/prod/docker-compose.yml exec -T ai-db \
  psql -U aiteam_prod_user aiteam_prod
```

## 🔐 Security Best Practices

### Secrets Management

❌ **Never commit secrets:**
- `.env.prod` (production variables)
- Database passwords
- API keys
- SSH keys

✅ **Store secrets in:**
- GitLab CI/CD variables (masked)
- HashiCorp Vault
- AWS Secrets Manager
- Environment variables on server

### Environment Variables

**Development** (`.env.dev`):
- Can be committed (safe defaults)
- Used for local testing

**Production** (`.env.prod`):
- Must NOT be committed
- Loaded from CI/CD variables
- Stored securely on production server

### Database Security

```bash
# Production database config
DB_NAME=aiteam_prod
DB_USER=aiteam_prod_user
DB_PASSWORD=${DB_PASSWORD}  # From CI/CD variable

# SSL/TLS support
ssl=on
ssl_cert_file=/var/lib/postgresql/certs/server.crt
ssl_key_file=/var/lib/postgresql/certs/server.key
ssl_min_protocol_version=TLSv1.3
```

## 📊 Monitoring & Logs

### View Logs

```bash
# All services
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f

# Specific service
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f ai-orchestrator

# Follow with timestamps
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f --timestamps

# Last 100 lines
docker-compose -f infra/deployments/prod/docker-compose.yml logs --tail=100
```

### Health Checks

```bash
# Check service health
curl http://localhost:8080/actuator/health

# Database
docker-compose -f infra/deployments/prod/docker-compose.yml exec ai-db pg_isready

# All services
docker-compose -f infra/deployments/prod/docker-compose.yml ps
```

### Resource Usage

```bash
# Real-time stats
docker stats --no-stream

# Disk usage
docker system df

# Container logs size
du -sh /var/lib/docker/containers/*/
```

## 🚨 Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs ai-orchestrator

# Verify environment variables
docker-compose -f infra/deployments/prod/docker-compose.yml config | grep -A 10 "environment:"

# Check port conflicts
lsof -i :8080
netstat -tulpn | grep LISTEN
```

### Out of Memory

```bash
# Check usage
docker stats --no-stream

# Free space
docker system prune -a

# Check limits
docker-compose -f infra/deployments/prod/docker-compose.yml config | grep -A 5 "resources:"
```

### Database Connection Failed

```bash
# Check database is healthy
docker-compose -f infra/deployments/prod/docker-compose.yml exec ai-db pg_isready

# Check credentials
grep "DB_" infra/deployments/prod/.env.prod

# View database logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs ai-db
```

### SSL Certificate Issues

```bash
# Check certificate validity
openssl x509 -in /etc/letsencrypt/live/domain.com/fullchain.pem -text -noout

# Renew certificate
sudo certbot renew

# Auto-renewal status
sudo systemctl status certbot.timer
```

## 📚 Related Documentation

- [DEPLOYMENT.md](../../DEPLOYMENT.md) - Full deployment guide
- [DEPLOYMENT_QUICKREF.md](../../DEPLOYMENT_QUICKREF.md) - Quick reference
- [DEV_SETUP.md](../../DEV_SETUP.md) - Local development setup
- [SECURITY.md](../../SECURITY.md) - Security best practices

## 🔄 Migration from Root CI/CD

This new structure replaces scattered CI/CD configs:

**Before:**
```
aiteam/
├── .gitlab-ci.yml              # Old GitLab CI
├── docker-compose.yml          # Old compose
├── docker-compose.dev.yml      # Old dev compose
├── docker-compose.prod.yml     # Old prod compose
├── scripts/deploy.sh           # Old deploy script
└── scripts/rollback.sh         # Old rollback script
```

**After:**
```
aiteam/
├── infra/
│   ├── ci-cd/
│   │   ├── gitlab-ci.yml       # New GitLab CI
│   │   └── scripts/
│   │       ├── deploy-dev.sh
│   │       └── deploy-prod.sh
│   └── deployments/
│       ├── dev/
│       │   └── docker-compose.yml
│       └── prod/
│           └── docker-compose.yml
└── .gitlab-ci.yml              # Imports infra/ci-cd/gitlab-ci.yml
```

## ✅ Deployment Checklist

### Before First Production Deployment

- [ ] All secrets configured in GitLab CI/CD
- [ ] SSH key (ed25519) generated and base64 encoded
- [ ] Domain DNS pointing to server
- [ ] SSL certificate ready (Let's Encrypt)
- [ ] Database backups configured
- [ ] Firewall rules set (80, 443, ssh)
- [ ] Server specs verified (2+ vCPU, 4GB+ RAM)
- [ ] Docker & Docker Compose installed
- [ ] Git repository accessible from CI/CD

### After Each Deployment

- [ ] Services are healthy: `docker-compose ps`
- [ ] Logs show no errors: `docker-compose logs -f`
- [ ] Frontend accessible: https://domain.com
- [ ] API responding: curl https://domain.com/api/health
- [ ] Database connected: `docker-compose exec ai-db pg_isready`
- [ ] Recent backup exists: `ls -la backups/`

---

**Questions?** Check the main [DEPLOYMENT.md](../../DEPLOYMENT.md) or reach out to the team.
