# Security - Dependency Vulnerability Management

This document describes the process for dependency vulnerability scanning, SBOM generation, and remediation in the Atlasia project.

## Overview

Atlasia uses automated security scanning to identify vulnerabilities in both backend (Maven/Java) and frontend (NPM/Node.js) dependencies:

- **Backend**: OWASP Dependency-Check for vulnerability scanning, CycloneDX for SBOM generation
- **Frontend**: NPM Audit for vulnerability scanning
- **Automation**: GitHub Actions workflows run on PRs and weekly schedules

## Backend Security (Maven)

### OWASP Dependency-Check

The Maven build includes the OWASP Dependency-Check plugin to scan for known vulnerabilities.

#### Configuration

Located in `ai-orchestrator/pom.xml`:
- **Fail threshold**: CVSS 7.0 (HIGH severity)
- **Reports**: HTML and JSON formats
- **Execution phase**: `verify`
- **Suppression file**: `dependency-check-suppressions.xml`

#### Running Locally

```bash
cd ai-orchestrator

# Run dependency check
mvn dependency-check:check

# View report
open target/dependency-check-report.html

# Or run as part of verify phase
mvn clean verify
```

#### Suppressing False Positives

If Dependency-Check reports false positives, add suppressions to `ai-orchestrator/dependency-check-suppressions.xml`:

```xml
<suppress>
   <notes><![CDATA[
       CVE-2024-12345 does not affect this usage of the library because...
   ]]></notes>
   <packageUrl regex="true">^pkg:maven/com\.example/vulnerable\-lib@.*$</packageUrl>
   <cve>CVE-2024-12345</cve>
</suppress>
```

**Important**: Always document the reason for suppression in the `<notes>` section.

### SBOM Generation

CycloneDX Maven plugin generates a Software Bill of Materials (SBOM) in JSON format.

#### Running Locally

```bash
cd ai-orchestrator

# Generate SBOM
mvn cyclonedx:makeAggregateBom

# View SBOM
cat target/bom.json
```

The SBOM includes:
- All project dependencies with versions
- Transitive dependencies
- License information
- Component hashes

#### Using the SBOM

The SBOM can be used for:
- Supply chain security analysis
- Compliance reporting
- Dependency auditing
- Vulnerability tracking in external tools

## Frontend Security (NPM)

### NPM Audit

NPM Audit checks for vulnerabilities in frontend dependencies.

#### Configuration

Located in `frontend/package.json`:
- **Fail threshold**: Moderate severity or higher
- **Scripts**: `npm run audit` and `npm run audit:fix`

#### Running Locally

```bash
cd frontend

# Run audit
npm run audit

# Attempt automatic fixes
npm run audit:fix

# Or use npm directly
npm audit --audit-level=moderate
```

#### Remediation Process

1. **Automated fixes**: Run `npm audit fix` to automatically update vulnerable dependencies
2. **Manual updates**: For breaking changes, update `package.json` manually and test
3. **Version pinning**: If no fix is available, consider pinning to a specific version temporarily
4. **Override vulnerabilities**: Use `.npmrc` overrides as a last resort (document thoroughly)

## Automated Security Workflows

### Backend Security Scan (`security-scan.yml`)

Runs on:
- Pull requests to `main` or `develop`
- Weekly schedule (Mondays at 2 AM UTC)
- Manual trigger via workflow dispatch

Jobs:
1. **OWASP Dependency Check**: Scans for vulnerabilities, uploads reports, comments on PRs
2. **SBOM Generation**: Creates CycloneDX SBOM, uploads as artifact

Artifacts:
- Dependency check reports (retained 30 days)
- SBOM (retained 90 days)

### Frontend Security Audit (`npm-audit.yml`)

Runs on:
- Pull requests affecting `frontend/package.json` or `frontend/package-lock.json`
- Weekly schedule (Mondays at 2 AM UTC)
- Manual trigger via workflow dispatch

Jobs:
- **NPM Audit**: Runs audit, uploads results, comments on PRs, fails on moderate+ vulnerabilities

Artifacts:
- Audit results JSON (retained 30 days)

## Vulnerability Response Process

### 1. Detection

Vulnerabilities are detected through:
- Automated weekly scans
- PR checks before merge
- Manual security audits
- Security advisories from GitHub/NVD

### 2. Assessment

When a vulnerability is found:

1. **Review the CVE details**:
   - Severity level (Critical/High/Moderate/Low)
   - Affected versions
   - Attack vector and exploitability
   - Impact on Atlasia

2. **Determine applicability**:
   - Is the vulnerable code path used in Atlasia?
   - Is the vulnerability exploitable in our deployment?
   - What is the risk to users and data?

3. **Check for fixes**:
   - Is a patched version available?
   - Are there breaking changes?
   - Is a workaround documented?

### 3. Remediation

Based on severity and applicability:

**Critical/High Severity (Exploitable)**:
- Immediate action required
- Update dependency ASAP
- Test thoroughly
- Deploy hotfix if in production
- Document in security advisory

**Moderate Severity**:
- Update in next release cycle
- Track in issue tracker
- Test with regular QA process

**Low Severity / Not Applicable**:
- Update during regular maintenance
- Consider suppressing if false positive
- Document decision

### 4. Documentation

After remediation:
1. Update dependency version in `pom.xml` or `package.json`
2. Run tests to verify no regressions
3. Update `dependency-check-suppressions.xml` if suppressing
4. Document in PR description and commit message
5. Update this document if process changes

## Severity Thresholds

### Backend (OWASP Dependency-Check)

| Severity | CVSS Score | Action |
|----------|------------|--------|
| Critical | 9.0 - 10.0 | Build fails, immediate fix required |
| High | 7.0 - 8.9 | Build fails, fix within 7 days |
| Medium | 4.0 - 6.9 | Warning only, fix in next sprint |
| Low | 0.1 - 3.9 | Warning only, fix during maintenance |

### Frontend (NPM Audit)

| Severity | Action |
|----------|--------|
| Critical | Build fails, immediate fix required |
| High | Build fails, fix within 7 days |
| Moderate | Build fails, fix within 14 days |
| Low | Warning only, fix during maintenance |
| Info | Warning only, no action required |

## Best Practices

### Dependency Management

1. **Keep dependencies up to date**: Regularly update to latest stable versions
2. **Review changelogs**: Check for breaking changes before updating
3. **Pin production versions**: Use specific versions in production, not ranges
4. **Minimize dependencies**: Remove unused dependencies
5. **Audit new dependencies**: Review security posture before adding

### Security Scanning

1. **Run locally before push**: Check for issues before creating PR
2. **Don't suppress without justification**: Always document why
3. **Review automated PRs**: Dependabot/Renovate PRs need human review
4. **Monitor security advisories**: Subscribe to security mailing lists
5. **Schedule regular reviews**: Monthly review of all dependencies

### Incident Response

1. **Have a security contact**: Designate someone for security reports
2. **Communicate clearly**: Inform stakeholders of security issues
3. **Track vulnerabilities**: Use issue tracker for remediation work
4. **Learn from incidents**: Update processes after security events
5. **Maintain audit trail**: Document all security decisions

## Tools and Resources

### Scanning Tools

- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
- [CycloneDX](https://cyclonedx.org/)
- [NPM Audit](https://docs.npmjs.com/cli/v8/commands/npm-audit)

### Vulnerability Databases

- [National Vulnerability Database (NVD)](https://nvd.nist.gov/)
- [GitHub Advisory Database](https://github.com/advisories)
- [Snyk Vulnerability Database](https://security.snyk.io/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)

### Additional Reading

- [NIST Secure Software Development Framework](https://csrc.nist.gov/Projects/ssdf)
- [OWASP Software Component Verification Standard](https://owasp.org/www-project-software-component-verification-standard/)
- [CycloneDX SBOM Standard](https://cyclonedx.org/specification/overview/)

## Configuration Files

### Backend

- `ai-orchestrator/pom.xml` - Maven plugin configuration
- `ai-orchestrator/dependency-check-suppressions.xml` - False positive suppressions

### Frontend

- `frontend/package.json` - NPM scripts and dependencies
- `frontend/package-lock.json` - Locked dependency versions

### CI/CD

- `.github/workflows/security-scan.yml` - Backend security workflow
- `.github/workflows/npm-audit.yml` - Frontend security workflow

## Support

For security questions or to report vulnerabilities:
- Create an issue in the repository (for non-sensitive issues)
- Contact the security team directly (for sensitive vulnerabilities)
- Review existing security documentation in `/docs`

## Maintenance

This document should be reviewed and updated:
- When security tools are added/removed
- When thresholds or policies change
- After security incidents
- Quarterly as part of security review
