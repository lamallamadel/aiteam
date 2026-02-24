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

## 🔐 Security Hardening

### Pre-Deployment Security Steps

Before deploying to production, complete the comprehensive security checklist in `docs/SECURITY_CHECKLIST.md`. Critical items include:

#### 1. HashiCorp Vault Setup

**CRITICAL**: All secrets must be stored in HashiCorp Vault, not in environment variables or config files.

```bash
# Start Vault in production mode (not dev mode)
docker run -d --cap-add=IPC_LOCK --name=vault \
  -p 8200:8200 \
  -v /opt/vault/data:/vault/data \
  -v /opt/vault/config:/vault/config \
  hashicorp/vault:latest server

# Initialize Vault (first time only)
export VAULT_ADDR='http://localhost:8200'
vault operator init -key-shares=5 -key-threshold=3

# Save unseal keys and root token in separate secure locations
# Distribute unseal keys to 5 different custodians

# Unseal Vault (requires 3 of 5 keys)
vault operator unseal <key1>
vault operator unseal <key2>
vault operator unseal <key3>

# Initialize all secrets
cd infra && chmod +x vault-init.sh && ./vault-init.sh

# Enable audit logging
vault audit enable file file_path=/var/log/vault/audit.log

# Configure AppRole authentication (production)
vault auth enable approle
vault policy write atlasia-read - <<EOF
path "secret/data/atlasia/*" {
  capabilities = ["read", "list"]
}
EOF

vault write auth/approle/role/atlasia-orchestrator \
  token_policies="atlasia-read" \
  token_ttl=1h \
  bind_secret_id=true

# Get role credentials
ROLE_ID=$(vault read -field=role_id auth/approle/role/atlasia-orchestrator/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/atlasia-orchestrator/secret-id)
```

**Update `.env.prod` with Vault credentials:**
```bash
VAULT_ADDR=http://localhost:8200
SPRING_CLOUD_VAULT_ENABLED=true
SPRING_CLOUD_VAULT_AUTHENTICATION=APPROLE
SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID=${ROLE_ID}
SPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=${SECRET_ID}
```

See `docs/VAULT_SETUP.md` for complete Vault setup guide.

#### 2. TLS/SSL Configuration

**CRITICAL**: Enable TLS 1.3 for all services.

```bash
# Application Server TLS
# Store keystore in Vault
vault kv put secret/atlasia/ssl-keystore-path value=file:/etc/ssl/certs/keystore.p12
vault kv put secret/atlasia/ssl-keystore-password value=$(openssl rand -base64 32)

# Update application.yml
cat >> ai-orchestrator/src/main/resources/application.yml <<EOF
server:
  ssl:
    enabled: true
    key-store: \${vault.secret.data.atlasia.ssl-keystore-path}
    key-store-password: \${vault.secret.data.atlasia.ssl-keystore-password}
    key-store-type: PKCS12
    enabled-protocols: TLSv1.3
    ciphers: TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
EOF

# PostgreSQL TLS
# Update docker-compose.prod.yml
command: >
  postgres
  -c ssl=on
  -c ssl_cert_file=/var/lib/postgresql/certs/server.crt
  -c ssl_key_file=/var/lib/postgresql/certs/server.key
  -c ssl_min_protocol_version=TLSv1.3
```

See `docs/SECURITY.md` for complete TLS setup guide.

#### 3. Secret Rotation Schedule

**CRITICAL**: Establish automated secret rotation.

| Secret | Rotation Frequency | Automation |
|--------|-------------------|------------|
| JWT Signing Key | Monthly | SecretRotationScheduler |
| Database Password | Quarterly | Manual |
| OAuth2 Secrets | Quarterly | Manual (update in provider console) |
| Encryption Key | Quarterly | Manual with re-encryption |
| SSL Certificates | 30 days before expiry | certbot (automated) |

```bash
# Schedule monthly JWT rotation (runs automatically via @Scheduled)
# Check logs: grep "JWT secret rotation" /var/log/ai-orchestrator/application.log

# Rotate database password (quarterly)
NEW_DB_PASS=$(openssl rand -base64 32)
vault kv put secret/atlasia/db-password value="${NEW_DB_PASS}"
# Update PostgreSQL: ALTER USER aiteam WITH PASSWORD '${NEW_DB_PASS}';
# Restart application to pick up new password

# Rotate encryption key (quarterly)
cd ai-orchestrator
mvn exec:java -Dexec.mainClass="com.atlasia.ai.migration.EncryptionKeyRotation"
```

#### 4. Container Security Hardening

**CRITICAL**: Verify all container security controls are in place.

```bash
# Verify non-root users
docker-compose -f docker-compose.prod.yml exec ai-orchestrator id
# Expected: uid=1000(appuser) gid=1000(appgroup)

# Verify read-only filesystem
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.ReadonlyRootfs'
# Expected: true

# Verify dropped capabilities
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.CapDrop'
# Expected: ["ALL"]

# Verify security options
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.SecurityOpt'
# Expected: ["no-new-privileges:true"]

# Enable user namespace remapping (production only)
sudo nano /etc/docker/daemon.json
# Add: {"userns-remap": "default"}
sudo systemctl restart docker
```

See `docs/CONTAINER_SECURITY.md` for complete container hardening guide.

#### 5. Vulnerability Scanning

**CRITICAL**: Scan all images before deployment.

```bash
# Scan backend image
trivy image --config infra/trivy-config.yaml ghcr.io/your-org/ai-orchestrator:production
# Expected: No CRITICAL vulnerabilities

# Scan frontend image
trivy image --config infra/trivy-config.yaml ghcr.io/your-org/frontend:production

# Scan for secrets
trivy image --scanners secret ghcr.io/your-org/ai-orchestrator:production
# Expected: No secrets found

# OWASP Dependency Check
cd ai-orchestrator && mvn dependency-check:check
# Expected: No HIGH/CRITICAL vulnerabilities

# NPM Audit
cd frontend && npm audit --audit-level=moderate
# Expected: No vulnerabilities
```

#### 6. Network Security

**CRITICAL**: Configure firewall and rate limiting.

```bash
# Allow SSH, HTTP, HTTPS only
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw deny 5432/tcp  # Block PostgreSQL from internet
sudo ufw deny 8200/tcp  # Block Vault from internet
sudo ufw enable

# Verify firewall status
sudo ufw status verbose

# Test external access (should fail)
nmap -p 5432 your-public-ip  # PostgreSQL should be closed
nmap -p 8200 your-public-ip  # Vault should be closed
```

**Rate Limiting**: Configured in application (see `RateLimitService.java`)
- Login attempts: Limited by BruteForceProtectionService (5 attempts → lockout)
- File downloads: 10 requests per minute per user
- WebSocket connections: Configurable limits per user
- API endpoints: Configurable rate limits (future)

#### 7. Audit Logging

**CRITICAL**: Verify audit logging is enabled.

```bash
# Check database audit tables exist
psql -U aiteam -d ai -c "SELECT tablename FROM pg_tables WHERE tablename LIKE '%audit%';"

# Expected tables:
# - auth_audit_logs
# - collaboration_events
# - (future: download_audit_logs)

# Verify Vault audit logging
cat /var/log/vault/audit.log | tail -n 10

# Configure log retention (90 days minimum)
# Update logrotate configuration
sudo nano /etc/logrotate.d/ai-orchestrator
```

```
/var/log/ai-orchestrator/*.log {
    daily
    rotate 90
    compress
    delaycompress
    missingok
    notifempty
    create 0644 appuser appgroup
}
```

#### 8. Database Security

**CRITICAL**: Enable database encryption and access controls.

```bash
# Enable column-level encryption
# Verify encrypted columns in database
psql -U aiteam -d ai -c "\d+ oauth2_accounts" | grep encrypted
# Expected: access_token_encrypted, refresh_token_encrypted

# Verify encryption key in Vault
vault kv get secret/atlasia/encryption-key

# Enable encrypted volume for database (optional but recommended)
# AWS: Use EBS encryption
# GCP: Use disk encryption
# On-prem: Use LUKS
sudo cryptsetup luksFormat /dev/sdb
sudo cryptsetup luksOpen /dev/sdb postgres_encrypted
sudo mkfs.ext4 /dev/mapper/postgres_encrypted
sudo mount /dev/mapper/postgres_encrypted /var/lib/postgresql/data
```

See `docs/SECURITY.md` for database encryption guide.

#### 9. Monitoring & Alerting

**RECOMMENDED**: Set up security monitoring.

```bash
# Enable Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep orchestrator_

# Configure Grafana dashboards
# Import: infra/grafana-websocket-dashboard.json

# Set up alerting rules (example)
cat > prometheus-alerts.yml <<EOF
groups:
  - name: security_alerts
    rules:
      - alert: HighAuthenticationFailureRate
        expr: rate(orchestrator_auth_failures_total[5m]) > 10
        for: 5m
        annotations:
          summary: "High authentication failure rate detected"
      
      - alert: UnauthorizedAccessAttempt
        expr: rate(orchestrator_authorization_failures_total[5m]) > 5
        for: 5m
        annotations:
          summary: "Unauthorized access attempts detected"
EOF
```

#### 10. Incident Response Plan

**CRITICAL**: Document incident response procedures.

**Security Contact**: security@atlasia.ai

**Incident Response Steps**:
1. **Detect**: Monitor logs, alerts, user reports
2. **Contain**: Isolate affected systems, revoke compromised credentials
3. **Assess**: Determine scope, impact, and root cause
4. **Remediate**: Fix vulnerability, rotate secrets, restore from backup
5. **Notify**: Inform stakeholders per disclosure policy (90-day)
6. **Document**: Update incident log and lessons learned
7. **Review**: Update threat model and security controls

See `SECURITY.md` (root level) for vulnerability reporting process.

### Security Checklist (Quick Reference)

- [ ] Vault configured with AppRole authentication
- [ ] All secrets stored in Vault (no secrets in .env or config files)
- [ ] TLS 1.3 enabled for application and database
- [ ] SSL certificates valid and auto-renewal configured
- [ ] Secret rotation schedule established
- [ ] Trivy scan passing (no CRITICAL vulnerabilities)
- [ ] OWASP Dependency-Check passing
- [ ] NPM Audit passing
- [ ] Containers run as non-root users
- [ ] Read-only root filesystem enabled
- [ ] Capabilities dropped (CAP_DROP ALL)
- [ ] User namespace remapping enabled (production)
- [ ] Firewall configured (only 22, 80, 443 open)
- [ ] Database port not exposed to internet
- [ ] Vault port not exposed to internet
- [ ] Audit logging enabled (Vault, application, database)
- [ ] Database encryption enabled (column-level, volume)
- [ ] Backups encrypted and tested
- [ ] Monitoring and alerting configured
- [ ] Incident response plan documented

**For complete checklist, see `docs/SECURITY_CHECKLIST.md`**

---

## 🛡️ Container Security Hardening

### Overview

Atlasia implements defense-in-depth container security using:
- **Trivy vulnerability scanning** - Automated scanning for OS and library vulnerabilities
- **Non-root users** - All containers run as unprivileged users
- **Minimal images** - Alpine-based images with unnecessary packages removed
- **Runtime restrictions** - Read-only filesystems, dropped capabilities, resource limits
- **User namespace remapping** - Host-level isolation (optional but recommended)

### Vulnerability Scanning with Trivy

#### Automated Scanning

Trivy scans run automatically in the CI/CD pipeline:
- **On every PR and push** to main/develop branches
- **Daily at 2 AM UTC** for continuous monitoring
- **Blocks deployment** on CRITICAL vulnerabilities

```bash
# View scan results in GitHub Security tab
# Settings > Security > Code scanning alerts
```

#### Manual Scanning

Run Trivy locally before committing:

```bash
# Scan backend image
docker build -t ai-orchestrator:test ./ai-orchestrator
trivy image --config infra/trivy-config.yaml ai-orchestrator:test

# Scan frontend image
docker build -t frontend:test ./frontend
trivy image --config infra/trivy-config.yaml frontend:test

# Scan for HIGH and CRITICAL only
trivy image --severity HIGH,CRITICAL ai-orchestrator:test

# Generate SARIF report for GitHub
trivy image --format sarif --output results.sarif ai-orchestrator:test
```

#### Trivy Configuration

Configuration file: `infra/trivy-config.yaml`

Key settings:
- **Severity threshold**: HIGH and CRITICAL vulnerabilities reported
- **Blocking**: CRITICAL vulnerabilities fail the build
- **Scanners**: OS packages, libraries, secrets, misconfigurations
- **Auto-update**: Vulnerability database refreshed before each scan
- **Suppression rules**: Documented exceptions for accepted risks

#### Managing Vulnerability Exceptions

Add suppression rules in `infra/trivy-config.yaml`:

```yaml
vulnerability:
  ignore:
    CVE-2024-12345:
      - package-name: "example-package"
        reason: "No fix available, mitigated by network isolation"
        expiry: "2025-12-31"  # Mandatory expiry date
```

**Requirements for suppressions:**
- Document business justification
- Set expiry date for review
- Requires CTO approval for CRITICAL CVEs
- Review quarterly during security audit

### Container Image Hardening

#### Non-Root Users

All containers run as non-root users:

**Backend (ai-orchestrator):**
```dockerfile
USER appuser  # UID 1000, GID 1000
```

**Frontend (nginx):**
```dockerfile
USER nginx  # UID 101, GID 101
```

**Database (postgres):**
```dockerfile
USER postgres  # UID 999, GID 999
```

Verify:
```bash
docker-compose -f infra/docker-compose.ai.yml exec ai-orchestrator id
# Expected: uid=1000(appuser) gid=1000(appgroup)
```

#### Minimal Image Size

Images use Alpine Linux with unnecessary packages removed:

```dockerfile
# Remove package manager after installation
RUN apk del --purge apk-tools && \
    rm -rf /var/cache/apk/* /tmp/* /var/tmp/*
```

**Image sizes:**
- `ai-orchestrator`: ~250MB (JRE + application)
- `frontend`: ~45MB (nginx + static files)
- `postgres`: ~240MB (official image)

### Runtime Security

#### Read-Only Filesystems

Containers run with read-only root filesystems where possible:

```yaml
read_only: true
tmpfs:
  - /tmp:noexec,nosuid,size=100m
  - /app/logs:noexec,nosuid,size=500m
```

**Benefits:**
- Prevents malware from writing to filesystem
- Stops unauthorized file modifications
- Limits exploit persistence

**Writable tmpfs volumes:**
- `/tmp` - Temporary files (100MB limit)
- `/app/logs` - Application logs (500MB limit)
- Both mounted with `noexec` (prevents script execution)

#### Dropped Capabilities

All unnecessary Linux capabilities are dropped:

```yaml
cap_drop:
  - ALL  # Drop all capabilities
cap_add:
  - NET_BIND_SERVICE  # Only add what's needed (bind to port 80/8080)
```

**Database capabilities** (minimal set):
```yaml
cap_add:
  - CHOWN          # Change file ownership
  - DAC_OVERRIDE   # Bypass file permission checks
  - FOWNER         # Bypass ownership checks
  - SETGID         # Set group ID
  - SETUID         # Set user ID
```

Verify capabilities:
```bash
docker inspect atlasia-vault | grep -A 10 "CapAdd"
```

#### Security Options

Additional security hardening:

```yaml
security_opt:
  - no-new-privileges:true  # Prevent privilege escalation
```

**no-new-privileges:**
- Blocks setuid/setgid binaries
- Prevents gaining additional privileges via execve()
- Mitigates container breakout attempts

#### Resource Limits

CPU and memory limits prevent DoS attacks:

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'      # Max 2 CPU cores
      memory: 2G       # Max 2GB RAM
    reservations:
      cpus: '0.5'      # Guaranteed 0.5 CPU cores
      memory: 512M     # Guaranteed 512MB RAM
```

**Per-service limits:**
- **ai-orchestrator**: 2 CPU / 2GB RAM (max), 0.5 CPU / 512MB RAM (reserved)
- **ai-db**: 2 CPU / 2GB RAM (max), 0.5 CPU / 256MB RAM (reserved)
- **vault**: 1 CPU / 512MB RAM (max), 0.25 CPU / 128MB RAM (reserved)

Monitor resource usage:
```bash
docker stats --no-stream
```

### User Namespace Remapping

User namespace remapping provides host-level isolation by mapping container UIDs to unprivileged host UIDs.

**Example:** Container root (UID 0) maps to host UID 100000, preventing container root from accessing host resources.

#### Enable User Namespace Remapping

1. **Edit Docker daemon configuration:**
   ```bash
   sudo nano /etc/docker/daemon.json
   ```

2. **Add configuration:**
   ```json
   {
     "userns-remap": "default",
     "log-driver": "json-file",
     "log-opts": {
       "max-size": "10m",
       "max-file": "3"
     }
   }
   ```

3. **Restart Docker daemon:**
   ```bash
   sudo systemctl restart docker
   ```

4. **Verify remapping:**
   ```bash
   # Check subuid/subgid mappings
   cat /etc/subuid
   cat /etc/subgid
   
   # Run container and verify UID mapping
   docker run --rm alpine id
   # Container sees: uid=0(root)
   
   # Check from host
   ps aux | grep [p]rocess-in-container
   # Host sees: uid=100000
   ```

**Important notes:**
- Existing volumes may need ownership updates
- Not compatible with some Docker features (host networking, privileged mode)
- Recommended for production but optional for development

#### Volume Ownership After Remapping

Update volume ownership to match remapped UIDs:

```bash
# Find remapped UID range
grep dockremap /etc/subuid
# Output: dockremap:100000:65536

# Update PostgreSQL volume
sudo chown -R 100999:100999 /var/lib/docker/volumes/ai_db/_data

# Update Vault volume
sudo chown -R 100100:100100 /var/lib/docker/volumes/vault_data/_data
```

### Security Monitoring

#### Check Container Security Status

```bash
# Verify non-root users
for svc in ai-orchestrator ai-db vault; do
  echo "=== $svc ==="
  docker-compose -f infra/docker-compose.ai.yml exec $svc id
done

# Check capabilities
docker inspect $(docker ps -qf "name=ai-orchestrator") | \
  jq '.[0].HostConfig.CapDrop, .[0].HostConfig.CapAdd'

# Verify read-only filesystem
docker inspect $(docker ps -qf "name=ai-orchestrator") | \
  jq '.[0].HostConfig.ReadonlyRootfs'

# Check resource limits
docker stats --no-stream --format \
  "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

#### Scan Running Containers

```bash
# Scan running container for vulnerabilities
trivy image $(docker inspect --format='{{.Image}}' atlasia-ai-orchestrator)

# Scan for secrets in running container
trivy image --scanners secret $(docker inspect --format='{{.Image}}' atlasia-ai-orchestrator)
```

#### Security Audit Checklist

Run quarterly:

- [ ] Review Trivy scan results in GitHub Security tab
- [ ] Update suppression rules (remove expired exceptions)
- [ ] Verify all containers run as non-root (`docker exec <container> id`)
- [ ] Check for unnecessary capabilities (`docker inspect | grep Cap`)
- [ ] Review resource usage and adjust limits if needed
- [ ] Update base images (Alpine, JRE, nginx, postgres)
- [ ] Test user namespace remapping in staging
- [ ] Audit secrets in images (`trivy image --scanners secret`)
- [ ] Review and update `infra/trivy-config.yaml`
- [ ] Document any new suppression rules

### Incident Response

**If CRITICAL vulnerability detected:**

1. **Assess impact:**
   ```bash
   # Check affected images
   trivy image --severity CRITICAL ai-orchestrator:latest
   ```

2. **Immediate mitigation:**
   - Check if suppression is acceptable (network isolation, etc.)
   - If not, take containers offline until patched

3. **Remediation:**
   ```bash
   # Update base image in Dockerfile
   # FROM eclipse-temurin:17.0.X-jre-alpine  # New version
   
   # Rebuild and test
   docker-compose -f infra/docker-compose.ai.yml build
   trivy image ai-orchestrator:latest
   
   # Deploy fix
   docker-compose -f infra/docker-compose.ai.yml up -d
   ```

4. **Verification:**
   ```bash
   # Confirm vulnerability resolved
   trivy image --severity CRITICAL ai-orchestrator:latest
   # Expected: No vulnerabilities found
   ```

5. **Documentation:**
   - Update CHANGELOG.md
   - Notify stakeholders
   - Document in incident log

### Best Practices Summary

✅ **DO:**
- Run Trivy scans before every deployment
- Keep base images updated (alpine, temurin, nginx, postgres)
- Review GitHub Security alerts weekly
- Document all vulnerability suppressions with expiry dates
- Use read-only filesystems where possible
- Drop all capabilities except required ones
- Set resource limits on all services
- Enable user namespace remapping in production
- Rotate secrets quarterly
- Monitor resource usage regularly

❌ **DON'T:**
- Suppress CRITICAL vulnerabilities without CTO approval
- Run containers as root user
- Use `:latest` tags in production
- Ignore HIGH severity vulnerabilities for >30 days
- Add unnecessary capabilities
- Disable security features for convenience
- Share credentials between environments
- Commit secrets to git
- Skip Trivy scans to speed up deployment

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
- [ ] Trivy security scans passing (no CRITICAL vulnerabilities)
- [ ] Container security verified (run `scripts/security-check.sh`)
- [ ] Server specs confirmed (2+ vCPU, 4+ GB RAM)
- [ ] Docker & Docker Compose installed
- [ ] User namespace remapping enabled (production only)
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
- [Container Security Hardening Guide](docs/CONTAINER_SECURITY.md) - Atlasia-specific security documentation
- [Trivy Security Scanner](https://aquasecurity.github.io/trivy/)
- [NIST Container Security Guidelines](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-190.pdf)

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
