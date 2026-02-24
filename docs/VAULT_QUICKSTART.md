# Vault Quick Start Guide

Get up and running with HashiCorp Vault in 5 minutes.

## 1. Start Vault (Dev Mode)

```bash
docker run --cap-add=IPC_LOCK -d --name=vault -p 8200:8200 \
  -e 'VAULT_DEV_ROOT_TOKEN_ID=dev-root-token' \
  -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' \
  hashicorp/vault:latest
```

## 2. Initialize Secrets

```bash
cd infra
chmod +x vault-init.sh
./vault-init.sh
```

**Save the generated tokens** displayed at the end!

## 3. Configure Application

```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
export SPRING_CLOUD_VAULT_ENABLED=true
```

Or create `.env` file:
```properties
VAULT_ADDR=http://localhost:8200
VAULT_TOKEN=dev-root-token
SPRING_CLOUD_VAULT_ENABLED=true
```

## 4. Start Application

```bash
cd ai-orchestrator
mvn spring-boot:run
```

## 5. Verify

```bash
# Check Vault health
curl http://localhost:8080/actuator/health | jq '.components.vault'

# List secrets
vault kv list secret/atlasia
```

## Common Commands

```bash
# Read a secret
vault kv get secret/atlasia/jwt-secret

# Update a secret
vault kv put secret/atlasia/llm-api-key="your-real-api-key"

# View all secrets
vault kv get secret/atlasia

# Delete Vault container (clean start)
docker rm -f vault
```

## Troubleshooting

**Can't connect to Vault?**
- Check: `docker ps | grep vault`
- Logs: `docker logs vault`

**Secrets not loading?**
- Verify: `export SPRING_CLOUD_VAULT_ENABLED=true`
- Check: Application logs for Vault connection errors

**Need fresh start?**
```bash
docker rm -f vault
# Then repeat steps 1-4
```

## Next Steps

- Full documentation: [docs/VAULT_SETUP.md](VAULT_SETUP.md)
- Infrastructure guide: [infra/README.md](../infra/README.md)
- Update placeholder secrets with real values
