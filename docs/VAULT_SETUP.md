# HashiCorp Vault Integration Guide

This document describes the HashiCorp Vault integration for secrets management in the Atlasia AI Orchestrator.

## Overview

The application uses HashiCorp Vault to securely manage sensitive configuration data including:
- Database passwords
- JWT signing keys
- LLM API keys
- OAuth2 client credentials
- GitHub App private keys
- Encryption keys
- Orchestrator authentication tokens

## Architecture

### Spring Cloud Vault

The application uses Spring Cloud Vault to integrate with HashiCorp Vault:

- **bootstrap.yml**: Configures Vault connection during Spring Boot bootstrap phase
- **VaultSecretsService**: Provides programmatic access to Vault secrets
- **VaultHealthIndicator**: Monitors Vault connectivity via Spring Actuator
- **SecretRotationScheduler**: Automates periodic secret rotation

### Secret Storage Structure

All secrets are stored under the KV v2 path: `secret/data/atlasia/`

```
secret/
└── atlasia/
    ├── orchestrator-token
    ├── jwt-secret
    ├── db-password
    ├── llm-api-key
    ├── llm-fallback-api-key
    ├── github-private-key-path
    ├── oauth2-github-client-id
    ├── oauth2-github-client-secret
    ├── oauth2-google-client-id
    ├── oauth2-google-client-secret
    ├── oauth2-gitlab-client-id
    ├── oauth2-gitlab-client-secret
    └── encryption-key
```

## Quick Start

### 1. Start Vault in Development Mode

```bash
docker run --cap-add=IPC_LOCK -d --name=vault -p 8200:8200 \
  -e 'VAULT_DEV_ROOT_TOKEN_ID=dev-root-token' \
  -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' \
  hashicorp/vault:latest
```

### 2. Initialize Secrets

Use the provided initialization script:

```bash
cd infra
chmod +x vault-init.sh
./vault-init.sh
```

Or manually:

```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Generate and store JWT secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
vault kv put secret/atlasia jwt-secret="$JWT_SECRET"

# Store other secrets...
```

### 3. Enable Vault in Application

Set environment variables:

```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
export SPRING_CLOUD_VAULT_ENABLED=true
```

Or update `.env` file:

```properties
VAULT_ADDR=http://localhost:8200
VAULT_TOKEN=dev-root-token
SPRING_CLOUD_VAULT_ENABLED=true
```

### 4. Start Application

```bash
cd ai-orchestrator
mvn spring-boot:run
```

The application will automatically fetch secrets from Vault at startup.

## Configuration Files

### bootstrap.yml

Bootstrap configuration loaded before main application configuration:

```yaml
spring:
  application:
    name: ai-orchestrator
  cloud:
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      authentication: TOKEN
      token: ${VAULT_TOKEN:}
      kv:
        enabled: true
        backend: secret
        default-context: atlasia
        application-name: ${spring.application.name}
      connection-timeout: 5000
      read-timeout: 15000
      config:
        lifecycle:
          enabled: true
```

### application.yml

Application properties reference Vault secrets with fallback to environment variables:

```yaml
atlasia:
  jwt:
    secret-key: ${vault.secret.data.atlasia.jwt-secret:${JWT_SECRET_KEY:}}
  orchestrator:
    token: ${vault.secret.data.atlasia.orchestrator-token:${ORCHESTRATOR_TOKEN:changeme}}
spring:
  datasource:
    password: ${vault.secret.data.atlasia.db-password:${DB_PASSWORD:ai}}
```

The pattern `${vault.secret.data.atlasia.jwt-secret:${JWT_SECRET_KEY:}}` means:
1. Try to fetch from Vault at `secret/data/atlasia/jwt-secret`
2. Fall back to environment variable `JWT_SECRET_KEY`
3. Fall back to empty string if both are missing

## Services

### VaultSecretsService

Programmatic access to Vault:

```java
@Autowired
private VaultSecretsService vaultSecretsService;

// Read a single secret value
String apiKey = vaultSecretsService.getSecret("secret/data/atlasia/llm-api-key");

// Read all data from a path
Map<String, Object> secrets = vaultSecretsService.getSecretData("secret/data/atlasia");

// Rotate a secret
vaultSecretsService.rotateSecret("secret/data/atlasia/jwt-secret", newValue);

// Write multiple values
vaultSecretsService.writeSecret("secret/data/atlasia", Map.of(
    "key1", "value1",
    "key2", "value2"
));
```

### SecretRotationScheduler

Automated secret rotation:

- **JWT Signing Key**: Monthly rotation (1st of month, 2:00 AM)
  - Cron: `0 0 2 1 * ?`
  - Automatically generates new secure key
  
- **OAuth2 Secrets**: Quarterly rotation (every 3 months, 1st of month, 2:00 AM)
  - Cron: `0 0 2 1 */3 ?`
  - Logs reminder to manually rotate in OAuth provider consoles

To trigger manual rotation:

```bash
# Via Vault CLI
vault kv put secret/atlasia jwt-secret="$(openssl rand -base64 64 | tr -d '\n')"

# Via REST API
curl -X POST http://localhost:8080/api/admin/rotate-jwt-secret \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

### VaultHealthIndicator

Health check endpoint for Vault:

```bash
curl http://localhost:8080/actuator/health
```

Response:

```json
{
  "status": "UP",
  "components": {
    "vault": {
      "status": "UP",
      "details": {
        "initialized": true,
        "sealed": false,
        "standby": false,
        "serverTime": 1234567890,
        "version": "1.15.0"
      }
    }
  }
}
```

## Production Deployment

### 1. Vault Server Setup

Install and configure Vault server:

```bash
# Install Vault
wget https://releases.hashicorp.com/vault/1.15.0/vault_1.15.0_linux_amd64.zip
unzip vault_1.15.0_linux_amd64.zip
sudo mv vault /usr/local/bin/

# Create Vault configuration
cat > /etc/vault.d/vault.hcl <<EOF
storage "raft" {
  path = "/opt/vault/data"
  node_id = "node1"
}

listener "tcp" {
  address = "0.0.0.0:8200"
  tls_disable = false
  tls_cert_file = "/etc/vault.d/cert.pem"
  tls_key_file = "/etc/vault.d/key.pem"
}

api_addr = "https://vault.example.com:8200"
cluster_addr = "https://vault.example.com:8201"
ui = true
EOF

# Initialize Vault
vault operator init -key-shares=5 -key-threshold=3

# Unseal Vault (requires 3 of 5 keys)
vault operator unseal <key1>
vault operator unseal <key2>
vault operator unseal <key3>
```

### 2. Authentication Setup

Use AppRole for production:

```bash
# Enable AppRole auth
vault auth enable approle

# Create policy
vault policy write atlasia-read - <<EOF
path "secret/data/atlasia/*" {
  capabilities = ["read", "list"]
}
path "secret/metadata/atlasia/*" {
  capabilities = ["list"]
}
EOF

# Create AppRole
vault write auth/approle/role/atlasia-orchestrator \
  token_policies="atlasia-read" \
  token_ttl=1h \
  token_max_ttl=4h \
  bind_secret_id=true

# Get credentials
ROLE_ID=$(vault read -field=role_id auth/approle/role/atlasia-orchestrator/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/atlasia-orchestrator/secret-id)
```

### 3. Application Configuration

Update bootstrap.yml for production:

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      ssl:
        trust-store: classpath:vault-truststore.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
```

Or use environment variables:

```bash
export VAULT_ADDR='https://vault.example.com:8200'
export SPRING_CLOUD_VAULT_ENABLED=true
export SPRING_CLOUD_VAULT_AUTHENTICATION=APPROLE
export SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID='role-id-here'
export SPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID='secret-id-here'
```

### 4. Enable Audit Logging

```bash
vault audit enable file file_path=/var/log/vault/audit.log
```

### 5. Backup Strategy

```bash
# Raft storage snapshot
vault operator raft snapshot save backup.snap

# Restore from snapshot
vault operator raft snapshot restore backup.snap
```

## Kubernetes Deployment

### 1. Install Vault via Helm

```bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault
```

### 2. Configure Kubernetes Auth

```bash
vault auth enable kubernetes

vault write auth/kubernetes/config \
  kubernetes_host="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

vault write auth/kubernetes/role/atlasia-orchestrator \
  bound_service_account_names=atlasia-orchestrator \
  bound_service_account_namespaces=default \
  policies=atlasia-read \
  ttl=1h
```

### 3. Application Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: atlasia-orchestrator
spec:
  template:
    spec:
      serviceAccountName: atlasia-orchestrator
      containers:
      - name: orchestrator
        image: atlasia/ai-orchestrator:latest
        env:
        - name: SPRING_CLOUD_VAULT_ENABLED
          value: "true"
        - name: SPRING_CLOUD_VAULT_AUTHENTICATION
          value: "KUBERNETES"
        - name: SPRING_CLOUD_VAULT_KUBERNETES_ROLE
          value: "atlasia-orchestrator"
        - name: VAULT_ADDR
          value: "http://vault:8200"
```

## Troubleshooting

### Connection Issues

**Problem**: Cannot connect to Vault

**Solution**:
```bash
# Check Vault status
vault status

# Test connectivity
curl http://localhost:8200/v1/sys/health

# Check application logs
grep -i vault logs/application.log
```

### Authentication Failures

**Problem**: Token expired or invalid

**Solution**:
```bash
# For dev mode, use root token
export VAULT_TOKEN='dev-root-token'

# For production, regenerate secret-id
vault write -f auth/approle/role/atlasia-orchestrator/secret-id
```

### Missing Secrets

**Problem**: Secret not found errors

**Solution**:
```bash
# List all secrets
vault kv list secret/atlasia

# Check specific secret
vault kv get secret/atlasia/jwt-secret

# Re-run initialization
cd infra && ./vault-init.sh
```

### Sealed Vault

**Problem**: Vault is sealed

**Solution**:
```bash
# Check seal status
vault status

# Unseal (requires threshold number of keys)
vault operator unseal <key1>
vault operator unseal <key2>
vault operator unseal <key3>
```

## Security Best Practices

1. **Never commit secrets to Git**
   - Use `.gitignore` for `*.pem`, `.env`, vault tokens
   - Store secrets only in Vault

2. **Use strong authentication in production**
   - AppRole or Kubernetes auth (not root tokens)
   - Short-lived tokens with TTL

3. **Enable TLS for Vault**
   - Use valid SSL certificates
   - Configure trust stores properly

4. **Implement secret rotation**
   - Automated rotation via SecretRotationScheduler
   - Manual rotation for external secrets (OAuth2)

5. **Monitor and audit**
   - Enable audit logging
   - Monitor health via actuator endpoints
   - Alert on Vault seal/failure events

6. **Backup regularly**
   - Snapshot Vault data
   - Store backups securely and encrypted
   - Test restore procedures

7. **Principle of least privilege**
   - Create narrow policies for each application
   - Use different roles for different services
   - Revoke unused tokens

8. **Network security**
   - Restrict Vault access via firewall
   - Use private networks in cloud
   - Enable rate limiting

## References

- [HashiCorp Vault Documentation](https://developer.hashicorp.com/vault/docs)
- [Spring Cloud Vault Reference](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [Vault Best Practices](https://developer.hashicorp.com/vault/tutorials/operations)
- [Secret Management Guide](https://developer.hashicorp.com/vault/tutorials/secrets-management)
