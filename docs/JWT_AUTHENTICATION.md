# JWT Authentication System

## Overview

The Atlasia AI Orchestrator implements a robust JWT-based authentication system with refresh token rotation, role-based access control (RBAC), and fine-grained permissions.

## Architecture

### Components

1. **JwtService**: Generates and validates JWT tokens using HS512 algorithm
2. **RefreshTokenService**: Manages refresh token lifecycle with rotation
3. **UserDetailsServiceImpl**: Loads user details with roles and permissions
4. **AuthenticationService**: Orchestrates the authentication flow

### Token Types

- **Access Token**: Short-lived (15 minutes default), contains user info, roles, and permissions
- **Refresh Token**: Long-lived (7 days default), used to obtain new access tokens

## Configuration

### Environment Variables

```bash
JWT_SECRET_KEY=<256-bit-secret-key>
JWT_ACCESS_TOKEN_EXPIRATION_MINUTES=15
JWT_REFRESH_TOKEN_EXPIRATION_DAYS=7
JWT_ISSUER=atlasia-ai-orchestrator
```

### Vault Integration

For production, JWT secrets should be stored in HashiCorp Vault:

```bash
# Write secret to Vault
vault kv put secret/atlasia/jwt secret-key=<generated-256-bit-key>

# Spring Cloud Vault will automatically read from:
# ${spring.cloud.vault.kv.backend}/atlasia/jwt/secret-key
```

### Generating a Secure Secret Key

```bash
# Generate a 256-bit (32 bytes) base64-encoded secret
openssl rand -base64 32
```

## Database Schema

### Users Table
- `id` (UUID): Primary key
- `username` (VARCHAR): Unique username
- `email` (VARCHAR): Unique email
- `password_hash` (VARCHAR): BCrypt hashed password
- `enabled` (BOOLEAN): Account enabled flag
- `locked` (BOOLEAN): Account locked flag
- `mfa_secret` (VARCHAR): TOTP secret for MFA
- `created_at`, `updated_at` (TIMESTAMP)

### Roles Table
- `id` (SERIAL): Primary key
- `name` (VARCHAR): Unique role name
- `description` (TEXT)

### Permissions Table
- `id` (SERIAL): Primary key
- `resource` (VARCHAR): Resource identifier
- `action` (VARCHAR): Action on resource
- `description` (TEXT)
- Unique constraint on (`resource`, `action`)

### Refresh Tokens Table
- `id` (UUID): Primary key
- `user_id` (UUID): Foreign key to users
- `token_hash` (VARCHAR): Hashed refresh token
- `expires_at` (TIMESTAMP): Expiration time
- `revoked` (BOOLEAN): Revoked flag
- `device_info` (TEXT): Device information
- `created_at` (TIMESTAMP)

## API Usage

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password123",
  "deviceInfo": "Chrome/Linux"
}
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMi...",
  "refreshToken": "eyJhbGciOiJIUzUxMi...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Refresh Token

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzUxMi..."
}
```

### Logout

```http
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzUxMi..."
}
```

### Using Access Token

```http
GET /api/protected-resource
Authorization: Bearer eyJhbGciOiJIUzUxMi...
```

## Token Claims

### Access Token Claims

```json
{
  "sub": "username",
  "userId": "uuid",
  "username": "username",
  "email": "user@example.com",
  "roles": ["USER", "ADMIN"],
  "permissions": ["runs:read", "runs:write", "users:read"],
  "iss": "atlasia-ai-orchestrator",
  "iat": 1234567890,
  "exp": 1234568790
}
```

### Refresh Token Claims

```json
{
  "sub": "username",
  "userId": "uuid",
  "type": "refresh",
  "iss": "atlasia-ai-orchestrator",
  "iat": 1234567890,
  "exp": 1235172690,
  "jti": "unique-token-id"
}
```

## Security Features

### Password Hashing
- BCrypt with strength 12
- Passwords are never stored in plain text

### Token Security
- Tokens signed with HS512 algorithm
- Refresh tokens are hashed before storage
- Automatic token rotation on refresh
- Old refresh tokens are immediately revoked

### Account Protection
- Account locking mechanism
- Account enable/disable flags
- Multi-factor authentication support (MFA secret field)

### Token Cleanup
- Scheduled task runs daily at 2 AM
- Removes expired refresh tokens from database
- Reduces database bloat

## Role-Based Access Control (RBAC)

### Permission Format
Permissions follow the format: `resource:action`

Examples:
- `runs:read` - Read workflow runs
- `runs:write` - Create/update workflow runs
- `users:admin` - Administer users

### Role Hierarchy
Roles contain a set of permissions. Users can have:
1. Direct role assignments (via `user_roles` table)
2. Direct permission overrides (via `user_permissions` table)

### Example Setup

```sql
-- Create roles
INSERT INTO roles (name, description) 
VALUES ('ADMIN', 'System administrator'),
       ('USER', 'Regular user');

-- Create permissions
INSERT INTO permissions (resource, action, description)
VALUES ('runs', 'read', 'Read workflow runs'),
       ('runs', 'write', 'Create/update workflow runs'),
       ('users', 'admin', 'Administer users');

-- Assign permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.name = 'USER' AND p.resource = 'runs' AND p.action = 'read';

-- Assign role to user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';
```

## Best Practices

### For Development
1. Use a simple secret key in `.env` file
2. Access token expiry can be longer (e.g., 60 minutes)
3. Refresh token expiry should still be reasonable (e.g., 7 days)

### For Production
1. Store JWT secret in HashiCorp Vault
2. Use a cryptographically secure 256-bit secret
3. Keep access token expiry short (15 minutes)
4. Enable HTTPS/TLS for all API endpoints
5. Implement rate limiting on authentication endpoints
6. Monitor for suspicious authentication patterns
7. Rotate JWT secrets periodically

### Token Rotation
- Refresh tokens are single-use
- Each refresh operation revokes the old token and issues a new pair
- Prevents token replay attacks
- Clients must store the new refresh token after each rotation

## Troubleshooting

### "JWT secret key must be configured"
Ensure `JWT_SECRET_KEY` environment variable is set and not empty.

### "Invalid JWT signature"
Token was signed with a different secret key. Verify the secret key configuration.

### "JWT token is expired"
Access token has expired. Use the refresh token to obtain a new access token.

### "Refresh token not found or invalid"
Refresh token was revoked, expired, or never existed. User must log in again.

### "Account is disabled" / "Account is locked"
User account has been disabled or locked. Contact an administrator.
