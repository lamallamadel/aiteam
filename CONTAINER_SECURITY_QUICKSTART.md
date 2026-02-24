# Container Security - Quick Start Guide

**TL;DR:** Atlasia now has automated container security scanning with Trivy and runtime hardening.

## What Changed?

✅ **Automated Vulnerability Scanning** - Trivy scans images on every PR/push  
✅ **Runtime Security Hardening** - Read-only filesystems, dropped capabilities, resource limits  
✅ **Deployment Blocking** - CRITICAL vulnerabilities prevent deployment  
✅ **Security Monitoring** - GitHub Security tab integration + automated reports  
✅ **Non-Root Containers** - All containers run as unprivileged users  

## Quick Commands

### Scan Your Local Images
```bash
# Make script executable (first time only)
chmod +x scripts/security-scan.sh

# Build and scan
./scripts/security-scan.sh --build
```

### Check Running Container Security
```bash
# Make script executable (first time only)
chmod +x scripts/security-check.sh

# Run security check
./scripts/security-check.sh
```

### View Scan Results in GitHub
1. Go to repository **Security** tab
2. Click **Code scanning**
3. Filter by tool: **Trivy**

## What Runs Automatically?

### On Every PR/Push
- ✅ Trivy scans backend image (ai-orchestrator)
- ✅ Trivy scans frontend image (nginx)
- ✅ Results uploaded to GitHub Security tab
- ❌ Build fails if CRITICAL vulnerabilities found

### Daily at 2 AM UTC
- ✅ Scheduled Trivy scan (workflow: `container-scan.yml`)
- ✅ Fresh vulnerability database
- ✅ Updated reports in GitHub Security

## Files Added/Modified

### New Files
- `.github/workflows/container-scan.yml` - Trivy scanning workflow
- `infra/trivy-config.yaml` - Trivy configuration
- `docs/CONTAINER_SECURITY.md` - Complete security documentation
- `docs/CONTAINER_SECURITY_IMPLEMENTATION.md` - Implementation summary
- `scripts/security-scan.sh` - Local scanning script
- `scripts/security-check.sh` - Runtime security verification
- `scripts/README.md` - Scripts documentation

### Modified Files
- `ai-orchestrator/Dockerfile` - Minimized packages
- `frontend/Dockerfile` - Non-root user + minimized packages
- `frontend/nginx.conf` - Security headers
- `infra/docker-compose.ai.yml` - Runtime security hardening
- `.github/workflows/ci.yml` - Integrated Trivy scanning
- `DEPLOYMENT.md` - Added container security section
- `AGENTS.md` - Added security reference
- `infra/README.md` - Trivy documentation
- `.gitignore` - Ignore security reports

## Security Features

### Build-Time
- ✅ Minimal Alpine-based images
- ✅ Removed package managers (apk-tools)
- ✅ Multi-stage builds
- ✅ Vulnerability scanning
- ✅ Secrets detection
- ✅ Misconfiguration checks

### Runtime
- ✅ Non-root users (all containers)
- ✅ Read-only root filesystem (ai-orchestrator)
- ✅ Dropped capabilities (CAP_DROP ALL)
- ✅ Resource limits (CPU/memory)
- ✅ Security options (no-new-privileges)
- ✅ Tmpfs mounts with noexec/nosuid

## How to Use

### Development Workflow

**Before committing:**
```bash
# Scan locally
./scripts/security-scan.sh --build

# If CRITICAL vulnerabilities found:
# 1. Fix in Dockerfile/dependencies
# 2. Or add suppression to infra/trivy-config.yaml
# 3. Re-scan
```

**After deployment:**
```bash
# Verify runtime security
./scripts/security-check.sh

# Should show all checks passed
```

### Managing Vulnerabilities

**If scan finds issues:**

1. **Review the report:**
   ```bash
   cat security-reports/backend-table.txt
   ```

2. **Fix if possible:**
   - Update base image version
   - Update dependencies
   - Rebuild and re-scan

3. **Or suppress with justification:**
   Edit `infra/trivy-config.yaml`:
   ```yaml
   vulnerability:
     ignore:
       CVE-2024-12345:
         - package-name: "alpine-baselayout"
           reason: "No fix available, network isolated"
           expiry: "2025-12-31"
   ```

4. **Re-scan:**
   ```bash
   ./scripts/security-scan.sh --build
   ```

## Production Deployment

### Required Steps

1. **Scan images:**
   ```bash
   ./scripts/security-scan.sh --build
   ```

2. **Verify security configuration:**
   ```bash
   ./scripts/security-check.sh
   ```

3. **Deploy:**
   ```bash
   ./scripts/deploy.sh prod
   ```

### Optional (Recommended)

**Enable user namespace remapping:**
```bash
# Edit /etc/docker/daemon.json
{
  "userns-remap": "default"
}

# Restart Docker
sudo systemctl restart docker
```

## Troubleshooting

### "Trivy is not installed"
```bash
# macOS
brew install trivy

# Linux
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo "deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt-get update
sudo apt-get install trivy
```

### "Permission denied" on scripts
```bash
chmod +x scripts/security-scan.sh
chmod +x scripts/security-check.sh
```

### CI/CD pipeline failing
1. Check GitHub Actions logs
2. Look for Trivy scan results
3. Fix CRITICAL vulnerabilities
4. Or add suppression with CTO approval

## Need More Info?

- **Complete guide:** [docs/CONTAINER_SECURITY.md](docs/CONTAINER_SECURITY.md)
- **Deployment docs:** [DEPLOYMENT.md](DEPLOYMENT.md) (see Container Security section)
- **Scripts help:** [scripts/README.md](scripts/README.md)
- **Trivy config:** [infra/trivy-config.yaml](infra/trivy-config.yaml)

## Support

- **GitHub Security Tab:** View vulnerability alerts
- **Security Team:** For questions or approvals
- **Documentation:** See links above

---

**Quick Start Checklist:**
- [ ] Make scripts executable: `chmod +x scripts/security-*.sh`
- [ ] Install Trivy: `brew install trivy` or apt-get
- [ ] Run first scan: `./scripts/security-scan.sh --build`
- [ ] Review results in `security-reports/`
- [ ] Check GitHub Security tab
- [ ] Verify runtime security: `./scripts/security-check.sh`

**Status:** ✅ Ready to use  
**Last Updated:** 2026-02-21
