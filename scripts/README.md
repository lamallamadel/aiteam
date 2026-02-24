# Deployment and Security Scripts

This directory contains scripts for deployment, security scanning, and operational tasks.

## Scripts Overview

### Deployment Scripts

#### `deploy.sh`
Production deployment script with automated backups.

**Usage:**
```bash
./scripts/deploy.sh [staging|prod]
```

**Features:**
- Environment validation
- Pre-deployment backup
- Docker Compose orchestration
- Health checks
- Slack notifications (optional)

**Example:**
```bash
./scripts/deploy.sh prod
```

#### `rollback.sh`
Rollback to previous deployment version.

**Usage:**
```bash
./scripts/rollback.sh [staging|prod]
```

**Features:**
- Lists available backups
- Database restoration
- Container rollback
- Verification checks

### Security Scripts

#### `security-scan.sh`
Comprehensive container vulnerability scanning with Trivy.

**Usage:**
```bash
# Scan existing images
./scripts/security-scan.sh

# Build and scan
./scripts/security-scan.sh --build
```

**What it does:**
- Builds Docker images (optional with `--build` flag)
- Scans backend and frontend images for vulnerabilities
- Detects secrets and misconfigurations
- Generates multiple report formats:
  - Table format (human-readable)
  - JSON format (machine-readable)
  - SARIF format (GitHub Security integration)
- Checks for CRITICAL vulnerabilities
- Fails if CRITICAL vulnerabilities found

**Output:**
Reports saved to `security-reports/` directory:
- `backend-table.txt` - Human-readable vulnerability list
- `backend-report.json` - Full JSON report
- `backend-sarif.json` - SARIF for GitHub Security
- `frontend-table.txt` - Frontend vulnerabilities
- `frontend-report.json` - Frontend JSON report
- `frontend-sarif.json` - Frontend SARIF report

**Example output:**
```
Scanning ai-orchestrator:local...
  → Running vulnerability scan...
  → Generating JSON report...
  → Generating SARIF report...
  ✓ No CRITICAL vulnerabilities found

Scan Summary
Reports saved to: security-reports/
✓ All scans passed - No CRITICAL vulnerabilities
```

#### `security-check.sh`
Runtime security configuration verification for running containers.

**Usage:**
```bash
./scripts/security-check.sh
```

**What it checks:**
1. **Non-root users** - Verifies containers run as unprivileged users
2. **Dropped capabilities** - Confirms all capabilities dropped (CAP_DROP ALL)
3. **Read-only filesystems** - Checks read-only root filesystem where applicable
4. **Security options** - Validates no-new-privileges is enabled
5. **Resource limits** - Verifies CPU and memory limits are set
6. **Current usage** - Shows real-time resource consumption

**Example output:**
```
====================================
1. Non-Root User Verification
====================================

Service: ai-orchestrator
  ✓ Running as non-root user (UID: 1000)

Service: vault
  ✓ Running as non-root user (UID: 100)

====================================
Security Check Summary
====================================

Passed: 12
Failed: 0

✓ All security checks passed
```

## Recommended Workflows

### Before Deployment

Run security scans to ensure no critical vulnerabilities:

```bash
# Build and scan images
./scripts/security-scan.sh --build

# If scans pass, deploy
./scripts/deploy.sh prod

# Verify runtime security
./scripts/security-check.sh
```

### Regular Security Audits

Weekly security verification:

```bash
# Scan running containers
./scripts/security-scan.sh

# Check runtime configuration
./scripts/security-check.sh
```

### Incident Response

If vulnerabilities detected:

```bash
# 1. Scan to identify issues
./scripts/security-scan.sh

# 2. Review reports
cat security-reports/backend-table.txt

# 3. Fix issues (update Dockerfile, dependencies)

# 4. Re-scan to verify
./scripts/security-scan.sh --build

# 5. Deploy fixed version
./scripts/deploy.sh prod
```

## Prerequisites

### For Deployment Scripts
- Docker and Docker Compose installed
- Environment files configured (`.env.staging`, `.env.prod`)
- Docker Compose files present (`docker-compose.staging.yml`, etc.)

### For Security Scripts
- **Trivy installed** (required for security-scan.sh)
  ```bash
  # Install on Linux
  wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
  echo "deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
  sudo apt-get update
  sudo apt-get install trivy

  # Install on macOS
  brew install trivy

  # Install on Windows
  choco install trivy
  ```

- **jq installed** (required for security-check.sh)
  ```bash
  # Linux
  sudo apt-get install jq

  # macOS
  brew install jq

  # Windows
  choco install jq
  ```

- **Docker running** with containers deployed (for security-check.sh)

## Configuration

### Trivy Configuration
Security scanning uses the configuration file at `infra/trivy-config.yaml`.

**Key settings:**
- Severity: HIGH, CRITICAL
- Scanners: vulnerabilities, secrets, misconfigurations
- Auto-refresh: Vulnerability database updated before each scan

**Customize scanning:**
Edit `infra/trivy-config.yaml` to add suppression rules:
```yaml
vulnerability:
  ignore:
    CVE-2024-12345:
      - package-name: "example-package"
        reason: "No fix available, network isolated"
        expiry: "2025-12-31"
```

## Troubleshooting

### security-scan.sh

**Error: "Trivy is not installed"**
```bash
# Install Trivy (see Prerequisites)
brew install trivy  # macOS
sudo apt-get install trivy  # Linux
```

**Error: "Cannot build image"**
```bash
# Ensure you're in the repository root
cd /path/to/atlasia

# Check Dockerfiles exist
ls -la ai-orchestrator/Dockerfile
ls -la frontend/Dockerfile

# Check Docker is running
docker ps
```

**High/Critical vulnerabilities found**
```bash
# Review the report
cat security-reports/backend-table.txt

# Update base images in Dockerfile
# Or add suppression rule to infra/trivy-config.yaml

# Re-scan
./scripts/security-scan.sh --build
```

### security-check.sh

**Error: "No containers running"**
```bash
# Start containers first
docker-compose -f infra/docker-compose.ai.yml up -d

# Verify containers are up
docker-compose -f infra/docker-compose.ai.yml ps
```

**Error: "jq: command not found"**
```bash
# Install jq
sudo apt-get install jq  # Linux
brew install jq          # macOS
```

**Security checks failing**
```bash
# Review specific failures in output
./scripts/security-check.sh

# Update docker-compose.ai.yml with missing configurations
# See docs/CONTAINER_SECURITY.md for guidance

# Restart containers
docker-compose -f infra/docker-compose.ai.yml up -d --force-recreate
```

## Automation

### CI/CD Integration

Security scans run automatically in GitHub Actions:
- Workflow: `.github/workflows/container-scan.yml`
- Trigger: PR, push to main/develop, daily at 2 AM UTC
- Blocking: CRITICAL vulnerabilities fail the build

**Manual trigger:**
```bash
# Push to trigger scan
git push origin feature-branch

# Or run workflow manually in GitHub UI
# Actions → Container Security Scan → Run workflow
```

### Scheduled Scans

Add to crontab for regular scanning:

```bash
# Weekly scan on Sunday at 2 AM
0 2 * * 0 /path/to/scripts/security-scan.sh >> /var/log/security-scan.log 2>&1

# Daily security check at 6 AM
0 6 * * * /path/to/scripts/security-check.sh >> /var/log/security-check.log 2>&1
```

## Documentation

For comprehensive security documentation, see:
- [docs/CONTAINER_SECURITY.md](../docs/CONTAINER_SECURITY.md) - Complete security hardening guide
- [DEPLOYMENT.md](../DEPLOYMENT.md) - Deployment procedures with security section
- [infra/README.md](../infra/README.md) - Infrastructure and Trivy configuration

## Support

For issues or questions:
- Check the troubleshooting section above
- Review [docs/CONTAINER_SECURITY.md](../docs/CONTAINER_SECURITY.md)
- Open an issue in the repository
- Contact the security team

---

**Last Updated:** 2026-02-21  
**Maintained By:** DevOps & Security Team
