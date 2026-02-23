# OAuth2 Integration Setup

## Overview

The Atlasia AI Orchestrator supports OAuth2 authentication with GitHub, Google, and GitLab. OAuth2 credentials are securely stored in HashiCorp Vault, and OAuth2 tokens are encrypted using AES-256-GCM encryption.

## Architecture

- **OAuth2 Providers**: GitHub, Google, GitLab
- **Authentication Flow**: Authorization Code Grant
- **Token Storage**: OAuth2 access and refresh tokens are encrypted in the database using AES-256-GCM
- **Credential Source**: OAuth2 client credentials (client-id, client-secret) are sourced from Vault
- **Success Handler**: Custom `OAuth2LoginSuccessHandler` generates JWT tokens and redirects to frontend

## Vault Configuration

### Required Vault Secrets

Store the following secrets in Vault at path `secret/atlasia`:

```bash
# GitHub OAuth2
vault kv put secret/atlasia oauth2.github.client-id="<github-client-id>"
vault kv put secret/atlasia oauth2.github.client-secret="<github-client-secret>"

# Google OAuth2
vault kv put secret/atlasia oauth2.google.client-id="<google-client-id>"
vault kv put secret/atlasia oauth2.google.client-secret="<google-client-secret>"

# GitLab OAuth2
vault kv put secret/atlasia oauth2.gitlab.client-id="<gitlab-client-id>"
vault kv put secret/atlasia oauth2.gitlab.client-secret="<gitlab-client-secret>"

# Encryption Key (32 bytes base64-encoded for AES-256)
vault kv put secret/atlasia encryption.key="<base64-encoded-32-byte-key>"
```

### Generate Encryption Key

```bash
# Generate a 32-byte (256-bit) random key and base64 encode it
openssl rand -base64 32
```

### Environment Variables

Enable Vault integration with these environment variables:

```bash
VAULT_ENABLED=true
VAULT_URI=http://localhost:8200
VAULT_TOKEN=<vault-token>
```

## OAuth2 Provider Setup

### GitHub

1. Go to GitHub Settings → Developer Settings → OAuth Apps
2. Create a new OAuth App
3. Set Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Copy Client ID and Client Secret to Vault

### Google

1. Go to Google Cloud Console → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID (Web application)
3. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Copy Client ID and Client Secret to Vault

### GitLab

1. Go to GitLab → User Settings → Applications
2. Create a new application
3. Set Redirect URI: `http://localhost:8080/login/oauth2/code/gitlab`
4. Select scopes: `read_user`, `email`
5. Copy Application ID and Secret to Vault

## Authentication Flow

### OAuth2 Login Flow

1. User initiates OAuth2 login by visiting: `/oauth2/authorization/{provider}` (e.g., `/oauth2/authorization/github`)
2. User is redirected to OAuth2 provider for authorization
3. After authorization, provider redirects back to `/login/oauth2/code/{provider}`
4. `OAuth2LoginSuccessHandler` processes the callback:
   - Checks if user exists by `provider_user_id`
   - Creates new user if not found
   - Links OAuth2 account to user
   - Generates JWT access and refresh tokens
   - Redirects to frontend: `http://localhost:4200/auth/callback?token=<jwt>&refreshToken=<refresh>`

### Linking Additional OAuth2 Accounts

Authenticated users can link additional OAuth2 providers using the API:

```bash
POST /api/auth/oauth2/link
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "provider": "github",
  "providerUserId": "12345678",
  "accessToken": "oauth2-access-token",
  "refreshToken": "oauth2-refresh-token"
}
```

Response:
```json
{
  "message": "OAuth2 account linked successfully",
  "provider": "github",
  "success": true
}
```

## Database Schema

### oauth2_accounts Table

```sql
CREATE TABLE oauth2_accounts (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id),
    CONSTRAINT fk_oauth2_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

## Security Features

### Token Encryption

OAuth2 access and refresh tokens are encrypted before storage using:
- **Algorithm**: AES-256-GCM
- **IV**: 12-byte random IV per encryption
- **Key**: 256-bit key from Vault (`atlasia.encryption.key`)
- **Implementation**: `EncryptedStringConverter` JPA attribute converter

### OAuth2 User Creation

When a user logs in via OAuth2 for the first time:
- Email is extracted from OAuth2 user info
- If user with email exists, OAuth2 account is linked
- If not, new user is created with:
  - Username: derived from name or email
  - Password: random UUID (user cannot login with password)
  - Email: from OAuth2 provider
  - OAuth2 account link is created

## Configuration Reference

### application.yml

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${atlasia.oauth2.github.client-id:}
            client-secret: ${atlasia.oauth2.github.client-secret:}
            scope:
              - user:email
              - read:user
          google:
            client-id: ${atlasia.oauth2.google.client-id:}
            client-secret: ${atlasia.oauth2.google.client-secret:}
            scope:
              - email
              - profile
          gitlab:
            client-id: ${atlasia.oauth2.gitlab.client-id:}
            client-secret: ${atlasia.oauth2.gitlab.client-secret:}
            scope:
              - read_user
              - email

atlasia:
  oauth2:
    github:
      client-id: ${VAULT_OAUTH2_GITHUB_CLIENT_ID:}
      client-secret: ${VAULT_OAUTH2_GITHUB_CLIENT_SECRET:}
    google:
      client-id: ${VAULT_OAUTH2_GOOGLE_CLIENT_ID:}
      client-secret: ${VAULT_OAUTH2_GOOGLE_CLIENT_SECRET:}
    gitlab:
      client-id: ${VAULT_OAUTH2_GITLAB_CLIENT_ID:}
      client-secret: ${VAULT_OAUTH2_GITLAB_CLIENT_SECRET:}
    frontend-callback-url: ${OAUTH2_FRONTEND_CALLBACK_URL:http://localhost:4200/auth/callback}
  encryption:
    key: ${VAULT_ENCRYPTION_KEY:}
```

## Testing

### Test OAuth2 Login

1. Start the application with Vault enabled
2. Navigate to: `http://localhost:8080/oauth2/authorization/github`
3. Authorize the application
4. Verify redirect to frontend with JWT token

### Test OAuth2 Link

1. Authenticate with JWT token
2. Send POST request to `/api/auth/oauth2/link` with OAuth2 account details
3. Verify account is linked in database

## Troubleshooting

### Common Issues

1. **Vault connection failed**: Check `VAULT_ENABLED`, `VAULT_URI`, and `VAULT_TOKEN` environment variables
2. **Encryption failed**: Verify `atlasia.encryption.key` is a valid base64-encoded 32-byte key
3. **OAuth2 redirect mismatch**: Ensure redirect URIs in provider configuration match application URLs
4. **Missing user info**: Check OAuth2 scopes include email and profile/user information

### Logs

Enable debug logging for OAuth2:
```yaml
logging:
  level:
    org.springframework.security.oauth2: DEBUG
    com.atlasia.ai.config.OAuth2LoginSuccessHandler: DEBUG
```
