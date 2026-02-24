# SSL/TLS Certificate Setup for Atlasia

This directory contains scripts and configurations for setting up SSL/TLS certificates for local development and production deployment.

## Quick Start (Development)

Generate self-signed certificates for local development:

```bash
cd infra
./gen-ssl-cert.sh
```

This creates:
- `certs/ca.crt` - Certificate Authority certificate
- `certs/ca.key` - CA private key
- `certs/server.crt` - Server certificate (signed by CA)
- `certs/server.key` - Server private key
- `certs/keystore.p12` - PKCS12 keystore for Spring Boot
- `certs/server-bundle.crt` - Certificate bundle for PostgreSQL

## Enabling TLS

### Spring Boot Application Server

Set environment variables:

```bash
export SSL_ENABLED=true
export SSL_KEYSTORE_PATH=file:$(pwd)/infra/certs/keystore.p12
export SSL_KEYSTORE_PASSWORD=changeit
```

Or store in Vault:

```bash
vault kv put secret/atlasia/ssl-keystore-path value=file:/absolute/path/to/keystore.p12
vault kv put secret/atlasia/ssl-keystore-password value=changeit
```

Then start the application:

```bash
cd ai-orchestrator
mvn spring-boot:run
```

Access via: `https://localhost:8080`

### PostgreSQL Database

Certificates are automatically mounted when using `docker-compose.ai.yml`:

```bash
cd infra
docker-compose -f docker-compose.ai.yml up -d
```

PostgreSQL will start with TLS 1.3 enabled and require SSL connections.

## Verifying TLS Configuration

### Test Application Server

```bash
# Check TLS version and cipher
openssl s_client -connect localhost:8080 -tls1_3 -showcerts

# Expected output should include:
# Protocol  : TLSv1.3
# Cipher    : TLS_AES_256_GCM_SHA384
```

### Test PostgreSQL

```bash
# Connect with SSL required
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require" -c "SELECT version();"

# Check SSL status
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require" -c "SELECT ssl_is_used();"
```

## Production Setup

**⚠️ WARNING: Do NOT use self-signed certificates in production!**

For production environments:

### 1. Obtain CA-Signed Certificates

Use a trusted Certificate Authority:

#### Option A: Let's Encrypt (Free, Automated)

```bash
# Install certbot
sudo apt-get install certbot

# Obtain certificate
sudo certbot certonly --standalone -d atlasia.example.com

# Certificates will be in /etc/letsencrypt/live/atlasia.example.com/
```

#### Option B: Commercial CA (DigiCert, GlobalSign, etc.)

1. Generate Certificate Signing Request (CSR)
2. Submit CSR to CA
3. Complete domain validation
4. Download signed certificate

### 2. Convert to PKCS12 Format

```bash
# For Let's Encrypt
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/atlasia.example.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/atlasia.example.com/privkey.pem \
  -out keystore.p12 \
  -name atlasia-server \
  -password pass:your-secure-password

# For commercial CA
openssl pkcs12 -export \
  -in server.crt \
  -inkey server.key \
  -certfile ca-chain.crt \
  -out keystore.p12 \
  -name atlasia-server \
  -password pass:your-secure-password
```

### 3. Store in Vault

```bash
# Store keystore path and password
vault kv put secret/atlasia/ssl-keystore-path value=file:/etc/atlasia/keystore.p12
vault kv put secret/atlasia/ssl-keystore-password value=your-secure-password

# Verify storage
vault kv get secret/atlasia/ssl-keystore-path
```

### 4. Deploy Application

Update production configuration:

```yaml
# application-prod.yml
server:
  ssl:
    enabled: true
    key-store: ${vault.secret.data.atlasia.ssl-keystore-path}
    key-store-password: ${vault.secret.data.atlasia.ssl-keystore-password}
    key-store-type: PKCS12
    enabled-protocols: TLSv1.3
    ciphers: TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
```

### 5. Set Up Certificate Renewal

#### Let's Encrypt Auto-Renewal

```bash
# Test renewal
sudo certbot renew --dry-run

# Certbot automatically sets up renewal via systemd timer
systemctl list-timers | grep certbot

# Post-renewal hook to update keystore
cat > /etc/letsencrypt/renewal-hooks/post/update-keystore.sh << 'EOF'
#!/bin/bash
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/atlasia.example.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/atlasia.example.com/privkey.pem \
  -out /etc/atlasia/keystore.p12 \
  -name atlasia-server \
  -password pass:your-secure-password

systemctl restart atlasia-orchestrator
EOF

chmod +x /etc/letsencrypt/renewal-hooks/post/update-keystore.sh
```

## PostgreSQL Certificate Configuration

### Development

Certificates are automatically generated and mounted by `gen-ssl-cert.sh` and `docker-compose.ai.yml`.

### Production

1. **Copy certificates to PostgreSQL directory:**

```bash
# Create directory
sudo mkdir -p /var/lib/postgresql/certs
sudo chown postgres:postgres /var/lib/postgresql/certs
sudo chmod 700 /var/lib/postgresql/certs

# Copy certificates
sudo cp server.crt /var/lib/postgresql/certs/
sudo cp server.key /var/lib/postgresql/certs/
sudo cp ca.crt /var/lib/postgresql/certs/

# Set permissions
sudo chown postgres:postgres /var/lib/postgresql/certs/*
sudo chmod 600 /var/lib/postgresql/certs/server.key
sudo chmod 644 /var/lib/postgresql/certs/server.crt
sudo chmod 644 /var/lib/postgresql/certs/ca.crt
```

2. **Configure postgresql.conf:**

```conf
ssl = on
ssl_cert_file = '/var/lib/postgresql/certs/server.crt'
ssl_key_file = '/var/lib/postgresql/certs/server.key'
ssl_ca_file = '/var/lib/postgresql/certs/ca.crt'
ssl_min_protocol_version = 'TLSv1.3'
ssl_ciphers = 'TLS_AES_256_GCM_SHA384:TLS_AES_128_GCM_SHA256'
ssl_prefer_server_ciphers = on
```

3. **Restart PostgreSQL:**

```bash
sudo systemctl restart postgresql
```

## SSL/TLS Best Practices

### Security

1. **Use TLS 1.3 only** - Disable older versions (TLS 1.2, 1.1, 1.0)
2. **Strong ciphers only** - Use AEAD ciphers (AES-GCM)
3. **Perfect Forward Secrecy** - Ensure cipher suite supports PFS
4. **HSTS headers** - Use HTTP Strict Transport Security
5. **Certificate validation** - Use `sslmode=verify-full` in production

### Operations

1. **Monitor expiration** - Set up alerts 30 days before expiry
2. **Automate renewal** - Use certbot or similar for automatic renewal
3. **Test regularly** - Run `openssl s_client` to verify configuration
4. **Rotate regularly** - Even if not expired, rotate annually
5. **Audit access** - Log all certificate operations in Vault

### Key Management

1. **Secure storage** - Store private keys in Vault or secure filesystem
2. **Restrict permissions** - `chmod 600` on private keys
3. **No commits** - Never commit private keys to Git
4. **Backup securely** - Encrypted backups of certificates
5. **Revocation plan** - Know how to revoke if compromised

## Troubleshooting

### Application won't start with SSL enabled

```bash
# Check keystore exists and is readable
ls -la infra/certs/keystore.p12

# Test keystore password
keytool -list -keystore infra/certs/keystore.p12 -storepass changeit

# Check application logs
tail -f ai-orchestrator/target/logs/application.log
```

### PostgreSQL SSL connection fails

```bash
# Check PostgreSQL SSL status
docker-compose exec ai-db psql -U ai -c "SHOW ssl;"

# Check certificate permissions
docker-compose exec ai-db ls -la /var/lib/postgresql/certs/

# Test connection with SSL debug
psql "postgresql://ai:ai@localhost:5432/ai?sslmode=require&ssl=true" -c "SELECT ssl_is_used();"
```

### Certificate verification fails

```bash
# Verify certificate chain
openssl verify -CAfile certs/ca.crt certs/server.crt

# Check certificate details
openssl x509 -in certs/server.crt -text -noout

# Check certificate dates
openssl x509 -in certs/server.crt -noout -dates
```

## Certificate Files

| File | Description | Permissions | Location |
|------|-------------|-------------|----------|
| `ca.crt` | CA certificate (public) | 644 | `infra/certs/` |
| `ca.key` | CA private key | 600 | `infra/certs/` |
| `server.crt` | Server certificate (public) | 644 | `infra/certs/` |
| `server.key` | Server private key | 600 | `infra/certs/` |
| `keystore.p12` | PKCS12 keystore for Spring Boot | 600 | `infra/certs/` |
| `server-bundle.crt` | Certificate bundle for PostgreSQL | 644 | `infra/certs/` |

## Additional Resources

- [Spring Boot SSL Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.configure-ssl)
- [PostgreSQL SSL Support](https://www.postgresql.org/docs/current/ssl-tcp.html)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [OpenSSL Documentation](https://www.openssl.org/docs/)
- [TLS 1.3 Specification](https://datatracker.ietf.org/doc/html/rfc8446)

## Support

For issues or questions:
- Check application logs in `ai-orchestrator/target/logs/`
- Review PostgreSQL logs with `docker-compose logs ai-db`
- See main documentation in `/docs/SECURITY.md`
- Open an issue in the repository
