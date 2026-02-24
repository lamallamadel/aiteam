# Container Security Hardening Guide

**Last Updated:** 2026-02-21  
**Owner:** Security Team  
**Review Cycle:** Quarterly

---

## Overview

This document provides comprehensive guidance on container security hardening for the Atlasia AI Orchestrator platform. It covers vulnerability scanning with Trivy, runtime security best practices, and incident response procedures.

## Table of Contents

1. [Security Architecture](#security-architecture)
2. [Vulnerability Scanning](#vulnerability-scanning)
3. [Image Hardening](#image-hardening)
4. [Runtime Security](#runtime-security)
5. [User Namespace Remapping](#user-namespace-remapping)
6. [Security Monitoring](#security-monitoring)
7. [Incident Response](#incident-response)
8. [Best Practices](#best-practices)

---

## Security Architecture

### Defense-in-Depth Approach

Atlasia implements multiple layers of security controls:

```
┌─────────────────────────────────────────────┐
│  Layer 1: Build-Time Security               │
│  - Minimal base images (Alpine Linux)       │
│  - Trivy vulnerability scanning             │
│  - Secrets detection                        │
│  - Misconfiguration checks                  │
└─────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────┐
│  Layer 2: Image Security                    │
│  - Non-root users (UID 1000+)              │
│  - Removed package managers                 │
│  - Minimal attack surface                   │
└─────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────┐
│  Layer 3: Runtime Security                  │
│  - Read-only root filesystem                │
│  - Dropped capabilities (CAP_DROP ALL)      │
│  - Resource limits (CPU/Memory)             │
│  - Security options (no-new-privileges)     │
└─────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────┐
│  Layer 4: Host-Level Isolation              │
│  - User namespace remapping                 │
│  - Network isolation                        │
│  - Volume security                          │
└─────────────────────────────────────────────┘
```

### Security Objectives

- **Prevent unauthorized access** - Non-root users, dropped capabilities
- **Minimize attack surface** - Minimal images, removed unnecessary packages
- **Detect vulnerabilities early** - Automated Trivy scanning in CI/CD
- **Limit blast radius** - Read-only filesystems, resource limits
- **Enable rapid response** - SARIF reports, GitHub Security integration

---

## Vulnerability Scanning

### Trivy Overview

Trivy is a comprehensive vulnerability scanner for containers that detects:
- **OS vulnerabilities** - CVEs in Alpine, Debian, Ubuntu packages
- **Library vulnerabilities** - CVEs in Java, Node.js, Python dependencies
- **Secrets** - API keys, passwords, tokens accidentally committed
- **Misconfigurations** - Insecure Docker/Kubernetes configurations

### Automated Scanning Pipeline

#### GitHub Actions Workflow

Location: `.github/workflows/container-scan.yml`

**Trigger conditions:**
- Pull request to any branch
- Push to `main` or `develop` branches
- Daily at 2 AM UTC (scheduled scan)

**Scan process:**
1. Build Docker images (backend and frontend)
2. Run Trivy scan with HIGH/CRITICAL severity filter
3. Generate SARIF report
4. Upload to GitHub Security tab
5. Block deployment if CRITICAL vulnerabilities found

**Example workflow:**
```yaml
- name: Run Trivy vulnerability scanner
  uses: aquasecurity/trivy-action@0.28.0
  with:
    image-ref: ai-orchestrator:${{ github.sha }}
    format: 'sarif'
    severity: 'HIGH,CRITICAL'
    vuln-type: 'os,library'
    scanners: 'vuln,secret,misconfig'
    trivy-config: 'infra/trivy-config.yaml'
```

#### CI/CD Integration

Location: `.github/workflows/ci.yml`

Container scanning is integrated into the main CI pipeline:
```
backend tests → frontend tests → container-scan → e2e tests
                                       ↓
                                  BLOCK if CRITICAL
```

**Deployment blocking:**
- CRITICAL vulnerabilities: Fail the build (exit code 1)
- HIGH vulnerabilities: Report but allow deployment
- MEDIUM/LOW: Report only

### Manual Scanning

#### Scan Local Images

```bash
# Scan backend image
cd ai-orchestrator
docker build -t ai-orchestrator:local .
trivy image --config ../infra/trivy-config.yaml ai-orchestrator:local

# Scan frontend image
cd frontend
docker build -t frontend:local .
trivy image --config ../infra/trivy-config.yaml frontend:local
```

#### Scan Running Containers

```bash
# Get image hash of running container
docker inspect --format='{{.Image}}' atlasia-ai-orchestrator

# Scan the image
trivy image <image-hash>
```

#### Advanced Scanning Options

```bash
# Scan for secrets only
trivy image --scanners secret ai-orchestrator:latest

# Scan with specific severity
trivy image --severity CRITICAL ai-orchestrator:latest

# Generate JSON report
trivy image --format json --output results.json ai-orchestrator:latest

# Scan with custom policy
trivy image --policy custom-policy.rego ai-orchestrator:latest
```

### Trivy Configuration

Location: `infra/trivy-config.yaml`

#### Key Settings

**Vulnerability Database:**
```yaml
db:
  auto-refresh: true  # Update DB before each scan
  repository: "ghcr.io/aquasecurity/trivy-db"
```

**Severity Configuration:**
```yaml
severity:
  default: "HIGH"  # Minimum severity to report
  exit-on:
    - CRITICAL     # Fail build on CRITICAL
```

**Scanners:**
```yaml
scan:
  security-checks:
    - vuln    # OS/library vulnerabilities
    - secret  # Exposed secrets
    - config  # Misconfigurations
```

#### Suppression Rules

Add suppression for accepted risks:

```yaml
vulnerability:
  ignore:
    CVE-2024-12345:
      - package-name: "example-lib"
        reason: "No fix available, mitigated by network isolation"
        expiry: "2025-12-31"  # Mandatory
```

**Suppression requirements:**
- Documented business justification
- Expiry date for quarterly review
- CTO approval for CRITICAL CVEs
- Alternative mitigation documented

### Viewing Scan Results

#### GitHub Security Tab

1. Navigate to repository
2. Click **Security** tab
3. Click **Code scanning** in left sidebar
4. View alerts filtered by:
   - Severity (Critical, High, Medium, Low)
   - Tool (Trivy)
   - Category (container-backend, container-frontend)

#### CI/CD Pipeline Logs

```bash
# View workflow run
gh run view <run-id>

# View specific job logs
gh run view <run-id> --job container-scan

# Download SARIF report
gh run download <run-id> --name trivy-backend-results
```

---

## Image Hardening

### Base Image Selection

**Current base images:**
- **Backend build**: `maven:3.9-eclipse-temurin-17-alpine`
- **Backend runtime**: `eclipse-temurin:17-jre-alpine`
- **Frontend build**: `node:22-alpine`
- **Frontend runtime**: `nginx:1.27-alpine`

**Why Alpine?**
- Minimal size (~5MB base image)
- Fewer packages = smaller attack surface
- Security-focused distribution
- Fast updates for CVEs

### Multi-Stage Builds

**Benefits:**
- Build dependencies not included in runtime image
- Smaller final image size
- Reduced attack surface

**Backend Dockerfile structure:**
```dockerfile
# Stage 1: Build (large image with Maven)
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
# ... build application ...

# Stage 2: Runtime (minimal image with JRE only)
FROM eclipse-temurin:17-jre-alpine
# ... copy JAR only ...
```

### Package Cleanup

Remove unnecessary packages post-installation:

```dockerfile
# Remove package manager to prevent runtime package installation
RUN apk del --purge apk-tools && \
    rm -rf /var/cache/apk/* /tmp/* /var/tmp/*
```

**Impact:**
- Prevents attackers from installing malicious packages
- Reduces image size by 10-20MB
- Eliminates package manager vulnerabilities

### Non-Root Users

**Backend (ai-orchestrator):**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser  # UID 1000, GID 1000
```

**Frontend (nginx):**
```dockerfile
USER nginx  # UID 101, GID 101
```

**Verification:**
```bash
docker-compose -f infra/docker-compose.ai.yml exec ai-orchestrator id
# Output: uid=1000(appuser) gid=1000(appgroup)
```

### File Permissions

Set correct ownership for application files:

```dockerfile
COPY --from=builder --chown=appuser:appgroup /build/app.jar .
```

Create writable directories with proper permissions:

```dockerfile
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs
```

### Health Checks

Enable Docker health checks for monitoring:

```dockerfile
HEALTHCHECK --interval=10s --timeout=5s --retries=20 --start-period=30s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

---

## Runtime Security

### Read-Only Root Filesystem

**Configuration (docker-compose.ai.yml):**
```yaml
services:
  ai-orchestrator:
    read_only: true
    tmpfs:
      - /tmp:noexec,nosuid,size=100m
      - /app/logs:noexec,nosuid,size=500m
```

**Benefits:**
- Prevents malware from writing to filesystem
- Stops unauthorized file modifications
- Limits exploit persistence
- Meets compliance requirements (PCI-DSS, SOC 2)

**Tmpfs volumes:**
- `/tmp` - Temporary files (100MB max)
- `/app/logs` - Application logs (500MB max)
- Mounted with `noexec` (prevents script execution)
- Mounted with `nosuid` (prevents SUID binaries)

**Testing:**
```bash
# Try to write to read-only filesystem (should fail)
docker-compose -f infra/docker-compose.ai.yml exec ai-orchestrator \
  touch /test.txt
# Expected error: Read-only file system

# Write to tmpfs (should succeed)
docker-compose -f infra/docker-compose.ai.yml exec ai-orchestrator \
  touch /tmp/test.txt
# Expected: Success
```

### Dropped Capabilities

**Default (insecure):** Containers run with ~15 capabilities
**Hardened (Atlasia):** Drop ALL, add only required ones

**Configuration:**
```yaml
services:
  ai-orchestrator:
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE  # Bind to port 80/8080
```

**PostgreSQL (minimal set):**
```yaml
  ai-db:
    cap_drop:
      - ALL
    cap_add:
      - CHOWN          # Change file ownership
      - DAC_OVERRIDE   # Bypass file permission checks
      - FOWNER         # Bypass ownership checks
      - SETGID         # Set group ID
      - SETUID         # Set user ID
```

**Why drop capabilities?**
- Prevents privilege escalation
- Limits kernel access
- Reduces container breakout risk
- Follows principle of least privilege

**Verification:**
```bash
docker inspect $(docker ps -qf "name=ai-orchestrator") | \
  jq '.[0].HostConfig.CapDrop, .[0].HostConfig.CapAdd'
```

### Security Options

**no-new-privileges:**
```yaml
security_opt:
  - no-new-privileges:true
```

**What it does:**
- Blocks setuid/setgid binaries
- Prevents gaining additional privileges via execve()
- Mitigates container breakout attempts

**Example attack prevented:**
```bash
# Attacker tries to exploit setuid binary
./exploit_suid_binary
# Blocked: Process cannot gain elevated privileges
```

### Resource Limits

**Purpose:**
- Prevent DoS attacks
- Ensure fair resource allocation
- Protect host from resource exhaustion

**Configuration:**
```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 2G
    reservations:
      cpus: '0.5'
      memory: 512M
```

**Per-Service Limits:**

| Service | CPU Limit | Memory Limit | CPU Reserved | Memory Reserved |
|---------|-----------|--------------|--------------|-----------------|
| ai-orchestrator | 2.0 | 2G | 0.5 | 512M |
| ai-db | 2.0 | 2G | 0.5 | 256M |
| vault | 1.0 | 512M | 0.25 | 128M |

**Monitoring:**
```bash
docker stats --no-stream --format \
  "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

### Network Isolation

**Future enhancement:** Custom Docker networks with segmentation

```yaml
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # No external access

services:
  ai-orchestrator:
    networks:
      - frontend  # Accessible from nginx
      - backend   # Can access database
  
  ai-db:
    networks:
      - backend   # Isolated from internet
```

---

## User Namespace Remapping

### Overview

User namespace remapping provides host-level isolation by remapping container UIDs to unprivileged host UIDs.

**Example:**
- Container runs as root (UID 0)
- Host sees process as UID 100000
- Container root cannot access host files owned by real root

### Benefits

- **Container breakout protection**: Even if attacker gains root in container, they're unprivileged on host
- **Defense in depth**: Additional isolation layer beyond capabilities
- **Compliance**: Required by some security frameworks (NIST 800-190)

### Enable Remapping

**1. Edit Docker daemon config:**
```bash
sudo nano /etc/docker/daemon.json
```

**2. Add configuration:**
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

**3. Restart Docker:**
```bash
sudo systemctl restart docker
```

**4. Verify:**
```bash
# Check subuid/subgid mappings
cat /etc/subuid
# Output: dockremap:100000:65536

cat /etc/subgid
# Output: dockremap:100000:65536
```

### UID Mapping

**Default mapping:**
```
Container UID → Host UID
0 (root)      → 100000
1000          → 101000
65535         → 165535
```

**Verification:**
```bash
# Start container
docker run -d --name test alpine sleep 1000

# Container sees UID 0
docker exec test id
# Output: uid=0(root) gid=0(root)

# Host sees UID 100000
ps aux | grep "sleep 1000"
# Output: 100000   12345  ...  sleep 1000
```

### Volume Ownership

After enabling remapping, update volume ownership:

```bash
# Find remapped UID range
grep dockremap /etc/subuid
# Output: dockremap:100000:65536

# Update PostgreSQL volume (postgres UID 999 → 100999)
sudo chown -R 100999:100999 /var/lib/docker/volumes/ai_db/_data

# Update Vault volume (vault UID 100 → 100100)
sudo chown -R 100100:100100 /var/lib/docker/volumes/vault_data/_data
```

### Limitations

**Not compatible with:**
- Host networking mode (`network_mode: host`)
- Privileged containers (`privileged: true`)
- Sharing PID namespace with host
- Direct volume mounts from `/etc`, `/var`

**Workarounds:**
- Use custom networks instead of host mode
- Avoid privileged containers (use capabilities instead)
- Use named volumes instead of bind mounts

### Troubleshooting

**Issue:** Permission denied accessing volumes
```bash
# Check volume ownership
ls -la /var/lib/docker/volumes/ai_db/_data
# Should show: 100999:100999

# Fix ownership
sudo chown -R 100999:100999 /var/lib/docker/volumes/ai_db/_data
```

**Issue:** Container won't start after enabling remapping
```bash
# Check Docker logs
sudo journalctl -u docker -n 50

# Disable remapping temporarily
sudo rm /etc/docker/daemon.json
sudo systemctl restart docker
```

---

## Security Monitoring

### Daily Checks

**Container security status:**
```bash
#!/bin/bash
# save as scripts/security-check.sh

echo "=== Non-Root User Verification ==="
for svc in ai-orchestrator ai-db vault; do
  echo "Service: $svc"
  docker-compose -f infra/docker-compose.ai.yml exec $svc id
done

echo ""
echo "=== Capability Check ==="
docker inspect $(docker ps -qf "name=ai-orchestrator") | \
  jq '.[0].HostConfig.CapDrop, .[0].HostConfig.CapAdd'

echo ""
echo "=== Read-Only Filesystem ==="
docker inspect $(docker ps -qf "name=ai-orchestrator") | \
  jq '.[0].HostConfig.ReadonlyRootfs'

echo ""
echo "=== Resource Usage ==="
docker stats --no-stream --format \
  "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

### Weekly Trivy Scans

```bash
# Scan all running containers
for container in $(docker ps --format '{{.Names}}'); do
  echo "Scanning $container..."
  image=$(docker inspect --format='{{.Image}}' $container)
  trivy image --severity HIGH,CRITICAL $image
done
```

### Monthly Security Audit

**Checklist:**
- [ ] Review GitHub Security alerts (all severities)
- [ ] Update suppression rules in `trivy-config.yaml`
- [ ] Verify container users: `docker exec <container> id`
- [ ] Check capabilities: `docker inspect | grep -i cap`
- [ ] Review resource usage trends
- [ ] Update base images (Alpine, JRE, nginx, postgres)
- [ ] Test backup/restore procedures
- [ ] Audit secrets in images: `trivy image --scanners secret`
- [ ] Document findings in security log

### Quarterly Security Review

**Tasks:**
- Update `infra/trivy-config.yaml` with new CVE exceptions
- Review and remove expired suppressions
- Update this document with new threats/mitigations
- Conduct penetration testing
- Review incident response procedures
- Update security training materials

---

## Incident Response

### Critical Vulnerability Detected

**Step 1: Assess Impact**
```bash
# Check affected images
trivy image --severity CRITICAL ai-orchestrator:latest

# Identify vulnerable packages
trivy image --format json ai-orchestrator:latest | \
  jq '.Results[].Vulnerabilities[] | select(.Severity=="CRITICAL")'
```

**Step 2: Immediate Mitigation**
- **Option A**: Suppress if acceptable risk
  ```yaml
  # Add to trivy-config.yaml
  vulnerability:
    ignore:
      CVE-XXXX-XXXXX:
        - package-name: "vulnerable-lib"
          reason: "Network isolated, awaiting patch"
          expiry: "2025-03-31"
  ```
- **Option B**: Take containers offline
  ```bash
  docker-compose -f infra/docker-compose.ai.yml stop ai-orchestrator
  ```

**Step 3: Remediation**
```bash
# Update base image version in Dockerfile
sed -i 's/eclipse-temurin:17-jre-alpine/eclipse-temurin:17.0.X-jre-alpine/' \
  ai-orchestrator/Dockerfile

# Rebuild
docker-compose -f infra/docker-compose.ai.yml build ai-orchestrator

# Rescan
trivy image ai-orchestrator:latest

# Deploy
docker-compose -f infra/docker-compose.ai.yml up -d ai-orchestrator
```

**Step 4: Verification**
```bash
# Confirm vulnerability resolved
trivy image --severity CRITICAL ai-orchestrator:latest
# Expected: No vulnerabilities found

# Check service health
curl http://localhost:8088/actuator/health
```

**Step 5: Documentation**
- Update `CHANGELOG.md`
- Notify stakeholders via Slack/email
- Document in incident log
- Schedule post-mortem

### Container Compromise

**Indicators:**
- Unexpected network connections
- High CPU/memory usage
- Unknown processes running
- File modifications in read-only areas

**Response:**
```bash
# 1. Isolate container
docker network disconnect bridge <container-name>

# 2. Capture forensics
docker logs <container-name> > incident-logs.txt
docker inspect <container-name> > incident-inspect.json
docker export <container-name> -o incident-filesystem.tar

# 3. Stop container
docker stop <container-name>

# 4. Scan for malware
trivy image --scanners vuln,secret,misconfig <image-name>

# 5. Restore from clean image
docker-compose -f infra/docker-compose.ai.yml up -d --force-recreate
```

### Secrets Exposed

**If secrets detected in image:**
```bash
# 1. Identify exposed secrets
trivy image --scanners secret ai-orchestrator:latest

# 2. Rotate compromised secrets immediately
# (See VAULT_SETUP.md for rotation procedures)

# 3. Remove secrets from image
# - Update Dockerfile to use environment variables
# - Use Vault for secret injection
# - Rebuild image

# 4. Invalidate old image
docker rmi ai-orchestrator:compromised-tag

# 5. Notify security team
```

---

## Best Practices

### DO ✅

**Development:**
- Run Trivy scans locally before committing
- Test container security in CI/CD pipeline
- Use multi-stage builds to minimize image size
- Keep base images updated weekly

**Configuration:**
- Document all CVE suppressions with expiry dates
- Use read-only filesystems where possible
- Drop ALL capabilities by default
- Set resource limits on all services
- Enable user namespace remapping in production

**Operations:**
- Review GitHub Security alerts weekly
- Rotate secrets quarterly
- Monitor resource usage daily
- Conduct security audits monthly
- Test incident response procedures quarterly

**Image Management:**
- Pin base image versions (avoid `:latest`)
- Use minimal base images (Alpine)
- Remove package managers after installation
- Run containers as non-root users
- Scan images before deployment

### DON'T ❌

**Security:**
- Suppress CRITICAL CVEs without CTO approval
- Run containers as root user
- Use `:latest` tags in production
- Ignore HIGH severity vulnerabilities for >30 days
- Add unnecessary capabilities
- Disable security features for convenience

**Operations:**
- Share credentials between environments
- Commit secrets to git
- Skip Trivy scans to speed up deployment
- Deploy without testing in staging
- Modify production containers directly

**Configuration:**
- Use `privileged: true` mode
- Mount sensitive host paths
- Allow unlimited resource usage
- Expose unnecessary ports
- Use host networking mode

---

## References

### Internal Documentation
- [DEPLOYMENT.md](../DEPLOYMENT.md) - Deployment procedures
- [VAULT_SETUP.md](VAULT_SETUP.md) - Secrets management
- [SECURITY.md](SECURITY.md) - Security policy

### External Resources
- [Trivy Documentation](https://aquasecurity.github.io/trivy/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [NIST 800-190: Container Security](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-190.pdf)
- [CIS Docker Benchmark](https://www.cisecurity.org/benchmark/docker)
- [OWASP Container Security](https://owasp.org/www-community/vulnerabilities/Container_Security)

### Tools
- [Trivy](https://github.com/aquasecurity/trivy) - Vulnerability scanner
- [Docker Bench Security](https://github.com/docker/docker-bench-security) - Security audit tool
- [Anchore](https://anchore.com/) - Alternative container scanner
- [Clair](https://github.com/quay/clair) - Vulnerability static analysis

---

**Document Version:** 1.0  
**Last Review:** 2026-02-21  
**Next Review:** 2026-05-21  
**Owner:** Security Team  
**Reviewers:** DevOps, Engineering
