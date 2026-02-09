---
name: security-review
description: >
  Perform security audits on Java/Spring Boot, Angular, and Node.js applications following OWASP
  Top 10 and security best practices. Use this skill when the user asks to:
  (1) Review code for security vulnerabilities,
  (2) Audit authentication/authorization implementations,
  (3) Check for OWASP Top 10 vulnerabilities,
  (4) Harden Spring Security, JWT, or OAuth2 configurations,
  (5) Review API security, CORS, CSP, or header configurations,
  (6) Implement secure coding patterns.
  Triggers: "security review", "security audit", "vulnerability", "OWASP",
  "SQL injection", "XSS", "CSRF", "authentication", "authorization",
  "Spring Security", "CORS", "JWT security", "penetration", "harden", "secure this".
---

# Security Review Skill

## Workflow

1. **Identify scope**: Full application, specific module, or single file.
2. **Detect stack**: Java/Spring, Angular, Node.js.
3. **Analyze**: Evaluate against OWASP Top 10 + stack-specific rules in `references/security-checklist.md`.
4. **Categorize**: Critical (exploitable) → High (potential exploit) → Medium (defense-in-depth) → Low (hardening).
5. **Output**: Security report + remediation code.

## Security Report Template

```markdown
# Security Audit: [Application/Module Name]

## Risk Summary
| Severity | Count |
|----------|-------|
| Critical | X |
| High | X |
| Medium | X |
| Low | X |

## Findings

### [CRITICAL] Finding 1: [Title]
**OWASP Category**: A01:2021 - Broken Access Control
**Location**: `UserController.java:42`
**Description**: [What the vulnerability is]
**Impact**: [What an attacker could do]
**Remediation**:
[Code fix with before/after]

## Security Checklist
- [ ] Authentication properly enforced on all endpoints
- [ ] Authorization checked at service layer
- [ ] Input validation on all user inputs
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS prevention (output encoding)
- [ ] CSRF protection enabled
- [ ] Sensitive data encrypted at rest and in transit
- [ ] Security headers configured (CSP, X-Frame-Options, etc.)
- [ ] Dependency vulnerabilities checked
- [ ] Logging does not contain sensitive data
```

## General Rules

- Always provide **remediation code** — not just descriptions.
- Reference specific OWASP category for each finding.
- Check both application code AND configuration (Spring Security, CORS, headers).
- For Angular: check for DOM XSS, unsafe innerHTML, bypassSecurityTrust usage.
- For Spring: check endpoint security annotations, method security, filter chains.
- For Node.js: check middleware ordering, input sanitization, rate limiting.
- Include dependency vulnerability check recommendations (OWASP Dependency-Check, npm audit, Snyk).
