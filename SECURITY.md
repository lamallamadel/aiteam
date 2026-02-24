# Security Policy

**Last Updated:** 2026-02-21  
**Version:** 1.0

---

## Reporting Security Vulnerabilities

The Atlasia team takes security vulnerabilities seriously. We appreciate your efforts to responsibly disclose your findings.

### How to Report a Vulnerability

**Email:** security@atlasia.ai

**Please include:**
- Description of the vulnerability
- Steps to reproduce the issue
- Potential impact
- Suggested mitigation (if applicable)
- Your contact information (for follow-up)

**What to expect:**
- **Initial Response**: Within 48 hours
- **Status Updates**: Every 7 days until resolution
- **Resolution Timeline**: Target 90 days from initial report

### Our Commitment

- **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours.
- **Communication**: We will keep you informed of our progress throughout the investigation.
- **Credit**: With your permission, we will publicly credit you for the discovery after the issue is resolved.
- **No Legal Action**: We will not pursue legal action against researchers who follow this responsible disclosure policy.

---

## Coordinated Disclosure Policy

We follow a **90-day coordinated disclosure policy**:

1. **Day 0**: Vulnerability reported to security@atlasia.ai
2. **Day 0-7**: Initial triage and validation
3. **Day 7-14**: Develop and test fix
4. **Day 14-30**: Deploy fix to production (critical vulnerabilities prioritized)
5. **Day 30-90**: Coordinate disclosure timeline with researcher
6. **Day 90**: Public disclosure (security advisory published)

**Early Public Disclosure:**
- If the vulnerability is being actively exploited in the wild, we may disclose earlier
- If the vulnerability is independently discovered and publicly disclosed, we will expedite our fix and disclosure

**Extended Timeline:**
- For complex vulnerabilities requiring extensive code changes, we may request a longer timeline
- Extensions require mutual agreement with the researcher

---

## Scope

### In Scope

The following components are in scope for security vulnerability reports:

- **Backend Application** (`ai-orchestrator`)
  - Spring Boot REST APIs
  - Authentication & authorization (JWT, OAuth2)
  - Workflow engine
  - WebSocket collaboration system
  - File upload/artifact storage
  - Database interactions

- **Frontend Application** (`frontend`)
  - Angular web application
  - WebSocket client
  - Authentication flows

- **Infrastructure**
  - Docker containers
  - PostgreSQL database
  - HashiCorp Vault integration

- **Dependencies**
  - Third-party libraries (Maven, NPM)
  - Base Docker images

### Out of Scope

The following are **not** in scope:

- **Social Engineering**: Phishing, social manipulation, etc.
- **Physical Attacks**: Physical access to servers or devices
- **Denial of Service (DoS/DDoS)**: Resource exhaustion attacks
- **Spam**: Email, comment, or form spam
- **Third-Party Services**: Vulnerabilities in GitHub, LLM providers, OAuth providers (report directly to them)
- **Self-XSS**: Issues requiring user to paste malicious code into browser console
- **Already Known Issues**: Vulnerabilities already publicly disclosed or in our issue tracker
- **Outdated Browsers/Software**: Issues only affecting unsupported browsers or operating systems

---

## Vulnerability Severity Guidelines

We use the **Common Vulnerability Scoring System (CVSS) v3.1** to assess vulnerability severity:

### Critical (CVSS 9.0 - 10.0)

- Remote code execution (RCE)
- Authentication bypass affecting all users
- SQL injection with data exfiltration
- Privilege escalation to admin
- Exposure of all secrets/credentials

**Response Time:** Fix within 7 days

### High (CVSS 7.0 - 8.9)

- Authentication bypass affecting specific users
- Authorization bypass (horizontal/vertical privilege escalation)
- Sensitive data exposure (PII, tokens, passwords)
- Server-side request forgery (SSRF)
- XML external entity (XXE) injection
- Stored cross-site scripting (XSS)

**Response Time:** Fix within 30 days

### Medium (CVSS 4.0 - 6.9)

- Reflected cross-site scripting (XSS)
- Cross-site request forgery (CSRF)
- Information disclosure (non-sensitive data)
- Denial of service (application level)
- Insecure direct object references (IDOR)

**Response Time:** Fix within 90 days

### Low (CVSS 0.1 - 3.9)

- Security misconfigurations
- Missing security headers
- Verbose error messages
- Information disclosure (public data)

**Response Time:** Fix in next release cycle

---

## Security Best Practices for Researchers

### Do's ✅

- **Report Responsibly**: Report vulnerabilities privately to security@atlasia.ai
- **Provide Details**: Include clear reproduction steps and impact assessment
- **Allow Time**: Give us reasonable time to fix the issue before public disclosure
- **Test Safely**: Only test against your own accounts or test environments
- **Be Professional**: Communicate respectfully and professionally

### Don'ts ❌

- **Don't Access User Data**: Do not access, modify, or delete user data
- **Don't Disrupt Service**: Do not perform attacks that impact availability
- **Don't Publicly Disclose**: Do not publicly disclose vulnerabilities before coordinated disclosure
- **Don't Test in Production**: Do not perform destructive testing in production environments
- **Don't Social Engineer**: Do not attempt to phish employees or users
- **Don't Demand Payment**: Vulnerability disclosure is not a bug bounty program (though we may offer rewards at our discretion)

---

## What Happens After You Report

1. **Acknowledgment** (Within 48 hours)
   - We confirm receipt of your report
   - We assign a tracking ID for your reference

2. **Validation** (Within 7 days)
   - We validate the vulnerability
   - We assess severity and impact
   - We determine if it's a duplicate or already known

3. **Triage** (Within 14 days)
   - We prioritize the fix based on severity
   - We develop a remediation plan
   - We estimate a fix timeline

4. **Fix Development** (7-90 days depending on severity)
   - We develop and test the fix
   - We may request additional information from you
   - We provide status updates every 7 days

5. **Deployment** (After fix is ready)
   - We deploy the fix to production
   - We verify the fix resolves the issue
   - We notify you when the fix is deployed

6. **Disclosure** (After deployment + up to 90 days)
   - We coordinate disclosure timing with you
   - We publish a security advisory (if applicable)
   - We credit you publicly (with your permission)

---

## Security Features

Atlasia implements multiple layers of security controls:

### Authentication & Authorization

- **JWT-based Authentication**: Short-lived access tokens (15 minutes)
- **Refresh Token Rotation**: Single-use refresh tokens prevent replay attacks
- **OAuth2 Integration**: GitHub, Google, GitLab authentication
- **Role-Based Access Control (RBAC)**: Granular permissions (runs:read, runs:write, users:admin)
- **Brute Force Protection**: Account lockout after 5 failed login attempts
- **Multi-Factor Authentication**: MFA support (future)

### Data Protection

- **TLS 1.3**: Encryption in transit for all communications
- **Column-Level Encryption**: AES-256-GCM for sensitive data (OAuth tokens, MFA secrets)
- **Password Hashing**: BCrypt (strength 12) for user passwords
- **Token Hashing**: SHA-256 for refresh tokens
- **Database Encryption**: Encrypted database volumes (LUKS or cloud-native)

### Secrets Management

- **HashiCorp Vault**: Centralized secrets storage
- **Automated Rotation**: Monthly JWT key rotation, quarterly OAuth2/encryption key rotation
- **No Hardcoded Secrets**: All secrets stored in Vault, not in code or config
- **Audit Logging**: All Vault access logged

### Container Security

- **Non-Root Users**: All containers run as unprivileged users
- **Read-Only Filesystem**: Prevents runtime tampering
- **Dropped Capabilities**: Minimal Linux capabilities (CAP_DROP ALL)
- **User Namespace Remapping**: Container isolation from host
- **Vulnerability Scanning**: Trivy scans on every build

### Input Validation & Sanitization

- **Schema Validation**: JSON Schema validation for workflow configurations
- **Input Sanitization**: All user input sanitized before processing
- **Parameterized Queries**: JPA/Hibernate prevents SQL injection
- **MIME Type Detection**: Content-based file type validation
- **Filename Sanitization**: Prevents path traversal attacks

### Rate Limiting & DoS Protection

- **Login Rate Limiting**: Brute force protection
- **File Download Rate Limiting**: 10 requests per minute per user
- **WebSocket Connection Limits**: Prevents connection flooding
- **Resource Limits**: CPU and memory limits on all containers
- **Circuit Breakers**: Graceful degradation for external services

### Audit Logging

- **Authentication Events**: All logins, logouts, token refreshes logged
- **Authorization Failures**: Unauthorized access attempts logged
- **Workflow Mutations**: Graft, prune, flag operations logged
- **Collaboration Events**: Real-time collaboration events stored immutably
- **Admin Actions**: All administrative actions logged with user attribution

---

## Security Compliance

Atlasia is designed with security best practices in mind:

- **OWASP Top 10**: Mitigations for all OWASP Top 10 vulnerabilities
- **CIS Docker Benchmark**: Container hardening follows CIS guidelines
- **NIST SP 800-190**: Container security per NIST guidelines
- **PCI DSS Considerations**: Encryption, access control, audit logging
- **GDPR Considerations**: Data minimization, encryption, audit trails

---

## Security Advisories

Published security advisories are available at:
- **GitHub Security Advisories**: https://github.com/your-org/aiteam/security/advisories
- **Security Mailing List**: security-announce@atlasia.ai (subscribe by emailing security@atlasia.ai)

---

## Past Security Advisories

| Advisory ID | Title | Severity | Published | Fixed In |
|-------------|-------|----------|-----------|----------|
| _None yet_ | | | | |

---

## Hall of Fame

We recognize and thank the following security researchers for their responsible disclosure:

| Researcher | Vulnerability | Date | Severity |
|------------|---------------|------|----------|
| _None yet_ | | | |

---

## Security Resources

### For Users

- **Two-Factor Authentication**: Enable MFA when available (future feature)
- **Strong Passwords**: Use unique, complex passwords (12+ characters)
- **Keep Software Updated**: Use latest browser versions
- **Beware of Phishing**: Verify URLs before entering credentials
- **Report Suspicious Activity**: Contact security@atlasia.ai

### For Developers

- **Threat Model**: See `docs/THREAT_MODEL.md`
- **Security Checklist**: See `docs/SECURITY_CHECKLIST.md`
- **Container Security**: See `docs/CONTAINER_SECURITY.md`
- **Vault Setup**: See `docs/VAULT_SETUP.md`
- **JWT Authentication**: See `docs/JWT_AUTHENTICATION.md`
- **File Upload Security**: See `docs/FILE_UPLOAD_SECURITY.md`
- **Collaboration Security**: See `docs/COLLABORATION.md`

### For Operators

- **Deployment Security**: See `DEPLOYMENT.md` (Security Hardening section)
- **Incident Response**: See `docs/THREAT_MODEL.md` (Attack Scenarios)
- **Monitoring**: See `docs/WEBSOCKET_HEALTH_MONITORING.md`

---

## Contact

- **Security Team Email**: security@atlasia.ai
- **Security Announcements**: security-announce@atlasia.ai
- **General Support**: support@atlasia.ai
- **Website**: https://atlasia.ai

---

## Updates to This Policy

This security policy may be updated from time to time. The latest version is always available at:
- https://github.com/your-org/aiteam/blob/main/SECURITY.md

**Version History:**

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-21 | Initial security policy |

---

**Thank you for helping keep Atlasia secure!**
