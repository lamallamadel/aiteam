# Security Deployment Checklist

**Last Updated:** 2026-02-21  
**Owner:** Security Team  
**Version:** 1.0

---

## Overview

This checklist must be completed before deploying Atlasia AI Orchestrator to production. All items marked as **CRITICAL** must be completed. Items marked as **RECOMMENDED** should be completed if feasible.

---

## Pre-Deployment Checklist

### 1. Secrets Management ✅

#### HashiCorp Vault Configuration

- [ ] **CRITICAL**: Vault installed and running
- [ ] **CRITICAL**: Vault initialized with Shamir secret sharing (3 of 5 keys minimum)
- [ ] **CRITICAL**: Unseal keys distributed to separate custodians
- [ ] **CRITICAL**: Vault unsealed and operational
- [ ] **CRITICAL**: All application secrets stored in Vault (`secret/data/atlasia/*`)
- [ ] **CRITICAL**: AppRole authentication configured (production only)
- [ ] **CRITICAL**: Vault ACL policies restrict access to `secret/data/atlasia/*` path
- [ ] **CRITICAL**: Vault audit logging enabled (`vault audit enable file`)
- [ ] **RECOMMENDED**: Auto-unseal with cloud KMS (AWS/GCP/Azure)
- [ ] **RECOMMENDED**: Vault HA cluster (3+ nodes)
- [ ] **RECOMMENDED**: Vault TLS enabled with valid certificates

**Verification:**
```bash
# Check Vault status
vault status

# List secrets
vault kv list secret/atlasia

# Verify audit log
cat /var/log/vault/audit.log | tail -n 20

# Test secret retrieval
vault kv get secret/atlasia/jwt-secret
```

---

### 2. TLS/SSL Encryption 🔒

#### Application Server TLS

- [ ] **CRITICAL**: TLS 1.3 enabled in `application.yml`
- [ ] **CRITICAL**: Valid SSL certificate obtained (CA-signed, not self-signed)
- [ ] **CRITICAL**: SSL keystore created in PKCS12 format
- [ ] **CRITICAL**: Keystore password stored in Vault
- [ ] **CRITICAL**: Keystore path configured in application
- [ ] **CRITICAL**: HTTPS enforced (HTTP redirects to HTTPS)
- [ ] **CRITICAL**: Strong cipher suites configured (TLS_AES_256_GCM_SHA384, TLS_AES_128_GCM_SHA256)
- [ ] **RECOMMENDED**: Certificate auto-renewal configured (certbot)
- [ ] **RECOMMENDED**: Certificate expiry monitoring enabled

**Verification:**
```bash
# Test TLS version
openssl s_client -connect localhost:8080 -tls1_3

# Check certificate validity
openssl s_client -connect localhost:8080 -showcerts

# Verify cipher suites
nmap --script ssl-enum-ciphers -p 8080 localhost
```

#### Database TLS

- [ ] **CRITICAL**: PostgreSQL SSL enabled (`ssl=on`)
- [ ] **CRITICAL**: PostgreSQL TLS 1.3 minimum (`ssl_min_protocol_version=TLSv1.3`)
- [ ] **CRITICAL**: Strong ciphers configured
- [ ] **CRITICAL**: JDBC connection string includes `ssl=true&sslmode=require`
- [ ] **RECOMMENDED**: Client certificate authentication (`sslmode=verify-full`)

**Verification:**
```bash
# Check PostgreSQL SSL status
docker-compose exec ai-db psql -U aiteam -d ai -c "SHOW ssl;"

# Test SSL connection
psql "postgresql://localhost:5432/ai?ssl=true&sslmode=require" -c "SELECT version();"
```

---

### 3. Secret Rotation 🔄

#### JWT Signing Key

- [ ] **CRITICAL**: JWT secret key generated (256-bit minimum)
- [ ] **CRITICAL**: JWT secret stored in Vault (`secret/atlasia/jwt-secret`)
- [ ] **CRITICAL**: JWT secret rotation scheduled (monthly)
- [ ] **RECOMMENDED**: Rotation automation tested

**Verification:**
```bash
# Check JWT secret exists
vault kv get secret/atlasia/jwt-secret

# Verify secret length (should be 256+ bits)
vault kv get -field=value secret/atlasia/jwt-secret | base64 -d | wc -c
```

#### Database Password

- [ ] **CRITICAL**: Database password generated (20+ characters, strong complexity)
- [ ] **CRITICAL**: Database password stored in Vault (`secret/atlasia/db-password`)
- [ ] **CRITICAL**: Database password rotation scheduled (quarterly)
- [ ] **RECOMMENDED**: Password complexity validated (uppercase, lowercase, digits, symbols)

**Verification:**
```bash
# Check database password
vault kv get secret/atlasia/db-password

# Test database connection with Vault password
DB_PASS=$(vault kv get -field=value secret/atlasia/db-password)
psql "postgresql://aiteam:${DB_PASS}@localhost:5432/ai" -c "SELECT 1;"
```

#### OAuth2 Credentials

- [ ] **CRITICAL**: OAuth2 client secrets stored in Vault
  - `secret/atlasia/oauth2-github-client-secret`
  - `secret/atlasia/oauth2-google-client-secret`
  - `secret/atlasia/oauth2-gitlab-client-secret`
- [ ] **CRITICAL**: OAuth2 secret rotation scheduled (quarterly)
- [ ] **CRITICAL**: OAuth2 secrets updated in provider consoles (GitHub, Google, GitLab)

**Verification:**
```bash
# List all OAuth2 secrets
vault kv list secret/atlasia | grep oauth2
```

#### Encryption Key

- [ ] **CRITICAL**: Encryption key generated (256-bit AES)
- [ ] **CRITICAL**: Encryption key stored in Vault (`secret/atlasia/encryption-key`)
- [ ] **CRITICAL**: Encryption key rotation scheduled (quarterly)
- [ ] **RECOMMENDED**: Old encryption keys retained for decryption (90 days)

**Verification:**
```bash
# Check encryption key
vault kv get secret/atlasia/encryption-key
```

---

### 4. Vulnerability Scanning 🛡️

#### Trivy Container Scanning

- [ ] **CRITICAL**: Trivy installed and configured
- [ ] **CRITICAL**: Trivy configuration file exists (`infra/trivy-config.yaml`)
- [ ] **CRITICAL**: Latest container images scanned
- [ ] **CRITICAL**: No CRITICAL vulnerabilities in images
- [ ] **CRITICAL**: HIGH vulnerabilities reviewed and accepted/mitigated
- [ ] **CRITICAL**: Trivy scan passing in CI/CD pipeline
- [ ] **RECOMMENDED**: Daily scheduled scans enabled
- [ ] **RECOMMENDED**: SARIF reports uploaded to GitHub Security

**Verification:**
```bash
# Scan backend image
trivy image --config infra/trivy-config.yaml ai-orchestrator:latest

# Scan frontend image
trivy image --config infra/trivy-config.yaml frontend:latest

# Check for CRITICAL vulnerabilities
trivy image --severity CRITICAL ai-orchestrator:latest

# Generate report
trivy image --format json --output trivy-report.json ai-orchestrator:latest
```

#### Dependency Scanning

- [ ] **CRITICAL**: OWASP Dependency-Check passing (`mvn dependency-check:check`)
- [ ] **CRITICAL**: NPM audit passing (`npm audit`)
- [ ] **CRITICAL**: No HIGH/CRITICAL vulnerabilities in dependencies
- [ ] **CRITICAL**: Dependency suppressions documented in `dependency-check-suppressions.xml`
- [ ] **RECOMMENDED**: SBOM generated (`mvn cyclonedx:makeAggregateBom`)

**Verification:**
```bash
# Backend dependency check
cd ai-orchestrator && mvn dependency-check:check

# Frontend dependency audit
cd frontend && npm audit --audit-level=moderate

# Generate SBOM
cd ai-orchestrator && mvn cyclonedx:makeAggregateBom
```

---

### 5. Container Security 🐳

#### Non-Root Users

- [ ] **CRITICAL**: All containers run as non-root users
  - Backend: UID 1000 (appuser)
  - Frontend: UID 101 (nginx)
  - Database: UID 999 (postgres)
  - Vault: UID 100 (vault)
- [ ] **CRITICAL**: Package managers removed from images
- [ ] **CRITICAL**: Minimal base images used (Alpine)

**Verification:**
```bash
# Check container users
docker-compose -f infra/docker-compose.ai.yml exec ai-orchestrator id
docker-compose -f infra/docker-compose.ai.yml exec ai-db id
docker-compose -f infra/docker-compose.ai.yml exec vault id
```

#### Runtime Hardening

- [ ] **CRITICAL**: Read-only root filesystem enabled
- [ ] **CRITICAL**: Capabilities dropped (CAP_DROP ALL)
- [ ] **CRITICAL**: no-new-privileges security option set
- [ ] **CRITICAL**: Resource limits configured (CPU: 2 cores, Memory: 2GB)
- [ ] **RECOMMENDED**: User namespace remapping enabled (production only)
- [ ] **RECOMMENDED**: Seccomp profile applied
- [ ] **RECOMMENDED**: AppArmor/SELinux enforced

**Verification:**
```bash
# Check read-only filesystem
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.ReadonlyRootfs'

# Check dropped capabilities
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.CapDrop'

# Check security options
docker inspect $(docker ps -qf "name=ai-orchestrator") | jq '.[0].HostConfig.SecurityOpt'

# Check resource limits
docker stats --no-stream
```

#### Image Security

- [ ] **CRITICAL**: Images built from trusted sources only
- [ ] **CRITICAL**: Base image versions pinned (no `:latest`)
- [ ] **CRITICAL**: No secrets in images (verified with Trivy)
- [ ] **RECOMMENDED**: Images signed with Docker Content Trust
- [ ] **RECOMMENDED**: Image digests used instead of tags

**Verification:**
```bash
# Scan for secrets in images
trivy image --scanners secret ai-orchestrator:latest

# Check base image versions in Dockerfiles
grep "FROM" ai-orchestrator/Dockerfile frontend/Dockerfile
```

---

### 6. Authentication & Authorization 🔐

#### JWT Configuration

- [ ] **CRITICAL**: JWT secret key configured (256-bit minimum)
- [ ] **CRITICAL**: JWT access token TTL configured (15 minutes maximum)
- [ ] **CRITICAL**: JWT refresh token TTL configured (7 days maximum)
- [ ] **CRITICAL**: JWT issuer configured (`atlasia-ai-orchestrator`)
- [ ] **CRITICAL**: HMAC-SHA512 algorithm used for signing
- [ ] **RECOMMENDED**: JWT tokens stored in httpOnly cookies (not localStorage)

**Verification:**
```bash
# Check JWT configuration in application.yml
grep -A 5 "jwt:" ai-orchestrator/src/main/resources/application.yml

# Test JWT token generation
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' | jq .expiresIn
```

#### RBAC/ABAC

- [ ] **CRITICAL**: Roles configured (ADMIN, USER)
- [ ] **CRITICAL**: Permissions configured (runs:read, runs:write, users:admin)
- [ ] **CRITICAL**: Default user has minimal permissions
- [ ] **CRITICAL**: Admin role assigned to authorized users only
- [ ] **CRITICAL**: Method-level security annotations verified (@PreAuthorize)
- [ ] **RECOMMENDED**: Custom permissions for fine-grained access control

**Verification:**
```bash
# List roles
psql -U aiteam -d ai -c "SELECT * FROM roles;"

# List permissions
psql -U aiteam -d ai -c "SELECT * FROM permissions;"

# Check role assignments
psql -U aiteam -d ai -c "SELECT u.username, r.name FROM users u JOIN user_roles ur ON u.id=ur.user_id JOIN roles r ON ur.role_id=r.id;"
```

#### Brute Force Protection

- [ ] **CRITICAL**: Brute force protection enabled (5 failed attempts)
- [ ] **CRITICAL**: Account lockout duration configured (15 minutes)
- [ ] **CRITICAL**: Rate limiting enabled on login endpoint

**Verification:**
```bash
# Test brute force protection (should lock after 5 attempts)
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}' -w "%{http_code}\n"
done
```

---

### 7. Database Security 🗄️

#### Encryption

- [ ] **CRITICAL**: Column-level encryption enabled (AES-256-GCM)
- [ ] **CRITICAL**: Encrypted columns: OAuth tokens, MFA secrets
- [ ] **CRITICAL**: Passwords hashed with BCrypt (strength 12)
- [ ] **CRITICAL**: Refresh tokens hashed with SHA-256
- [ ] **CRITICAL**: Encryption key stored in Vault
- [ ] **RECOMMENDED**: Database volume encrypted (LUKS or cloud encryption)
- [ ] **RECOMMENDED**: Database backups encrypted

**Verification:**
```bash
# Check encrypted columns
psql -U aiteam -d ai -c "\d+ oauth2_accounts" | grep encrypted

# Verify column encryption service
grep -r "ColumnEncryptionService" ai-orchestrator/src/main/java/
```

#### Access Control

- [ ] **CRITICAL**: Database password stored in Vault
- [ ] **CRITICAL**: Database user has minimal privileges (no SUPERUSER)
- [ ] **CRITICAL**: Database connections use TLS
- [ ] **CRITICAL**: Database network isolated (not exposed to internet)
- [ ] **RECOMMENDED**: Separate database users per service
- [ ] **RECOMMENDED**: pg_audit enabled for audit logging

**Verification:**
```bash
# Check database user privileges
psql -U aiteam -d ai -c "\du aiteam"

# Test TLS connection
psql "postgresql://aiteam@localhost:5432/ai?ssl=true&sslmode=require" -c "SELECT 1;"
```

#### Backups

- [ ] **CRITICAL**: Automated daily backups configured
- [ ] **CRITICAL**: Backup retention policy defined (90 days minimum)
- [ ] **CRITICAL**: Backup restore tested successfully
- [ ] **RECOMMENDED**: Backups encrypted at rest
- [ ] **RECOMMENDED**: Backups stored offsite/off-host

**Verification:**
```bash
# Create test backup
docker-compose -f docker-compose.prod.yml exec ai-db pg_dump -U aiteam aiteam | gzip > backup_test.sql.gz

# Verify backup size
ls -lh backup_test.sql.gz

# Test restore (in test environment)
zcat backup_test.sql.gz | docker-compose exec -T ai-db psql -U aiteam -d aiteam_test
```

---

### 8. Logging & Monitoring 📊

#### Audit Logging

- [ ] **CRITICAL**: Authentication events logged (login, logout, token refresh)
- [ ] **CRITICAL**: Authorization failures logged
- [ ] **CRITICAL**: Workflow mutations logged (graft, prune, flag)
- [ ] **CRITICAL**: File upload/download events logged
- [ ] **CRITICAL**: Admin actions logged
- [ ] **CRITICAL**: Audit logs are immutable (append-only)
- [ ] **RECOMMENDED**: Audit logs sent to SIEM (Security Information and Event Management)
- [ ] **RECOMMENDED**: Real-time alerting on suspicious activity

**Verification:**
```bash
# Check authentication audit logs
psql -U aiteam -d ai -c "SELECT * FROM auth_audit_logs ORDER BY created_at DESC LIMIT 10;"

# Check collaboration events
psql -U aiteam -d ai -c "SELECT * FROM collaboration_events ORDER BY timestamp DESC LIMIT 10;"
```

#### Security Monitoring

- [ ] **CRITICAL**: Application health checks enabled
- [ ] **CRITICAL**: Container resource monitoring enabled
- [ ] **CRITICAL**: Error rates monitored
- [ ] **RECOMMENDED**: Prometheus metrics exposed (`/actuator/prometheus`)
- [ ] **RECOMMENDED**: Grafana dashboards configured
- [ ] **RECOMMENDED**: Alerting rules configured (Prometheus/Grafana)

**Verification:**
```bash
# Test health endpoint
curl http://localhost:8080/actuator/health | jq .

# Test Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep orchestrator_
```

#### Log Security

- [ ] **CRITICAL**: No secrets in logs (verified)
- [ ] **CRITICAL**: No PII in logs
- [ ] **CRITICAL**: Log retention policy defined (90 days minimum)
- [ ] **CRITICAL**: Log rotation configured
- [ ] **RECOMMENDED**: Logs encrypted in transit (TLS to log aggregator)
- [ ] **RECOMMENDED**: Logs encrypted at rest

**Verification:**
```bash
# Scan logs for secrets
grep -rE "(password|secret|key|token).*=.*[A-Za-z0-9]{20,}" /var/log/ai-orchestrator/

# Check log rotation
ls -lh /var/log/ai-orchestrator/
```

---

### 9. Network Security 🌐

#### Firewall Configuration

- [ ] **CRITICAL**: Firewall enabled (UFW, iptables, cloud security groups)
- [ ] **CRITICAL**: Only required ports open (80, 443, 22)
- [ ] **CRITICAL**: SSH limited to specific IPs (not 0.0.0.0/0)
- [ ] **CRITICAL**: Database port not exposed to internet (5432 internal only)
- [ ] **CRITICAL**: Vault port not exposed to internet (8200 internal only)
- [ ] **RECOMMENDED**: DDoS protection enabled (Cloudflare, AWS Shield)
- [ ] **RECOMMENDED**: WAF configured (Web Application Firewall)

**Verification:**
```bash
# Check firewall status
sudo ufw status verbose

# Test external access to database (should fail)
nmap -p 5432 your-public-ip

# Test external access to Vault (should fail)
nmap -p 8200 your-public-ip
```

#### Rate Limiting

- [ ] **CRITICAL**: Rate limiting enabled on API endpoints
- [ ] **CRITICAL**: Login rate limiting configured
- [ ] **CRITICAL**: File download rate limiting configured (10/min per user)
- [ ] **CRITICAL**: WebSocket connection rate limiting configured
- [ ] **RECOMMENDED**: Global rate limiting (requests per IP)

**Verification:**
```bash
# Test rate limiting on downloads
for i in {1..12}; do
  curl -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/runs/{runId}/artifacts/{artifactId}/download \
    -w "%{http_code}\n" -o /dev/null
done
# Should return 429 after 10 requests
```

---

### 10. Compliance & Documentation 📝

#### Security Documentation

- [ ] **CRITICAL**: THREAT_MODEL.md reviewed and up-to-date
- [ ] **CRITICAL**: SECURITY_CHECKLIST.md (this document) completed
- [ ] **CRITICAL**: SECURITY.md exists with vulnerability reporting process
- [ ] **CRITICAL**: DEPLOYMENT.md includes security hardening steps
- [ ] **CRITICAL**: Incident response plan documented
- [ ] **RECOMMENDED**: Security training completed by all team members
- [ ] **RECOMMENDED**: Penetration testing completed

**Verification:**
```bash
# Check documentation exists
ls -l docs/THREAT_MODEL.md
ls -l docs/SECURITY_CHECKLIST.md
ls -l SECURITY.md
ls -l DEPLOYMENT.md
```

#### Access Control

- [ ] **CRITICAL**: Principle of least privilege applied
- [ ] **CRITICAL**: Admin access limited to authorized personnel
- [ ] **CRITICAL**: Vault unseal keys distributed to separate custodians
- [ ] **CRITICAL**: Production database access restricted
- [ ] **CRITICAL**: SSH key-based authentication enforced (no passwords)
- [ ] **RECOMMENDED**: Multi-factor authentication (MFA) enabled for admins
- [ ] **RECOMMENDED**: Access review conducted quarterly

**Verification:**
```bash
# Check SSH configuration
grep "PasswordAuthentication" /etc/ssh/sshd_config
# Should be: PasswordAuthentication no

# List users with sudo access
grep -Po '^sudo.+:\K.*$' /etc/group
```

---

## Post-Deployment Verification

### 1. Security Scan ✅

- [ ] Run full security scan after deployment
- [ ] Verify no CRITICAL vulnerabilities
- [ ] Review and accept/mitigate HIGH vulnerabilities
- [ ] Document all accepted risks

```bash
# Full security scan
./scripts/security-check.sh

# Trivy scan
trivy image ai-orchestrator:production
trivy image frontend:production

# OWASP Dependency Check
cd ai-orchestrator && mvn dependency-check:check

# NPM Audit
cd frontend && npm audit
```

### 2. Penetration Testing 🎯

- [ ] **RECOMMENDED**: External penetration testing completed
- [ ] **RECOMMENDED**: Vulnerabilities identified and remediated
- [ ] **RECOMMENDED**: Pentest report reviewed by security team

### 3. Security Monitoring 📡

- [ ] Verify logs are flowing to central logging system
- [ ] Verify metrics are being collected
- [ ] Verify alerts are configured and firing correctly
- [ ] Test incident response procedures

### 4. Backup & Recovery 💾

- [ ] Verify daily backups are running
- [ ] Test backup restoration in non-production environment
- [ ] Verify backup encryption (if enabled)
- [ ] Document recovery time objective (RTO) and recovery point objective (RPO)

---

## Ongoing Maintenance

### Daily

- [ ] Monitor application health and error rates
- [ ] Review security alerts
- [ ] Check disk space and resource usage

### Weekly

- [ ] Review audit logs for suspicious activity
- [ ] Review Trivy scan results (GitHub Security tab)
- [ ] Check for dependency updates

### Monthly

- [ ] Rotate JWT signing key
- [ ] Review and update Trivy suppression rules
- [ ] Review user access and permissions
- [ ] Test backup restoration

### Quarterly

- [ ] Rotate database password
- [ ] Rotate OAuth2 client secrets
- [ ] Rotate encryption key
- [ ] Conduct security audit (review this checklist)
- [ ] Update security documentation
- [ ] Review and remove expired Vault secrets
- [ ] Penetration testing (if budget allows)

### Annually

- [ ] External security audit
- [ ] Compliance review (SOC 2, ISO 27001, etc.)
- [ ] Disaster recovery drill
- [ ] Security training for all team members

---

## Incident Response

### If Security Incident Detected

1. **Contain**: Isolate affected systems
2. **Assess**: Determine scope and impact
3. **Remediate**: Fix vulnerability, rotate compromised secrets
4. **Notify**: Inform stakeholders per disclosure policy
5. **Document**: Update incident log and lessons learned
6. **Review**: Update threat model and security controls

**Incident Contact**: security@atlasia.ai

---

## Sign-Off

This checklist must be reviewed and signed off by the following roles before production deployment:

| Role | Name | Signature | Date |
|------|------|-----------|------|
| **Security Lead** | | | |
| **DevOps Lead** | | | |
| **Engineering Lead** | | | |
| **CTO/CISO** | | | |

---

## References

- [THREAT_MODEL.md](THREAT_MODEL.md)
- [CONTAINER_SECURITY.md](CONTAINER_SECURITY.md)
- [VAULT_SETUP.md](VAULT_SETUP.md)
- [JWT_AUTHENTICATION.md](JWT_AUTHENTICATION.md)
- [DEPLOYMENT.md](../DEPLOYMENT.md)
- [SECURITY.md](SECURITY.md)

---

**Document Version:** 1.0  
**Last Review:** 2026-02-21  
**Next Review:** 2026-05-21
