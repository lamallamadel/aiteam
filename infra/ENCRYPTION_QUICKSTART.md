# Encryption Quick Start Guide

This guide provides quick setup instructions for TLS and database encryption in Atlasia.

## Prerequisites

- OpenSSL installed
- Docker and Docker Compose
- Vault running (via `docker-compose.ai.yml`)

## Setup Steps

### 1. Initialize Vault with Encryption Keys

```bash
cd infra
./vault-init.sh
```

This generates and stores:
- AES-256 encryption key for database column encryption
- JWT signing key
- Database password
- SSL keystore configuration (placeholder)

**Important**: Save the displayed secrets securely!

### 2. Generate SSL/TLS Certificates

```bash
cd infra
./gen-ssl-cert.sh
```

This creates:
- Self-signed CA and server certificates
- PKCS12 keystore for Spring Boot (`certs/keystore.p12`)
- PostgreSQL certificates (`certs/server.crt`, `certs/server.key`)

Default password: `changeit`

### 3. Configure Environment

Set these environment variables:

```bash
# Vault connection
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-root-token
export SPRING_CLOUD_VAULT_ENABLED=true

# Enable SSL (optional for dev)
export SSL_ENABLED=false  # Set to true for HTTPS

# Database connection (SSL required)
export DB_URL="jdbc:postgresql://localhost:5432/ai?ssl=true&sslmode=require"
```

### 4. Start Infrastructure

```bash
cd infra
docker-compose -f docker-compose.ai.yml up -d
```

This starts:
- PostgreSQL with TLS 1.3 enabled
- Vault for secrets management
- Application server (if configured)

### 5. Verify Setup

#### Check Vault Secrets

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-root-token

vault kv get secret/atlasia
```

Should show:
- `encryption-key` (base64 encoded, 44 characters)
- `ssl-keystore-path` (file path to keystore)
- `ssl-keystore-password` (default: changeit)

#### Check PostgreSQL SSL

```bash
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require" -c "SHOW ssl;"
```

Expected output: `on`

#### Check Column Encryption

After starting the application:

```bash
# Check application logs for encryption initialization
tail -f ai-orchestrator/target/logs/application.log | grep -i encryption
```

Should show: `ColumnEncryptionService initialized with AES-256-GCM`

## What Gets Encrypted

### Column-Level Encryption (AES-256-GCM)

Automatically encrypted via JPA converters:

| Table | Column | Purpose |
|-------|--------|---------|
| `oauth2_accounts` | `access_token_encrypted` | OAuth2 access tokens |
| `oauth2_accounts` | `refresh_token_encrypted` | OAuth2 refresh tokens |
| `users` | `mfa_secret_encrypted` | MFA TOTP secrets |

### Transport Encryption (TLS 1.3)

- **Application ↔ Database**: TLS 1.3 with `TLS_AES_256_GCM_SHA384`
- **Client ↔ Application**: TLS 1.3 (when `SSL_ENABLED=true`)

### Encryption at Rest

PostgreSQL data volume can use:
- LUKS encryption (Linux)
- EBS encryption (AWS)
- Disk encryption (GCP/Azure)

See `docs/SECURITY.md` for setup instructions.

## Troubleshooting

### Encryption Key Not Found

**Error**: `Encryption key not configured`

**Solution**:
```bash
# Verify Vault secret exists
vault kv get secret/atlasia

# Check encryption-key field
vault kv get -field=encryption-key secret/atlasia

# Re-initialize if missing
cd infra && ./vault-init.sh
```

### SSL Connection Fails

**Error**: `FATAL: no pg_hba.conf entry for host`

**Solution**:
```bash
# Check PostgreSQL is running with SSL
docker-compose -f docker-compose.ai.yml logs ai-db | grep ssl

# Verify certificates exist
ls -la infra/certs/

# Regenerate if missing
cd infra && ./gen-ssl-cert.sh
```

### Keystore Not Found

**Error**: `java.io.FileNotFoundException: keystore.p12`

**Solution**:
```bash
# Generate certificates
cd infra && ./gen-ssl-cert.sh

# Update Vault with absolute path
vault kv put secret/atlasia ssl-keystore-path=file:$(pwd)/infra/certs/keystore.p12

# Or disable SSL for development
export SSL_ENABLED=false
```

### Decryption Fails

**Error**: `Decryption failed`

**Causes**:
1. Encryption key changed but data not re-encrypted
2. Corrupted ciphertext in database
3. Wrong encryption key loaded

**Solution**:
```bash
# Verify correct key is loaded
vault kv get -field=encryption-key secret/atlasia

# Check application configuration
grep encryption-key ai-orchestrator/src/main/resources/application.yml

# If key rotated, run migration script (future feature)
```

## Key Rotation

### Encryption Key Rotation

**Frequency**: Every 90 days (recommended)

**Process**:
1. Generate new key: `openssl rand -base64 32`
2. Store old key: `vault kv put secret/atlasia/encryption-key-v1 value=$OLD_KEY`
3. Store new key: `vault kv put secret/atlasia/encryption-key value=$NEW_KEY`
4. Run re-encryption migration (future feature)
5. Verify all data accessible

### TLS Certificate Rotation

**Frequency**: 30 days before expiration

**Process**:
1. Generate new certificates: `./gen-ssl-cert.sh`
2. Update Vault: `vault kv put secret/atlasia ssl-keystore-password value=new-password`
3. Restart services: `docker-compose restart`

## Security Best Practices

### Development

✅ **DO**:
- Use self-signed certificates for local development
- Store secrets in Vault, not environment variables
- Test with SSL enabled periodically
- Rotate development keys quarterly

❌ **DON'T**:
- Commit certificates or keys to Git (already in `.gitignore`)
- Share Vault tokens
- Use production keys in development
- Disable encryption in development

### Production

✅ **DO**:
- Use CA-signed certificates (Let's Encrypt, commercial CA)
- Enable SSL for all connections (`SSL_ENABLED=true`)
- Use `sslmode=verify-full` for database connections
- Enable disk encryption (LUKS, EBS, etc.)
- Rotate keys every 90 days
- Monitor certificate expiration
- Enable audit logging in Vault

❌ **DON'T**:
- Use self-signed certificates
- Store keys outside Vault
- Disable SSL in production
- Skip key rotation
- Share encryption keys between environments

## Additional Resources

- **Full Security Documentation**: `docs/SECURITY.md`
- **SSL Setup Guide**: `infra/README-SSL.md`
- **Vault Setup**: `docs/VAULT_SETUP.md`
- **Database Migrations**: `ai-orchestrator/src/main/resources/db/migration/V17__add_column_encryption.sql`

## Quick Reference

```bash
# Generate all secrets and certificates
cd infra
./vault-init.sh
./gen-ssl-cert.sh

# Start infrastructure with TLS
docker-compose -f docker-compose.ai.yml up -d

# Verify encryption
vault kv get secret/atlasia
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require" -c "SHOW ssl;"

# View encrypted data (base64 encoded ciphertext)
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require" \
  -c "SELECT access_token_encrypted FROM oauth2_accounts LIMIT 1;"

# Restart with SSL enabled
export SSL_ENABLED=true
export SSL_KEYSTORE_PASSWORD=changeit
cd ai-orchestrator && mvn spring-boot:run
```

## Support

For issues or questions:
- Review logs: `docker-compose logs -f`
- Check Vault status: `vault status`
- See troubleshooting: `docs/SECURITY.md`
- Open an issue in the repository
