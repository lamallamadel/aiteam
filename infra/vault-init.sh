#!/bin/bash

# Vault initialization script for Atlasia AI Orchestrator
# This script sets up all required secrets in Vault for local development

set -e

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"

echo "=== Atlasia Vault Initialization ==="
echo "Vault Address: $VAULT_ADDR"
echo ""

# Set environment for vault CLI
export VAULT_ADDR
export VAULT_TOKEN

# Check Vault status
echo "Checking Vault status..."
if ! vault status > /dev/null 2>&1; then
    echo "Error: Cannot connect to Vault at $VAULT_ADDR"
    echo "Please ensure Vault is running:"
    echo "  docker run --cap-add=IPC_LOCK -d --name=vault -p 8200:8200 \\"
    echo "    -e 'VAULT_DEV_ROOT_TOKEN_ID=dev-root-token' \\"
    echo "    -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' \\"
    echo "    hashicorp/vault:latest"
    exit 1
fi

echo "✓ Vault is accessible"
echo ""

# Enable KV v2 secrets engine (might already be enabled in dev mode)
echo "Enabling KV v2 secrets engine..."
vault secrets enable -version=2 -path=secret kv 2>/dev/null || echo "✓ KV secrets engine already enabled"
echo ""

# Generate secure secrets
echo "Generating secure secrets..."
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
ORCHESTRATOR_TOKEN=$(openssl rand -hex 32)
ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')
DB_PASSWORD="ai-dev-$(openssl rand -hex 8)"

echo "✓ Generated secure random secrets"
echo ""

# Store secrets in Vault
echo "Storing secrets in Vault..."

# Orchestrator token
vault kv put secret/atlasia orchestrator-token="$ORCHESTRATOR_TOKEN"
echo "✓ Stored orchestrator-token"

# JWT signing key
vault kv put secret/atlasia jwt-secret="$JWT_SECRET"
echo "✓ Stored jwt-secret"

# Database password
vault kv put secret/atlasia db-password="$DB_PASSWORD"
echo "✓ Stored db-password"

# Encryption key
vault kv put secret/atlasia encryption-key="$ENCRYPTION_KEY"
echo "✓ Stored encryption-key"

# LLM API keys (placeholders - update with real keys)
vault kv put secret/atlasia llm-api-key="your-openai-api-key-here"
echo "✓ Stored llm-api-key (placeholder)"

vault kv put secret/atlasia llm-fallback-api-key="your-deepseek-api-key-here"
echo "✓ Stored llm-fallback-api-key (placeholder)"

# GitHub App configuration (placeholders)
vault kv put secret/atlasia github-private-key-path="/path/to/private-key.pem"
echo "✓ Stored github-private-key-path (placeholder)"

# OAuth2 Client Secrets (placeholders - update with real values from OAuth providers)
vault kv put secret/atlasia oauth2-github-client-id="your-github-client-id"
vault kv put secret/atlasia oauth2-github-client-secret="your-github-client-secret"
echo "✓ Stored GitHub OAuth2 credentials (placeholders)"

vault kv put secret/atlasia oauth2-google-client-id="your-google-client-id"
vault kv put secret/atlasia oauth2-google-client-secret="your-google-client-secret"
echo "✓ Stored Google OAuth2 credentials (placeholders)"

vault kv put secret/atlasia oauth2-gitlab-client-id="your-gitlab-client-id"
vault kv put secret/atlasia oauth2-gitlab-client-secret="your-gitlab-client-secret"
echo "✓ Stored GitLab OAuth2 credentials (placeholders)"

echo ""
echo "=== Vault Initialization Complete ==="
echo ""
echo "Important Generated Secrets:"
echo "----------------------------"
echo "Orchestrator Token: $ORCHESTRATOR_TOKEN"
echo "Database Password: $DB_PASSWORD"
echo ""
echo "IMPORTANT: Save these values securely!"
echo ""
echo "To verify secrets:"
echo "  vault kv get secret/atlasia"
echo ""
echo "To use Vault with the application, set:"
echo "  export VAULT_ADDR='$VAULT_ADDR'"
echo "  export VAULT_TOKEN='$VAULT_TOKEN'"
echo "  export SPRING_CLOUD_VAULT_ENABLED=true"
echo ""
echo "Note: OAuth2 and LLM API keys are set to placeholders."
echo "Update them with real values using:"
echo "  vault kv put secret/atlasia llm-api-key='your-actual-key'"
echo ""
