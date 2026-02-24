# Infrastructure Setup

This directory contains infrastructure configuration for the Atlasia AI Orchestrator.

## Services

### PostgreSQL Database
- **Version**: 16
- **Default Port**: 5432
- **Database**: ai
- **Username**: ai
- **Password**: ai (dev mode)

### HashiCorp Vault
HashiCorp Vault is used for secure secrets management in production environments.

## Vault Setup

### Development Mode

For local development and testing, run Vault in dev mode:

```bash
docker run --cap-add=IPC_LOCK -d --name=vault -p 8200:8200 \
  -e 'VAULT_DEV_ROOT_TOKEN_ID=dev-root-token' \
  -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' \
  hashicorp/vault:latest
```

Set environment variables:
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
```

### Initialize Vault Secrets

Enable KV v2 secrets engine (if not already enabled in dev mode):
```bash
vault secrets enable -version=2 -path=secret kv
```

Store secrets in Vault:

#### Orchestrator Token
```bash
vault kv put secret/atlasia orchestrator-token="your-secure-orchestrator-token"
```

#### JWT Signing Key
```bash
# Generate a secure random key
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
vault kv put secret/atlasia jwt-secret="$JWT_SECRET"
```

#### Database Password
```bash
vault kv put secret/atlasia db-password="your-database-password"
```

#### LLM API Keys
```bash
vault kv put secret/atlasia llm-api-key="your-openai-api-key"
vault kv put secret/atlasia llm-fallback-api-key="your-deepseek-api-key"
```

#### GitHub App Configuration
```bash
# Store GitHub App private key path or content
vault kv put secret/atlasia github-private-key-path="/path/to/private-key.pem"
```

#### OAuth2 Client Secrets
```bash
# GitHub OAuth2
vault kv put secret/atlasia oauth2-github-client-id="your-github-client-id"
vault kv put secret/atlasia oauth2-github-client-secret="your-github-client-secret"

# Google OAuth2
vault kv put secret/atlasia oauth2-google-client-id="your-google-client-id"
vault kv put secret/atlasia oauth2-google-client-secret="your-google-client-secret"

# GitLab OAuth2
vault kv put secret/atlasia oauth2-gitlab-client-id="your-gitlab-client-id"
vault kv put secret/atlasia oauth2-gitlab-client-secret="your-gitlab-client-secret"
```

#### Encryption Key
```bash
# Generate a secure encryption key
ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')
vault kv put secret/atlasia encryption-key="$ENCRYPTION_KEY"
```

### Verify Secrets
```bash
vault kv get secret/atlasia
```

### Production Setup

For production environments:

1. **Install Vault**: Follow the [official installation guide](https://developer.hashicorp.com/vault/docs/install)

2. **Initialize Vault**:
```bash
vault operator init -key-shares=5 -key-threshold=3
```
Save the unseal keys and initial root token securely.

3. **Unseal Vault**:
```bash
vault operator unseal <unseal-key-1>
vault operator unseal <unseal-key-2>
vault operator unseal <unseal-key-3>
```

4. **Enable Audit Logging**:
```bash
vault audit enable file file_path=/var/log/vault/audit.log
```

5. **Configure Authentication**:
```bash
# For Kubernetes
vault auth enable kubernetes

# For AppRole
vault auth enable approle
```

6. **Create Policies**:
Create a policy file `atlasia-policy.hcl`:
```hcl
path "secret/data/atlasia/*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/atlasia/*" {
  capabilities = ["list"]
}
```

Apply the policy:
```bash
vault policy write atlasia-read atlasia-policy.hcl
```

7. **Create AppRole for Application**:
```bash
vault write auth/approle/role/atlasia-orchestrator \
  token_policies="atlasia-read" \
  token_ttl=1h \
  token_max_ttl=4h

# Get role-id
vault read auth/approle/role/atlasia-orchestrator/role-id

# Generate secret-id
vault write -f auth/approle/role/atlasia-orchestrator/secret-id
```

### Application Configuration

Configure the application to use Vault by setting environment variables:

```bash
export VAULT_ADDR='http://vault-server:8200'
export VAULT_TOKEN='your-vault-token'
export SPRING_CLOUD_VAULT_ENABLED=true
```

For production, use AppRole authentication:
```bash
export VAULT_ADDR='https://vault-server:8200'
export SPRING_CLOUD_VAULT_ENABLED=true
export SPRING_CLOUD_VAULT_AUTHENTICATION=APPROLE
export SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID='role-id-value'
export SPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID='secret-id-value'
```

### Secret Rotation

The application includes automated secret rotation:

- **JWT Signing Key**: Rotated monthly (1st of each month at 2 AM)
- **OAuth2 Client Secrets**: Rotated quarterly (every 3 months on the 1st at 2 AM)

Manual rotation via Vault CLI:
```bash
# Rotate JWT secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
vault kv put secret/atlasia jwt-secret="$JWT_SECRET"

# Rotate database password
vault kv put secret/atlasia db-password="new-secure-password"
```

### Troubleshooting

#### Connection Issues
```bash
# Check Vault status
vault status

# Check application logs
tail -f logs/application.log | grep Vault
```

#### Health Check
```bash
curl http://localhost:8080/actuator/health
```

The response should include Vault health status when enabled.

#### Common Issues

1. **Vault Sealed**: Unseal Vault using the unseal keys
2. **Token Expired**: Generate a new token or use AppRole authentication
3. **Missing Secrets**: Verify secrets exist in Vault at the correct path
4. **Permission Denied**: Ensure the token/role has appropriate policies

## Docker Compose

To start all infrastructure services including Vault:

```bash
docker compose -f docker-compose.ai.yml up -d
```

### Adding Vault to docker-compose.ai.yml

Add the following service definition:

```yaml
  vault:
    image: hashicorp/vault:latest
    container_name: atlasia-vault
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: dev-root-token
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    cap_add:
      - IPC_LOCK
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 10s
      timeout: 5s
      retries: 5
```

## Monitoring

Vault metrics are exposed via the actuator endpoint when enabled:
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### Grafana Dashboard

Monitor Vault health and secret access patterns using the provided Grafana dashboards.

## Security Best Practices

1. **Never commit secrets to version control**
2. **Use strong, randomly generated tokens and keys**
3. **Rotate secrets regularly** (automated via SecretRotationScheduler)
4. **Use AppRole or Kubernetes auth in production** (not root tokens)
5. **Enable audit logging** in production environments
6. **Use TLS** for Vault communication in production
7. **Restrict network access** to Vault using firewall rules
8. **Backup Vault data** regularly
9. **Monitor Vault access logs** for suspicious activity
10. **Use separate Vault instances** for different environments

## References

- [HashiCorp Vault Documentation](https://developer.hashicorp.com/vault/docs)
- [Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault)
- [Vault Operations Guide](https://developer.hashicorp.com/vault/tutorials/operations)
