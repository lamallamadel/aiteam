# OAuth2 Quick Start Guide

## Prerequisites

1. Java 17
2. Maven
3. PostgreSQL 16
4. HashiCorp Vault (optional, but recommended for production)
5. OAuth2 applications configured on GitHub, Google, and/or GitLab

## Step 1: Set Up OAuth2 Applications

### GitHub

1. Go to https://github.com/settings/developers
2. Click "New OAuth App"
3. Fill in:
   - Application name: `Atlasia AI Orchestrator`
   - Homepage URL: `http://localhost:8080`
   - Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Save the **Client ID** and **Client Secret**

### Google

1. Go to https://console.cloud.google.com/apis/credentials
2. Create a new OAuth 2.0 Client ID (Web application)
3. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Save the **Client ID** and **Client Secret**

### GitLab

1. Go to https://gitlab.com/-/profile/applications
2. Create a new application
3. Add redirect URI: `http://localhost:8080/login/oauth2/code/gitlab`
4. Select scopes: `read_user`, `email`
5. Save the **Application ID** and **Secret**

## Step 2: Set Up Vault (Recommended)

### Start Vault (Docker)

```bash
docker run --cap-add=IPC_LOCK -d --name=vault -p 8200:8200 vault:latest
```

### Initialize and Unseal Vault

```bash
# Get root token
docker logs vault

# Set Vault address
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='<root-token-from-logs>'
```

### Store OAuth2 Credentials

```bash
# Generate encryption key
ENCRYPTION_KEY=$(openssl rand -base64 32)

# Store secrets
vault kv put secret/atlasia \
  oauth2.github.client-id="<github-client-id>" \
  oauth2.github.client-secret="<github-client-secret>" \
  oauth2.google.client-id="<google-client-id>" \
  oauth2.google.client-secret="<google-client-secret>" \
  oauth2.gitlab.client-id="<gitlab-client-id>" \
  oauth2.gitlab.client-secret="<gitlab-client-secret>" \
  encryption.key="$ENCRYPTION_KEY"
```

## Step 3: Configure Environment Variables

Create a `.env` file in the `ai-orchestrator` directory:

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/ai
DB_USER=ai
DB_PASSWORD=ai

# JWT
JWT_SECRET_KEY=<base64-encoded-secret-key>

# Vault (if using Vault)
VAULT_ENABLED=true
VAULT_URI=http://localhost:8200
VAULT_TOKEN=<vault-token>

# OAuth2 Frontend Callback
OAUTH2_FRONTEND_CALLBACK_URL=http://localhost:4200/auth/callback
```

### Alternative: Without Vault

If not using Vault, set these additional environment variables:

```bash
VAULT_OAUTH2_GITHUB_CLIENT_ID=<github-client-id>
VAULT_OAUTH2_GITHUB_CLIENT_SECRET=<github-client-secret>
VAULT_OAUTH2_GOOGLE_CLIENT_ID=<google-client-id>
VAULT_OAUTH2_GOOGLE_CLIENT_SECRET=<google-client-secret>
VAULT_OAUTH2_GITLAB_CLIENT_ID=<gitlab-client-id>
VAULT_OAUTH2_GITLAB_CLIENT_SECRET=<gitlab-client-secret>
VAULT_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

## Step 4: Run Database Migrations

The OAuth2 tables are already created by migration `V14__create_auth_tables.sql`.

Verify the migration:

```bash
cd ai-orchestrator
mvn flyway:info
```

## Step 5: Build and Run

```bash
cd ai-orchestrator
mvn clean install
mvn spring-boot:run
```

## Step 6: Test OAuth2 Login

### Browser Test

1. Open browser and navigate to:
   - GitHub: `http://localhost:8080/oauth2/authorization/github`
   - Google: `http://localhost:8080/oauth2/authorization/google`
   - GitLab: `http://localhost:8080/oauth2/authorization/gitlab`

2. Authorize the application

3. You should be redirected to:
   ```
   http://localhost:4200/auth/callback?token=<jwt-token>&refreshToken=<refresh-token>
   ```

### cURL Test

Extract the token from the redirect and test API access:

```bash
# Use the token from the redirect
TOKEN="<jwt-token>"

# Test authenticated endpoint
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/runs
```

## Step 7: Link Additional OAuth2 Accounts

After logging in with one provider, you can link additional providers:

```bash
TOKEN="<jwt-token>"

curl -X POST http://localhost:8080/api/auth/oauth2/link \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "google",
    "providerUserId": "123456789",
    "accessToken": "oauth2-access-token",
    "refreshToken": "oauth2-refresh-token"
  }'
```

## Verification

### Check Database

```sql
-- Connect to PostgreSQL
psql -U ai -d ai

-- View OAuth2 accounts
SELECT id, user_id, provider, provider_user_id, created_at 
FROM oauth2_accounts;

-- View users created via OAuth2
SELECT id, username, email, created_at 
FROM users 
WHERE id IN (SELECT user_id FROM oauth2_accounts);
```

### Check Logs

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.atlasia.ai.config.OAuth2LoginSuccessHandler: DEBUG
    org.springframework.security.oauth2: DEBUG
```

## Troubleshooting

### Vault Connection Failed

```
Error: Unable to connect to Vault
```

**Solution:**
- Verify Vault is running: `docker ps | grep vault`
- Check `VAULT_ENABLED=true` and `VAULT_URI=http://localhost:8200`
- Verify `VAULT_TOKEN` is correct

### Encryption Failed

```
Error: Encryption key not configured
```

**Solution:**
- Ensure encryption key is stored in Vault or environment variable
- Key must be base64-encoded 32-byte value
- Generate with: `openssl rand -base64 32`

### OAuth2 Redirect Mismatch

```
Error: redirect_uri_mismatch
```

**Solution:**
- Verify redirect URI in OAuth2 provider settings matches exactly:
  - GitHub: `http://localhost:8080/login/oauth2/code/github`
  - Google: `http://localhost:8080/login/oauth2/code/google`
  - GitLab: `http://localhost:8080/login/oauth2/code/gitlab`

### Missing User Email

```
Error: Invalid OAuth2 user data
```

**Solution:**
- Ensure OAuth2 scopes include email
- GitHub: `user:email`
- Google: `email`, `profile`
- GitLab: `email`, `read_user`

## Production Deployment

For production:

1. **Use HTTPS**: Update redirect URIs to use `https://`
2. **Vault**: Always use Vault for credential storage
3. **Encryption Key**: Rotate encryption key periodically
4. **Frontend URL**: Update `OAUTH2_FRONTEND_CALLBACK_URL` to production frontend URL
5. **Rate Limiting**: Add rate limiting to OAuth2 endpoints
6. **Monitoring**: Enable audit logging for OAuth2 operations

## Next Steps

- Read `OAUTH2_SETUP.md` for detailed configuration
- Read `OAUTH2_IMPLEMENTATION_SUMMARY.md` for technical details
- Configure additional OAuth2 providers as needed
- Set up monitoring and alerting for OAuth2 operations
