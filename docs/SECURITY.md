# Security Documentation

This document describes security features and processes for the Atlasia project, including TLS configuration, encryption, key rotation, and dependency vulnerability management.

## Table of Contents

1. [Transport Layer Security (TLS)](#transport-layer-security-tls)
2. [Database Encryption](#database-encryption)
3. [Key Rotation Procedures](#key-rotation-procedures)
4. [Encryption at Rest](#encryption-at-rest)
5. [Dependency Vulnerability Management](#dependency-vulnerability-management)

## Transport Layer Security (TLS)

Atlasia enforces TLS 1.3 for all network communications to protect data in transit.

### Application Server (Spring Boot)

#### Configuration

The application server is configured in `application.yml` to use TLS 1.3:

```yaml
server:
  port: 8080
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${vault.secret.data.atlasia.ssl-keystore-path:${SSL_KEYSTORE_PATH:classpath:keystore.p12}}
    key-store-password: ${vault.secret.data.atlasia.ssl-keystore-password:${SSL_KEYSTORE_PASSWORD:}}
    key-store-type: PKCS12
    enabled-protocols: TLSv1.3
    ciphers: TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
```

**Key Features:**
- **Protocol**: TLS 1.3 only (older versions disabled)
- **Ciphers**: Strong AEAD ciphers (AES-GCM with SHA-384/SHA-256)
- **Keystore**: PKCS12 format, password stored in Vault
- **Keystore path**: Can be loaded from filesystem or classpath

#### Generating SSL Certificates (Development)

For local development, use the provided script to generate self-signed certificates:

```bash
cd infra
./gen-ssl-cert.sh
```

This creates:
- CA certificate and key
- Server certificate and key
- PKCS12 keystore for Spring Boot
- PostgreSQL certificate bundle

**Note**: Self-signed certificates are for development only. Use proper CA-signed certificates in production.

#### Enabling TLS

Set the following environment variables:

```bash
export SSL_ENABLED=true
export SSL_KEYSTORE_PATH=file:/path/to/infra/certs/keystore.p12
export SSL_KEYSTORE_PASSWORD=changeit
```

Or store in Vault:

```bash
vault kv put secret/atlasia/ssl-keystore-path value=file:/path/to/keystore.p12
vault kv put secret/atlasia/ssl-keystore-password value=your-secure-password
```

#### Production Setup

For production environments:

1. **Obtain CA-signed certificates** from a trusted CA (Let's Encrypt, DigiCert, etc.)
2. **Convert to PKCS12 format**:
   ```bash
   openssl pkcs12 -export -in cert.pem -inkey key.pem -out keystore.p12 -name atlasia-server
   ```
3. **Store keystore and password in Vault**
4. **Update application configuration** to reference Vault paths
5. **Set up certificate renewal** (e.g., certbot for Let's Encrypt)

### Database (PostgreSQL)

#### Configuration

PostgreSQL is configured to require TLS 1.3 for all client connections:

```yaml
command: >
  postgres
  -c ssl=on
  -c ssl_cert_file=/var/lib/postgresql/certs/server.crt
  -c ssl_key_file=/var/lib/postgresql/certs/server.key
  -c ssl_ca_file=/var/lib/postgresql/certs/ca.crt
  -c ssl_min_protocol_version=TLSv1.3
  -c ssl_ciphers='TLS_AES_256_GCM_SHA384:TLS_AES_128_GCM_SHA256'
  -c ssl_prefer_server_ciphers=on
```

**Key Features:**
- **Protocol**: TLS 1.3 minimum
- **Client authentication**: Requires SSL mode
- **Ciphers**: Strong AEAD ciphers only
- **Server preference**: Server cipher order takes precedence

#### JDBC Connection

The JDBC connection string enforces SSL:

```
jdbc:postgresql://host:5432/ai?ssl=true&sslmode=require
```

**SSL Modes:**
- `require`: Encryption required (used in production)
- `verify-ca`: Also verify server certificate against CA
- `verify-full`: Also verify hostname matches certificate

For production, consider `verify-full` for maximum security.

## Database Encryption

Atlasia implements column-level encryption for sensitive data using AES-256-GCM.

### Column Encryption Service

`ColumnEncryptionService` provides encryption/decryption using:
- **Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key size**: 256 bits
- **Authentication**: GCM provides authenticated encryption
- **IV**: Random 12-byte initialization vector per encryption
- **Storage**: IV prepended to ciphertext, base64 encoded

#### Usage

The service is automatically used via JPA attribute converters:

```java
@Column(name = "sensitive_field_encrypted", columnDefinition = "TEXT")
@Convert(converter = EncryptedStringConverter.class)
private String sensitiveField;
```

#### Configuration

Encryption key must be configured in Vault:

```bash
# Generate a 256-bit key
openssl rand -base64 32

# Store in Vault
vault kv put secret/atlasia/encryption-key value=<base64-encoded-key>
```

Or set via environment variable:

```bash
export VAULT_ENCRYPTION_KEY=<base64-encoded-key>
```

### Encrypted Fields

The following sensitive fields are encrypted at rest:

| Entity | Field | Column | Encryption |
|--------|-------|--------|------------|
| OAuth2AccountEntity | accessToken | access_token_encrypted | AES-256-GCM |
| OAuth2AccountEntity | refreshToken | refresh_token_encrypted | AES-256-GCM |
| UserEntity | mfaSecret | mfa_secret_encrypted | AES-256-GCM |
| RefreshTokenEntity | tokenHash | token_hash | SHA-256 (hash only) |

**Note**: `refresh_tokens.token_hash` is hashed, not encrypted, as it's used for lookup and doesn't need to be reversed.

### Implementation Details

**Encryption Flow:**
1. Generate random 12-byte IV using `SecureRandom`
2. Encrypt plaintext with AES-256-GCM using IV
3. Prepend IV to ciphertext
4. Base64 encode entire blob
5. Store in database TEXT column

**Decryption Flow:**
1. Base64 decode from database
2. Extract first 12 bytes as IV
3. Decrypt remaining bytes with AES-256-GCM
4. Return plaintext

**Security Properties:**
- **Confidentiality**: AES-256 provides strong encryption
- **Integrity**: GCM authentication tag prevents tampering
- **Uniqueness**: Random IV ensures different ciphertext for same plaintext
- **Forward secrecy**: Old ciphertexts remain secure after key rotation

## Key Rotation Procedures

Regular key rotation is critical for maintaining security. Atlasia supports rotation of encryption keys and TLS certificates.

### Encryption Key Rotation

#### When to Rotate

- **Scheduled**: Every 90 days (recommended)
- **Compromise**: Immediately if key may be exposed
- **Employee departure**: If key custodian leaves
- **Audit requirement**: As mandated by compliance

#### Rotation Process

1. **Generate new encryption key:**
   ```bash
   NEW_KEY=$(openssl rand -base64 32)
   ```

2. **Store new key in Vault with versioning:**
   ```bash
   # Store new key as current
   vault kv put secret/atlasia/encryption-key value=$NEW_KEY
   
   # Archive old key with version number
   vault kv put secret/atlasia/encryption-key-v1 value=$OLD_KEY
   ```

3. **Deploy key rotation script:**
   ```bash
   cd ai-orchestrator
   mvn exec:java -Dexec.mainClass="com.atlasia.ai.migration.EncryptionKeyRotation"
   ```

4. **Verify rotation:**
   - Check application logs for successful re-encryption
   - Test reading encrypted fields
   - Monitor for decryption errors

5. **Update monitoring:**
   - Document key rotation in audit log
   - Update key version in documentation
   - Schedule next rotation

#### Migration Script (Pseudocode)

```java
// Re-encrypt all sensitive fields with new key
1. Load old key from Vault (encryption-key-v1)
2. Load new key from Vault (encryption-key)
3. For each entity with encrypted fields:
   a. Decrypt with old key
   b. Encrypt with new key
   c. Update database
   d. Commit transaction
4. Verify all records updated
5. Log completion
```

### TLS Certificate Rotation

#### When to Rotate

- **Scheduled**: 30 days before expiration
- **Compromise**: Immediately if private key exposed
- **Algorithm upgrade**: When stronger algorithms available
- **CA requirement**: Per CA policy

#### Rotation Process

1. **Generate/obtain new certificate:**
   ```bash
   # Development
   cd infra && ./gen-ssl-cert.sh
   
   # Production - use your CA's process
   certbot renew --cert-name atlasia.example.com
   ```

2. **Update Vault with new certificate paths:**
   ```bash
   vault kv put secret/atlasia/ssl-keystore-path value=file:/path/to/new/keystore.p12
   vault kv put secret/atlasia/ssl-keystore-password value=new-password
   ```

3. **Rolling restart application servers:**
   ```bash
   # Kubernetes
   kubectl rollout restart deployment/ai-orchestrator
   
   # Docker Compose
   docker-compose restart ai-orchestrator
   ```

4. **Update PostgreSQL certificates:**
   ```bash
   # Copy new certificates
   cp infra/certs/server.* /var/lib/postgresql/certs/
   
   # Reload PostgreSQL configuration
   docker-compose exec ai-db pg_ctl reload
   ```

5. **Verify TLS configuration:**
   ```bash
   # Test application server
   openssl s_client -connect localhost:8080 -tls1_3
   
   # Test PostgreSQL
   psql "sslmode=require host=localhost" -c "SELECT version()"
   ```

### Automated Rotation

Atlasia includes `SecretRotationScheduler` for automated key rotation:

```java
@Scheduled(cron = "0 0 0 1 */3 *") // Every 3 months
public void rotateEncryptionKey() {
    // Automated rotation logic
}

@Scheduled(cron = "0 0 0 * * *") // Daily check
public void checkCertificateExpiry() {
    // Alert if certificates expire within 30 days
}
```

### Key Storage Best Practices

1. **Use Vault**: Never store keys in code, config files, or environment variables
2. **Separate duties**: Different people control key generation vs. usage
3. **Audit access**: Log all key retrievals from Vault
4. **Backup keys**: Securely backup old keys for data recovery
5. **Destroy old keys**: After rotation period, securely delete old keys

## Encryption at Rest

In addition to column-level encryption, Atlasia supports full disk encryption for database volumes.

### LUKS Encryption (Linux)

For on-premises deployments on Linux:

1. **Create encrypted volume:**
   ```bash
   # Create LUKS volume
   cryptsetup luksFormat /dev/sdb
   
   # Open volume
   cryptsetup luksOpen /dev/sdb postgres_encrypted
   
   # Format filesystem
   mkfs.ext4 /dev/mapper/postgres_encrypted
   
   # Mount
   mount /dev/mapper/postgres_encrypted /var/lib/postgresql/data
   ```

2. **Configure auto-unlock:**
   ```bash
   # Store key in secure location
   echo "your-secure-passphrase" > /root/postgres-luks.key
   chmod 600 /root/postgres-luks.key
   
   # Add to /etc/crypttab
   echo "postgres_encrypted /dev/sdb /root/postgres-luks.key" >> /etc/crypttab
   
   # Add to /etc/fstab
   echo "/dev/mapper/postgres_encrypted /var/lib/postgresql/data ext4 defaults 0 2" >> /etc/fstab
   ```

3. **Update Docker Compose:**
   ```yaml
   volumes:
     ai_db:
       driver: local
       driver_opts:
         type: none
         o: bind
         device: /var/lib/postgresql/data  # Encrypted mount point
   ```

### Cloud Provider Encryption

#### AWS (EBS Encryption)

```hcl
resource "aws_ebs_volume" "postgres" {
  availability_zone = "us-east-1a"
  size             = 100
  encrypted        = true
  kms_key_id       = aws_kms_key.postgres.arn
  
  tags = {
    Name = "atlasia-postgres-data"
  }
}
```

#### Google Cloud (Disk Encryption)

```hcl
resource "google_compute_disk" "postgres" {
  name  = "atlasia-postgres-disk"
  type  = "pd-ssd"
  zone  = "us-central1-a"
  size  = 100
  
  disk_encryption_key {
    kms_key_self_link = google_kms_crypto_key.postgres.id
  }
}
```

#### Azure (Disk Encryption)

```hcl
resource "azurerm_managed_disk" "postgres" {
  name                 = "atlasia-postgres-disk"
  location             = azurerm_resource_group.main.location
  resource_group_name  = azurerm_resource_group.main.name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = 100
  
  encryption_settings {
    enabled = true
    disk_encryption_key {
      secret_url      = azurerm_key_vault_secret.disk_encryption.id
      source_vault_id = azurerm_key_vault.main.id
    }
  }
}
```

### Docker Volume Encryption

For Docker Compose deployments with encryption:

```yaml
volumes:
  ai_db:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /path/to/encrypted/mount  # LUKS or cloud-encrypted volume
```

**Note**: The `device` path should point to an encrypted filesystem (LUKS, EBS, etc.).

## Dependency Vulnerability Management

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
