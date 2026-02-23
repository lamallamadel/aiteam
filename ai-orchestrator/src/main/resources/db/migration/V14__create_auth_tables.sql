-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    mfa_secret VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT true,
    locked BOOLEAN NOT NULL DEFAULT false
);

-- Create roles table
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- Create permissions table
CREATE TABLE permissions (
    id SERIAL PRIMARY KEY,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    description TEXT,
    UNIQUE(resource, action)
);

-- Create user_roles junction table
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Create user_permissions junction table for attribute-based overrides
CREATE TABLE user_permissions (
    user_id UUID NOT NULL,
    permission_id INTEGER NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Create refresh_tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    device_info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create oauth2_accounts table
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

-- Create indexes for users table
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_enabled ON users(enabled);
CREATE INDEX idx_users_locked ON users(locked);

-- Create indexes for refresh_tokens table
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- Create indexes for oauth2_accounts table
CREATE INDEX idx_oauth2_accounts_user_id ON oauth2_accounts(user_id);
CREATE INDEX idx_oauth2_accounts_provider ON oauth2_accounts(provider);
CREATE INDEX idx_oauth2_accounts_provider_user_id ON oauth2_accounts(provider, provider_user_id);

-- Create indexes for junction tables
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_permissions_permission_id ON user_permissions(permission_id);

-- Add comments for documentation
COMMENT ON TABLE users IS 'User accounts for authentication and authorization';
COMMENT ON TABLE roles IS 'Role definitions for role-based access control (RBAC)';
COMMENT ON TABLE permissions IS 'Permission definitions for fine-grained access control';
COMMENT ON TABLE user_roles IS 'Junction table mapping users to roles';
COMMENT ON TABLE user_permissions IS 'Junction table for user-specific permission overrides';
COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for JWT authentication';
COMMENT ON TABLE oauth2_accounts IS 'OAuth2 provider account linkages';

COMMENT ON COLUMN users.mfa_secret IS 'TOTP secret for multi-factor authentication';
COMMENT ON COLUMN users.enabled IS 'Whether the user account is enabled';
COMMENT ON COLUMN users.locked IS 'Whether the user account is locked (e.g., due to failed login attempts)';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Hashed refresh token for security';
COMMENT ON COLUMN refresh_tokens.device_info IS 'Information about the device that requested the token';
COMMENT ON COLUMN oauth2_accounts.access_token_encrypted IS 'Encrypted OAuth2 access token';
COMMENT ON COLUMN oauth2_accounts.refresh_token_encrypted IS 'Encrypted OAuth2 refresh token';
