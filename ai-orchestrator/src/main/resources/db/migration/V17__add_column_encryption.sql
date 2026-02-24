-- V17: Add encrypted columns for sensitive data
-- This migration adds encrypted versions of sensitive columns

-- OAuth2 accounts: encrypt access tokens and refresh tokens
ALTER TABLE oauth2_accounts 
    ADD COLUMN IF NOT EXISTS access_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS refresh_token_encrypted TEXT;

-- Migrate existing data to encrypted columns (if any)
-- Note: This will be null initially - encryption will happen on first update
UPDATE oauth2_accounts 
    SET access_token_encrypted = access_token,
        refresh_token_encrypted = refresh_token
    WHERE access_token_encrypted IS NULL;

-- Drop old unencrypted columns after migration
-- WARNING: Only run this after verifying encryption is working
-- ALTER TABLE oauth2_accounts DROP COLUMN IF EXISTS access_token;
-- ALTER TABLE oauth2_accounts DROP COLUMN IF EXISTS refresh_token;

-- Users: encrypt MFA secrets
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS mfa_secret_encrypted TEXT;

-- Migrate existing MFA secrets (if any)
UPDATE users 
    SET mfa_secret_encrypted = mfa_secret
    WHERE mfa_secret_encrypted IS NULL AND mfa_secret IS NOT NULL;

-- Drop old unencrypted column after migration
-- WARNING: Only run this after verifying encryption is working
-- ALTER TABLE users DROP COLUMN IF EXISTS mfa_secret;

-- Add comment to track encryption migration
COMMENT ON COLUMN oauth2_accounts.access_token_encrypted IS 'AES-256-GCM encrypted access token (IV prepended, base64 encoded)';
COMMENT ON COLUMN oauth2_accounts.refresh_token_encrypted IS 'AES-256-GCM encrypted refresh token (IV prepended, base64 encoded)';
COMMENT ON COLUMN users.mfa_secret_encrypted IS 'AES-256-GCM encrypted MFA secret (IV prepended, base64 encoded)';

-- Note: refresh_tokens.token_hash is already hashed with SHA-256, no encryption needed
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of refresh token (used for lookup, not reversible)';
