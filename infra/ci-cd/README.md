# AITEAM CI/CD Infrastructure

This directory contains clean CI/CD configurations for **Development** and **Production** deployments.

## 📁 Directory Structure

```
infra/ci-cd/
├── gitlab-ci.yml                    # GitLab CI/CD pipeline (imported into root .gitlab-ci.yml)
├── scripts/
│   ├── backup.sh                    # PostgreSQL backup with retention policy
│   ├── rollback.sh                  # Blue-green rollback via Docker tags
│   ├── deploy-dev.sh                # Local dev deployment script
│   ├── deploy-prod.sh               # Remote prod deployment script (includes pre-deployment backup)
│   ├── verify-backup-setup.sh       # Verification script for backup/rollback setup
│   ├── setup-permissions.sh         # Auto-setup script permissions
│   └── .env.backup.example          # Example environment variables for backup/rollback
└── README.md                        # This file

infra/deployments/
├── dev/
│   ├── .env.dev                     # Dev environment variables
│   ├── docker-compose.yml           # Dev services (PostgreSQL, Backend, Frontend)
│   └── config/                      # Dev config files (nginx, etc.)
└── prod/
    ├── .env.prod                    # Prod environment variables (secrets)
    ├── docker-compose.yml           # Prod services (PostgreSQL, Backend, Frontend)
    ├── backup-cron.sh               # Scheduled backup cron wrapper
    └── config/
        └── nginx-prod.conf          # Prod nginx configuration with SSL

backups/                             # Created on first backup (not in git)
├── daily/                           # Daily backups (7 day retention)
├── weekly/                          # Weekly backups (4 week retention)
├── monthly/                         # Monthly backups (kept indefinitely)
├── predeployment/                   # Pre-deployment safety backups
└── rollback/                        # Deployment history and rollback data
    └── deployment_tags.log          # Docker image tag history for rollback
```

## 🚀 Quick Start

### Initial Setup

Before first use, ensure all scripts have execute permissions:

```bash
# Quick setup (recommended)
./infra/ci-cd/scripts/setup-permissions.sh

# Or manually set permissions:
chmod +x infra/ci-cd/scripts/*.sh
chmod +x infra/deployments/prod/*.sh

# Verify your setup
./infra/ci-cd/scripts/verify-backup-setup.sh prod

# For development environment
./infra/ci-cd/scripts/verify-backup-setup.sh dev
```

**What the verification script checks:**
- ✅ Script execute permissions
- ✅ Backup directory structure
- ✅ Database connectivity
- ✅ Docker Compose configuration
- ✅ Required tools (gzip, gunzip, docker-compose)
- ✅ Optional tools (AWS CLI, mail, healthcheck)
- ✅ Deployment history
- ✅ Environment variables
- ✅ Available disk space

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

3. **Load Test** (manual trigger)
   - Run k6 load tests against staging
   - Run k6 load tests against production
   - Performance regression detection

4. **Deploy** (manual trigger)
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

**Load Testing:**
- `LOAD_TEST_URL` - Staging API URL for load tests
- `LOAD_TEST_WS_URL` - Staging WebSocket URL
- `PRODUCTION_URL` - Production API URL for load tests
- `PRODUCTION_WS_URL` - Production WebSocket URL
- `AUTH_TOKEN` - Test authentication token
- `A2A_TOKEN` - A2A protocol authentication token

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

#### Overview

AITEAM includes a comprehensive backup and rollback system for production safety:

| Component | Location | Purpose | Automation |
|-----------|----------|---------|------------|
| **backup.sh** | `infra/ci-cd/scripts/` | PostgreSQL dumps with retention | Manual or cron |
| **rollback.sh** | `infra/ci-cd/scripts/` | Blue-green deployment rollback | Manual |
| **backup-cron.sh** | `infra/deployments/prod/` | Scheduled backup wrapper | Cron job |
| **verify-backup-setup.sh** | `infra/ci-cd/scripts/` | System verification | Manual |
| **setup-permissions.sh** | `infra/ci-cd/scripts/` | Permission setup | Manual |

**Key Features:**
- ✅ Automatic retention policy (7 daily, 4 weekly, monthly archives)
- ✅ Pre-deployment backups (automatic in deploy-prod.sh)
- ✅ Blue-green rollback with deployment history
- ✅ Optional S3 uploads for off-site storage
- ✅ Backup integrity verification (gunzip -t)
- ✅ Health monitoring integration (healthcheck.io)
- ✅ Email notifications on success/failure
- ✅ Detailed backup reports with metadata

#### Automated Backup System

The backup system uses a tiered retention policy:

**Retention Policy:**
- Daily backups: 7 days
- Weekly backups: 4 weeks (created on Sundays)
- Monthly backups: Indefinite (created on 1st of month)

**Backup Script:**

```bash
# Run manual backup (production)
./infra/ci-cd/scripts/backup.sh prod

# Run manual backup (development)
./infra/ci-cd/scripts/backup.sh dev

# Backup with custom directory
BACKUP_DIR=/custom/path ./infra/ci-cd/scripts/backup.sh prod

# Backup with S3 upload
S3_BUCKET=my-backup-bucket ./infra/ci-cd/scripts/backup.sh prod
```

**Scheduled Backups (Cron):**

The `backup-cron.sh` script is designed for automated scheduled backups:

```bash
# Install on production server
ssh deploy@prod-server

# Setup cron job (edit crontab)
crontab -e

# Add one of these lines:

# Daily backup at 2:00 AM
0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1

# Every 6 hours
0 */6 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1

# Weekly on Sunday at 3:00 AM
0 3 * * 0 /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
```

**Optional: Setup healthcheck monitoring**

```bash
# Add to cron environment
export HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
export ADMIN_EMAIL=admin@example.com
```

**Pre-Deployment Backups:**

Backups are automatically created before each production deployment:

```bash
# Location structure
/opt/atlasia/backups/
├── daily/              # Daily backups (7 day retention)
│   ├── backup_20260226_020000.sql.gz
│   └── schema_20260226_020000.sql
├── weekly/             # Weekly backups (4 week retention)
│   └── backup_20260223_020000.sql.gz
├── monthly/            # Monthly backups (kept indefinitely)
│   └── backup_20260201_020000.sql.gz
├── predeployment/      # Created during deployments
│   └── 20260226_143022/
│       └── db_backup.sql.gz
└── rollback/           # Pre-rollback backups
    ├── deployment_tags.log
    └── prerollback_20260226_150000.sql.gz
```

**Manual backup:**

```bash
# Using the automated script (recommended)
./infra/ci-cd/scripts/backup.sh prod

# Using docker-compose directly
docker-compose -f infra/deployments/prod/docker-compose.yml exec ai-db \
  pg_dump -U aiteam_prod_user aiteam_prod | \
  gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

**Restore from backup:**

```bash
# Restore from compressed backup
zcat /opt/atlasia/backups/daily/backup_20260226_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod

# Or using docker-compose
zcat backup_20260226_120000.sql.gz | \
  docker-compose -f infra/deployments/prod/docker-compose.yml exec -T ai-db \
  psql -U aiteam_prod_user aiteam_prod

# Restore with connection reset (drops connections first)
docker exec -i aiteam_db_prod psql -U postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='aiteam_prod' AND pid <> pg_backend_pid();"
zcat backup_20260226_120000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

**View backup reports:**

```bash
# Each backup generates a report
cat /opt/atlasia/backups/daily/backup_20260226_020000.report.txt
```

#### Blue-Green Rollback

The rollback system supports blue-green deployments using Docker image tags.

**Rollback Script:**

```bash
# Rollback production to previous deployment
./infra/ci-cd/scripts/rollback.sh prod

# Rollback to specific tag
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod

# Rollback without creating pre-rollback backup
./infra/ci-cd/scripts/rollback.sh prod --skip-backup

# Rollback development environment
./infra/ci-cd/scripts/rollback.sh dev
```

**How Rollback Works:**

1. **Detects current deployment** - Identifies currently running Docker image tags
2. **Creates pre-rollback backup** - Backs up database before rollback (unless --skip-backup)
3. **Determines rollback target** - Uses previous tags from deployment history or specified ROLLBACK_TAG
4. **Pulls rollback images** - Downloads Docker images from GHCR
5. **Stops services gracefully** - 30-second timeout for graceful shutdown
6. **Starts rolled-back services** - Deploys services with previous image tags
7. **Health verification** - Waits for services to become healthy
8. **Rollback verification** - Confirms all services are running with correct tags

**Deployment History:**

The system maintains deployment history for rollback:

```bash
# View deployment history
cat /opt/atlasia/backups/rollback/deployment_tags.log

# Format: timestamp,backend_tag,frontend_tag
20260226_143022,latest,latest
20260226_150000,v1.2.3,v1.2.3
20260226_163045,v1.2.4,v1.2.4
```

**Rollback Scenarios:**

```bash
# Scenario 1: Rollback to immediate previous version
./infra/ci-cd/scripts/rollback.sh prod

# Scenario 2: Rollback to specific version
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod

# Scenario 3: Emergency rollback (skip backup for speed)
./infra/ci-cd/scripts/rollback.sh prod --skip-backup

# Scenario 4: Rollback with custom GHCR repository
GITHUB_REPO=myorg/myrepo ./infra/ci-cd/scripts/rollback.sh prod
```

**Verify Rollback:**

```bash
# Check service status
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# Verify image tags
docker inspect aiteam_backend_prod --format='{{.Config.Image}}'
docker inspect aiteam_frontend_prod --format='{{.Config.Image}}'

# Check service health
curl http://localhost:8080/actuator/health

# View logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f
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

### Backup Issues

#### Backup Script Not Found
```bash
# Verify script exists
ls -la infra/ci-cd/scripts/backup.sh

# Check permissions
chmod +x infra/ci-cd/scripts/backup.sh

# Test backup manually
./infra/ci-cd/scripts/backup.sh prod
```

#### Database Container Not Running
```bash
# Check if database is running
docker ps | grep db

# Start database container
docker-compose -f infra/deployments/prod/docker-compose.yml up -d ai-db

# Check database health
docker exec aiteam_db_prod pg_isready -U aiteam_prod_user
```

#### Insufficient Disk Space
```bash
# Check disk usage
df -h

# Clean old backups manually (if needed)
find ./backups/daily -name "*.sql.gz" -mtime +7 -delete

# Clean Docker resources
docker system prune -a --volumes
```

#### Backup Verification Failed
```bash
# Test backup integrity
gunzip -t backups/daily/backup_20260226_020000.sql.gz

# If corrupted, use previous backup
ls -lt backups/daily/ | head -5

# Restore from known good backup
zcat backups/daily/backup_20260225_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

### Rollback Issues

#### Rollback Tag Not Found
```bash
# Check deployment history
cat backups/rollback/deployment_tags.log

# Manually specify tag
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod

# List available Docker images
docker images | grep ai-orchestrator
```

#### Rollback Script Confirmation Timeout
```bash
# Skip confirmation (use with caution)
# Edit rollback.sh and comment out confirm_rollback function
# Or run with expect/automation tools in CI/CD

# For non-production environments
./infra/ci-cd/scripts/rollback.sh dev  # No confirmation required for dev
```

#### Services Not Healthy After Rollback
```bash
# Check service status
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# Check individual service health
docker inspect aiteam_backend_prod --format='{{.State.Health.Status}}'

# View service logs
docker logs aiteam_backend_prod --tail=100

# Restart specific service
docker-compose -f infra/deployments/prod/docker-compose.yml restart ai-orchestrator
```

### Cron Job Issues

#### Cron Job Not Running
```bash
# Check cron status
sudo systemctl status cron

# View cron logs
grep CRON /var/log/syslog

# Test cron job manually
/opt/atlasia/infra/deployments/prod/backup-cron.sh

# Check crontab entries
crontab -l
```

#### Email Notifications Not Working
```bash
# Test mail command
echo "Test" | mail -s "Test Subject" admin@example.com

# Install mail if not available
sudo apt-get install mailutils

# Check mail logs
tail -f /var/log/mail.log
```

#### Healthcheck Pings Failing
```bash
# Test healthcheck URL manually
curl -fsS --retry 3 "$HEALTHCHECK_URL"

# Check if URL is set
echo $HEALTHCHECK_URL

# Set in crontab environment
crontab -e
# Add: HEALTHCHECK_URL=https://hc-ping.com/your-id
```

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

## 🔬 Load Testing

### Overview

The platform includes comprehensive load testing using [k6](https://k6.io/) to validate performance under realistic load conditions.

### Quick Start

```bash
# Run all load tests locally
cd infra/ci-cd/scripts
./load-test.sh all local

# Run specific test
./load-test.sh api local        # API endpoints only
./load-test.sh a2a local        # A2A protocol only
./load-test.sh websocket local  # WebSocket collaboration only
```

### Test Scenarios

1. **API Load Test** (`api-load-test.js`)
   - Tests: `/api/runs` endpoints
   - Users: 100+ concurrent
   - Duration: ~20 minutes
   - Baseline: p95 < 500ms

2. **A2A Protocol Test** (`a2a-load-test.js`)
   - Tests: `/api/a2a/tasks` endpoints
   - Agents: 100+ concurrent
   - Duration: ~18 minutes
   - Baseline: p95 < 500ms (submission), < 2s (execution)

3. **WebSocket Test** (`websocket-load-test.js`)
   - Tests: `/ws/runs/{runId}/collaboration`
   - Connections: 50+ concurrent
   - Duration: ~18 minutes
   - Baseline: p95 < 1s (connection), < 100ms (messages)

### CI/CD Integration

Load tests are available as manual jobs in GitLab CI:

```yaml
# In .gitlab-ci.yml
load-test:
  stage: load-test
  when: manual
  script:
    - bash infra/ci-cd/scripts/load-test.sh all staging

load-test-production:
  stage: load-test
  when: manual
  script:
    - bash infra/ci-cd/scripts/load-test.sh all production
```

**Trigger manually**:
- GitLab UI: Pipeline → load-test stage → load-test job → ▶ Run
- Results stored in artifacts for 30 days (staging) or 90 days (production)

### Performance Baselines

| Metric | Baseline | Test |
|--------|----------|------|
| API p95 latency | < 500ms | `api-load-test.js` |
| AI p95 latency | < 2s | `api-load-test.js` |
| WebSocket connection | < 1s | `websocket-load-test.js` |
| WebSocket messages | < 100ms | `websocket-load-test.js` |
| Error rate | < 1% | All tests |

### Results Analysis

```bash
# Analyze results (automatic after test run)
./infra/ci-cd/scripts/analyze-load-results.sh ../../../ai-orchestrator/target/load-test-results

# View HTML report
open ai-orchestrator/target/load-test-results/load-test-report.html

# Extract metrics
jq '.metrics.http_req_duration.values.p95' results/*_summary.json
```

### Related Documentation

- [PERFORMANCE_BENCHMARKS.md](../../docs/PERFORMANCE_BENCHMARKS.md) - **Complete performance documentation**
- [LOAD_TESTING_QUICKREF.md](scripts/LOAD_TESTING_QUICKREF.md) - Quick reference guide
- [Load Test Examples](../../ai-orchestrator/src/test/load/EXAMPLES.md) - Practical examples
- [Load Test README](../../ai-orchestrator/src/test/load/README.md) - Test scenarios

## 📚 Related Documentation

- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - **Implementation Overview & Summary**
- [BACKUP_ROLLBACK_QUICKREF.md](BACKUP_ROLLBACK_QUICKREF.md) - **Backup & Rollback Quick Reference Card**
- [BACKUP_ROLLBACK_README.md](scripts/BACKUP_ROLLBACK_README.md) - Detailed backup & rollback documentation
- [LOAD_TESTING_QUICKREF.md](scripts/LOAD_TESTING_QUICKREF.md) - **Load Testing Quick Reference**
- [DEPLOYMENT.md](../../DEPLOYMENT.md) - Full deployment guide
- [DEPLOYMENT_QUICKREF.md](../../DEPLOYMENT_QUICKREF.md) - Quick reference
- [DEV_SETUP.md](../../DEV_SETUP.md) - Local development setup
- [SECURITY.md](../../SECURITY.md) - Security best practices
- [PERFORMANCE_BENCHMARKS.md](../../docs/PERFORMANCE_BENCHMARKS.md) - **Performance benchmarks and load testing**

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
- [ ] **Backup system verified**: `./infra/ci-cd/scripts/verify-backup-setup.sh prod`
- [ ] **Script permissions set**: `./infra/ci-cd/scripts/setup-permissions.sh`
- [ ] **Cron backup scheduled**: Added to crontab
- [ ] **S3 backups configured** (optional): S3_BUCKET environment variable set
- [ ] **Monitoring enabled** (optional): HEALTHCHECK_URL configured
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
- [ ] Deployment tags saved: `tail backups/rollback/deployment_tags.log`

## 🔄 Disaster Recovery Procedures

### Complete System Recovery

In case of catastrophic failure, follow these steps:

**1. Assess the Situation**
```bash
# Check what's running
docker ps -a

# Check disk space
df -h

# Check recent logs
journalctl -u docker --since "1 hour ago"
```

**2. Stop All Services**
```bash
cd /opt/atlasia
docker-compose -f infra/deployments/prod/docker-compose.yml down
```

**3. Restore from Most Recent Backup**
```bash
# Find most recent backup
ls -lt /opt/atlasia/backups/daily/ | head -5

# Restore database
BACKUP_FILE="/opt/atlasia/backups/daily/backup_20260226_020000.sql.gz"
docker-compose -f infra/deployments/prod/docker-compose.yml up -d ai-db
sleep 10
zcat $BACKUP_FILE | docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

**4. Start All Services**
```bash
docker-compose -f infra/deployments/prod/docker-compose.yml up -d
```

**5. Verify Recovery**
```bash
# Check all services
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# Test API
curl http://localhost:8080/actuator/health

# Check logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f
```

### Database Corruption Recovery

If the database is corrupted:

```bash
# 1. Stop services
docker-compose -f infra/deployments/prod/docker-compose.yml stop ai-orchestrator

# 2. Backup current (possibly corrupted) state
docker exec aiteam_db_prod pg_dump -U aiteam_prod_user aiteam_prod > corrupted_backup.sql 2>&1

# 3. Drop and recreate database
docker exec -i aiteam_db_prod psql -U postgres << EOF
DROP DATABASE IF EXISTS aiteam_prod;
CREATE DATABASE aiteam_prod OWNER aiteam_prod_user;
EOF

# 4. Restore from last known good backup
zcat /opt/atlasia/backups/daily/backup_20260226_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod

# 5. Restart services
docker-compose -f infra/deployments/prod/docker-compose.yml up -d
```

### Lost Backup Recovery

If backups are lost, recover from S3 (if configured):

```bash
# List available S3 backups
aws s3 ls s3://my-backup-bucket/atlasia-backups/

# Download specific backup
aws s3 cp s3://my-backup-bucket/atlasia-backups/backup_20260226_020000.sql.gz .

# Restore
zcat backup_20260226_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

### Point-in-Time Recovery

For point-in-time recovery using PostgreSQL WAL archives (if configured):

```bash
# This requires PostgreSQL continuous archiving to be enabled
# See: https://www.postgresql.org/docs/current/continuous-archiving.html

# 1. Stop database
docker-compose -f infra/deployments/prod/docker-compose.yml stop ai-db

# 2. Restore base backup
zcat /opt/atlasia/backups/daily/backup_20260226_020000.sql.gz > base_backup.sql

# 3. Configure recovery.conf with target time
docker exec -i aiteam_db_prod bash -c "cat > /var/lib/postgresql/data/recovery.conf" << EOF
restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
recovery_target_time = '2026-02-26 14:30:00'
recovery_target_action = 'promote'
EOF

# 4. Start database (will replay WAL to target time)
docker-compose -f infra/deployments/prod/docker-compose.yml start ai-db
```

## 📋 Best Practices

### Production Deployment Checklist

Before deploying to production:

- [ ] **Verify Backup System**
  ```bash
  # Test backup script
  ./infra/ci-cd/scripts/backup.sh prod
  
  # Verify backup integrity
  gunzip -t backups/daily/backup_*.sql.gz
  
  # Check backup retention
  ls -lh backups/daily/ backups/weekly/ backups/monthly/
  ```

- [ ] **Verify Rollback Capability**
  ```bash
  # Check deployment history exists
  cat backups/rollback/deployment_tags.log
  
  # Verify rollback script is executable
  test -x infra/ci-cd/scripts/rollback.sh && echo "OK" || chmod +x infra/ci-cd/scripts/rollback.sh
  
  # Verify Docker images are available
  docker images | grep -E "(ai-orchestrator|ai-dashboard)"
  ```

- [ ] **Configure Automated Backups**
  ```bash
  # Setup cron job for daily backups
  crontab -e
  # Add: 0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
  
  # Setup optional S3 uploads
  export S3_BUCKET=my-backup-bucket
  
  # Setup healthcheck monitoring
  export HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
  ```

- [ ] **Test Disaster Recovery**
  ```bash
  # Simulate disaster recovery in dev environment
  ./infra/ci-cd/scripts/backup.sh dev
  docker-compose -f infra/docker-compose.ai.yml down -v
  # Restore from backup...
  zcat backups/daily/backup_*.sql.gz | docker exec -i ai-db psql -U ai ai
  ```

### Backup Best Practices

1. **Multiple Backup Locations**: Store backups in multiple locations (local + S3/cloud)
2. **Test Restores Regularly**: Perform restore tests monthly in dev environment
3. **Monitor Backup Success**: Use healthcheck.io or similar monitoring
4. **Document Restore Procedures**: Keep restore procedures documented and up-to-date
5. **Retention Policy**: Follow 7-4-indefinite retention (7 daily, 4 weekly, monthly forever)
6. **Pre-Deployment Backups**: Always backup before deployments (automatic in deploy-prod.sh)
7. **Verify Integrity**: Always verify backup integrity with `gunzip -t`
8. **Backup Before Schema Changes**: Create manual backup before database migrations

### Rollback Best Practices

1. **Keep Deployment History**: Maintain deployment_tags.log for all deployments
2. **Tag Images Properly**: Use semantic versioning for Docker image tags
3. **Test Rollback in Dev**: Test rollback procedure in development first
4. **Quick Rollback Option**: Use `--skip-backup` for emergency rollbacks
5. **Document Current State**: Save current deployment state before rollback
6. **Verify After Rollback**: Always verify services are healthy after rollback
7. **Database Compatibility**: Ensure rolled-back app version is compatible with current DB schema
8. **Communicate**: Notify team before and after production rollbacks

### Security Best Practices

1. **Encrypt Backups**: Consider encrypting backups at rest (use gpg or AWS KMS)
2. **Secure S3 Buckets**: Use IAM roles and bucket policies to restrict access
3. **Restrict Script Access**: Only authorized users should have execute permissions
4. **Audit Logs**: Review backup and rollback logs regularly
5. **Secrets Management**: Never commit database passwords or API keys

### Monitoring & Alerting

**Setup Backup Monitoring:**

```bash
# 1. Setup healthcheck.io monitoring
# Create account at https://healthcheck.io
# Get your unique ping URL

# 2. Add to crontab environment
crontab -e
# Add at the top:
HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
ADMIN_EMAIL=admin@example.com

# 3. Configure alerting (healthcheck.io will email on failures)
```

**Monitor Backup Metrics:**

```bash
# Check backup sizes over time
du -sh backups/daily/* | tail -7
du -sh backups/weekly/* | tail -4
du -sh backups/monthly/*

# Monitor backup success rate
grep "BACKUP COMPLETED" /var/log/atlasia-backup.log | wc -l
grep "BACKUP FAILED" /var/log/atlasia-backup.log | wc -l

# Check latest backup age
find backups/daily -name "backup_*.sql.gz" -type f -mtime -1 | wc -l
# Should return 1 if backup ran in last 24h

# Alert if no backup in 25 hours
if [ $(find backups/daily -name "backup_*.sql.gz" -type f -mtime -1 | wc -l) -eq 0 ]; then
    echo "WARNING: No backup created in last 24 hours" | mail -s "Backup Alert" admin@example.com
fi
```

**Backup Success Dashboard:**

Create a simple monitoring script:

```bash
#!/bin/bash
# /opt/atlasia/scripts/backup-status.sh

echo "=== Atlasia Backup Status ==="
echo ""
echo "Last Daily Backup:"
ls -lh backups/daily/*.sql.gz | tail -1
echo ""
echo "Last Weekly Backup:"
ls -lh backups/weekly/*.sql.gz | tail -1
echo ""
echo "Last Monthly Backup:"
ls -lh backups/monthly/*.sql.gz | tail -1
echo ""
echo "Backup Counts:"
echo "  Daily: $(ls backups/daily/*.sql.gz 2>/dev/null | wc -l)"
echo "  Weekly: $(ls backups/weekly/*.sql.gz 2>/dev/null | wc -l)"
echo "  Monthly: $(ls backups/monthly/*.sql.gz 2>/dev/null | wc -l)"
echo ""
echo "Disk Usage:"
du -sh backups/
echo ""
echo "Recent Backup Log:"
tail -20 /var/log/atlasia-backup.log
```

**Integration with External Monitoring:**

```bash
# Prometheus metrics (if using Prometheus)
# Add to backup-cron.sh:
echo "atlasia_backup_success $(date +%s)" > /var/lib/node_exporter/atlasia_backup.prom

# Grafana alert (if using Grafana)
# Create alert for: time() - atlasia_backup_success > 86400

# Datadog/New Relic (if using APM)
# Add to backup-cron.sh:
if [[ -n "${DD_API_KEY:-}" ]]; then
    curl -X POST "https://api.datadoghq.com/api/v1/events?api_key=$DD_API_KEY" \
         -H "Content-Type: application/json" \
         -d "{\"title\":\"Atlasia Backup Completed\",\"text\":\"Backup successful\",\"tags\":[\"env:prod\"]}"
fi
```

## 🔍 Setup Verification

The verification script helps ensure your backup and rollback system is properly configured:

```bash
# Run verification
./infra/ci-cd/scripts/verify-backup-setup.sh prod

# Example output:
# [PASS] backup.sh is executable
# [PASS] rollback.sh is executable
# [PASS] Database container is running: aiteam_db_prod
# [PASS] Database is accepting connections
# [PASS] docker-compose configuration is valid
# [PASS] Sufficient disk space available: 50GB
# [WARN] GITHUB_REPO not set (required for image pull during rollback)
# 
# ✅ All critical tests passed!
```

**Automated Fixes:**

If verification fails, run the setup script:

```bash
# Fix permissions automatically
./infra/ci-cd/scripts/setup-permissions.sh

# Start database if not running
docker-compose -f infra/deployments/prod/docker-compose.yml up -d ai-db

# Set required environment variables
export GITHUB_REPO=myorg/atlasia
export S3_BUCKET=my-backup-bucket  # Optional
export HEALTHCHECK_URL=https://hc-ping.com/xxx  # Optional

# Re-run verification
./infra/ci-cd/scripts/verify-backup-setup.sh prod
```

## 📋 Backup & Rollback Quick Reference

### Quick Commands

```bash
# === BACKUPS ===

# Manual backup (production)
./infra/ci-cd/scripts/backup.sh prod

# Manual backup (development)
./infra/ci-cd/scripts/backup.sh dev

# Backup with S3 upload
S3_BUCKET=my-bucket ./infra/ci-cd/scripts/backup.sh prod

# === ROLLBACK ===

# Rollback to previous version
./infra/ci-cd/scripts/rollback.sh prod

# Rollback to specific tag
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod

# Emergency rollback (no backup)
./infra/ci-cd/scripts/rollback.sh prod --skip-backup

# === RESTORE ===

# Restore from backup
zcat backup.sql.gz | docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod

# === VERIFICATION ===

# Check backup files
ls -lh /opt/atlasia/backups/daily/
ls -lh /opt/atlasia/backups/weekly/
ls -lh /opt/atlasia/backups/monthly/

# View backup report
cat /opt/atlasia/backups/daily/backup_20260226_020000.report.txt

# Check deployment history
cat /opt/atlasia/backups/rollback/deployment_tags.log

# Verify service tags
docker inspect aiteam_backend_prod --format='{{.Config.Image}}'
```

### Backup File Locations

```
/opt/atlasia/backups/
├── daily/                          # 7 day retention
│   ├── backup_YYYYMMDD_HHMMSS.sql.gz
│   ├── backup_YYYYMMDD_HHMMSS.report.txt
│   └── schema_YYYYMMDD_HHMMSS.sql
├── weekly/                         # 4 week retention
│   └── backup_YYYYMMDD_HHMMSS.sql.gz
├── monthly/                        # Kept indefinitely
│   └── backup_YYYYMMDD_HHMMSS.sql.gz
├── predeployment/                  # Pre-deployment backups
│   └── YYYYMMDD_HHMMSS/
│       └── db_backup.sql.gz
└── rollback/                       # Rollback data
    ├── deployment_tags.log         # Deployment history
    └── prerollback_YYYYMMDD_HHMMSS.sql.gz
```

### Retention Policy Summary

| Backup Type | Frequency | Retention | Purpose |
|-------------|-----------|-----------|---------|
| Daily | Every day | 7 days | Regular daily backups |
| Weekly | Every Sunday | 4 weeks | Weekly snapshots |
| Monthly | 1st of month | Indefinite | Long-term archives |
| Pre-deployment | Before each deploy | Indefinite | Rollback safety |
| Pre-rollback | Before rollback | Indefinite | Rollback safety |

### Environment Variables

**Backup Script:**
- `BACKUP_DIR` - Base backup directory (default: ./backups)
- `DB_CONTAINER` - Database container name (auto-detected)
- `DB_NAME` - Database name (auto-detected)
- `DB_USER` - Database user (auto-detected)
- `S3_BUCKET` - S3 bucket for remote backup (optional)

**Rollback Script:**
- `ROLLBACK_TAG` - Docker image tag to rollback to (default: previous)
- `BACKUP_DIR` - Backup directory (default: ./backups)
- `GITHUB_REPO` - GitHub repository for GHCR images (required for image pull)

**Cron Script:**
- `DEPLOY_DIR` - Deployment directory (default: /opt/atlasia)
- `BACKUP_DIR` - Backup directory (default: /opt/atlasia/backups)
- `S3_BUCKET` - S3 bucket for remote backup (optional)
- `HEALTHCHECK_URL` - Healthcheck.io URL to ping on success (optional)
- `ADMIN_EMAIL` - Email for notifications (optional)

---

**Questions?** Check the main [DEPLOYMENT.md](../../DEPLOYMENT.md) or reach out to the team.
