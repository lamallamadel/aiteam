# AITEAM Infrastructure

This directory contains all infrastructure-as-code for deploying AITEAM:
- **CI/CD pipelines** (GitLab, GitHub Actions)
- **Docker Compose configurations** (Dev and Prod)
- **Deployment scripts** (Bash automation)
- **Configuration files** (nginx, security, monitoring)

## 📁 Directory Structure

```
infra/
├── ci-cd/                           # CI/CD pipelines and scripts
│   ├── gitlab-ci.yml               # GitLab CI/CD config (imported into root)
│   ├── scripts/
│   │   ├── deploy-dev.sh           # Deploy development locally
│   │   └── deploy-prod.sh          # Deploy production to remote server
│   └── README.md                   # CI/CD documentation
│
├── deployments/                    # Environment-specific deployments
│   ├── dev/                        # Development environment
│   │   ├── .env.dev               # Development variables
│   │   ├── docker-compose.yml     # Dev services
│   │   └── config/                # Dev configs
│   │
│   └── prod/                       # Production environment
│       ├── .env.prod              # Production variables (secrets)
│       ├── docker-compose.yml     # Prod services
│       └── config/
│           └── nginx-prod.conf    # Production nginx config
│
├── docker-compose.ai.yml           # Original AI orchestrator compose (kept for reference)
├── vault-init.sh                   # HashiCorp Vault initialization
├── trivy-config.yaml              # Container security scanning config
├── README.md                       # This file
└── [other files...]
```

## 🚀 Quick Start

### Development (Local)

```bash
# Start development environment
./infra/ci-cd/scripts/deploy-dev.sh

# Access:
#   Backend:  http://localhost:8080
#   Frontend: http://localhost:4200
#   Database: localhost:5432
```

### Production (Remote)

```bash
# Deploy to production server
export DEPLOY_HOST="prod-server.com"
export DEPLOY_USER="ubuntu"
export DEPLOY_DIR="/opt/aiteam"
export DB_PASSWORD="$(openssl rand -base64 32)"
export ORCHESTRATOR_TOKEN="$(openssl rand -base64 32)"
export JWT_SECRET_KEY="$(openssl rand -base64 48)"   # Auth JWT (ou via Vault)
export LLM_API_KEY="sk-..."
export GITHUB_REPO="myorg/aiteam"
export DOMAIN="api.example.com"

./infra/ci-cd/scripts/deploy-prod.sh
```

## 🔄 CI/CD Pipeline

### GitLab CI/CD

Pipeline stages (automatic on push):

1. **Build** - Compile backend JAR, build frontend dist
2. **Test** - Run Maven tests, Playwright E2E tests
3. **Deploy** - Manual trigger to deploy to dev/prod

See `infra/ci-cd/README.md` for detailed instructions.

### GitHub Actions

Not moved - remains in `.github/workflows/` for compatibility.

## 📦 Environment Configurations

### Development (`.env.dev`)

Safe defaults for local development:
- Database: PostgreSQL 16
- Backend: Spring Boot with debug port 5005
- Frontend: Angular dev server with hot reload
- All passwords are example values

Start locally:
```bash
docker-compose -f infra/deployments/dev/docker-compose.yml up -d
```

### Production (`.env.prod`)

Secure production deployment:
- Database: PostgreSQL 16 with SSL
- Backend: Spring Boot optimized for prod
- Frontend: nginx with SSL/TLS
- All secrets from CI/CD variables

Deploy remotely:
```bash
ssh deploy@prod-server "cd /opt/aiteam && docker-compose -f infra/deployments/prod/docker-compose.yml up -d"
```

## 🐳 Docker Compose Services

### Development

```yaml
Services:
  ai-db            - PostgreSQL 16 (dev)
  ai-orchestrator  - Spring Boot backend (debug enabled)
  ai-dashboard     - Angular frontend (hot reload)
```

### Production

```yaml
Services:
  ai-db            - PostgreSQL 16 (prod, SSL enabled)
  ai-orchestrator  - Spring Boot backend (optimized)
  ai-dashboard     - nginx reverse proxy (SSL/TLS)
```

## 🔐 Security

### Secrets Management

- **Development**: Safe defaults in `.env.dev` (committable)
- **Production**: Secrets in CI/CD variables (never committed)
- **Database**: Encrypted connections (TLS 1.3)
- **SSL/TLS**: Let's Encrypt certificates

### Credential Rotation

```bash
# Generate secure passwords
openssl rand -base64 32

# Generate ed25519 SSH key
ssh-keygen -t ed25519 -f ~/.ssh/aiteam_deploy -N ""

# Base64 encode for CI/CD
cat ~/.ssh/aiteam_deploy | base64 | tr -d '\n'
```

## 📊 Deployment Checklist

Before going live:

### Development
- [ ] `.env.dev` configured
- [ ] PostgreSQL running on 5432
- [ ] Backend accessible on 8080
- [ ] Frontend accessible on 4200
- [ ] Debug port working on 5005

### Production
- [ ] `.env.prod` secrets in GitLab CI/CD
- [ ] Domain DNS configured
- [ ] SSL certificate ready
- [ ] SSH key configured
- [ ] Server specs verified (2+ vCPU, 4GB+ RAM)
- [ ] Docker installed
- [ ] Firewall rules set (80, 443, 22)
- [ ] Backups configured

## 📚 Documentation

- `infra/ci-cd/README.md` - CI/CD pipelines and deployment scripts
- `infra/deployments/dev/` - Development environment details
- `infra/deployments/prod/` - Production environment details
- `../DEPLOYMENT.md` - Full deployment guide
- `../DEPLOYMENT_QUICKREF.md` - Quick reference
- `../SECURITY.md` - Security best practices

## 🛠️ Common Tasks

### View Deployment Status

```bash
# Development
docker-compose -f infra/deployments/dev/docker-compose.yml ps

# Production (remote)
ssh deploy@prod-server "cd /opt/aiteam && docker-compose -f infra/deployments/prod/docker-compose.yml ps"
```

### View Logs

```bash
# Development
docker-compose -f infra/deployments/dev/docker-compose.yml logs -f

# Production
ssh deploy@prod-server "cd /opt/aiteam && docker-compose -f infra/deployments/prod/docker-compose.yml logs -f ai-orchestrator"
```

### Backup Database

```bash
# Development
docker-compose -f infra/deployments/dev/docker-compose.yml exec ai-db pg_dump -U ai_dev ai_dev | gzip > backup.sql.gz

# Production
ssh deploy@prod-server "cd /opt/aiteam && docker-compose -f infra/deployments/prod/docker-compose.yml exec ai-db pg_dump -U aiteam_prod_user aiteam_prod | gzip > backup.sql.gz"
```

### Restart Services

```bash
# Development
docker-compose -f infra/deployments/dev/docker-compose.yml restart ai-orchestrator

# Production
ssh deploy@prod-server "cd /opt/aiteam && docker-compose -f infra/deployments/prod/docker-compose.yml restart ai-orchestrator"
```

## 🚨 Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose -f infra/deployments/dev/docker-compose.yml logs ai-orchestrator

# Check environment variables
docker-compose -f infra/deployments/dev/docker-compose.yml config | grep -A 10 "environment:"
```

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill and restart
docker-compose -f infra/deployments/dev/docker-compose.yml restart ai-orchestrator
```

### Database Connection Failed

```bash
# Check database is running
docker-compose -f infra/deployments/dev/docker-compose.yml exec ai-db pg_isready

# Check credentials
grep "DB_" infra/deployments/dev/.env.dev
```

## 🔗 Related Files

- Root `.gitlab-ci.yml` - Imports `infra/ci-cd/gitlab-ci.yml`
- `.github/workflows/` - GitHub Actions (unchanged)
- Root `docker-compose.yml` - Supprimé ; utiliser `infra/deployments/dev/` ou `infra/deployments/prod/`
- Root `scripts/deploy.sh` - Legacy (use `infra/ci-cd/scripts/` instead)

## ✅ Migration Path

This restructuring provides:

✅ **Clean separation** - Dev and Prod configs isolated  
✅ **Scalable** - Easy to add staging, QA environments  
✅ **Maintainable** - Single source of truth for infrastructure  
✅ **Secure** - Secrets never in version control  
✅ **Automated** - CI/CD pipelines for all environments  

---

**For detailed CI/CD documentation, see `infra/ci-cd/README.md`**

**Questions?** Check `../DEPLOYMENT.md` or reach out to the team.
