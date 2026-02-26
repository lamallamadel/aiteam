# Centralized Logging Quick Start

## Overview

Atlasia uses **Loki + Promtail** for centralized log aggregation with structured JSON logging.

## Architecture

```
AI Orchestrator (JSON) → Promtail → Loki → Grafana
                                      ↓
                                  Alertmanager
```

## Quick Start

### 1. Start the Logging Stack

```bash
cd infra/deployments/prod

# Start all monitoring services (includes Loki + Promtail)
docker-compose -f docker-compose-monitoring.yml up -d loki promtail

# Verify services
docker-compose -f docker-compose-monitoring.yml ps
```

### 2. Check Logs Are Flowing

```bash
# Check Promtail is scraping
curl http://localhost:9080/targets | jq

# Check Loki is receiving logs
curl http://localhost:3100/loki/api/v1/labels | jq

# Check recent logs
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="ai-orchestrator"}' \
  | jq
```

### 3. View Logs in Grafana

1. Open Grafana: http://localhost:3001
2. Navigate to **Explore** (compass icon)
3. Select **Loki** data source
4. Enter query: `{service="ai-orchestrator"}`
5. Or open **Logs Overview** dashboard

## Common Queries

### All Errors
```logql
{service="ai-orchestrator"} | json | level="ERROR"
```

### Authentication Failures
```logql
{service="ai-orchestrator"} |= "authentication" | json | level=~"WARN|ERROR"
```

### Track a Workflow Run
```logql
{service="ai-orchestrator"} | json | runId="YOUR-RUN-ID"
```

### User Activity
```logql
{service="ai-orchestrator"} | json | userId="user@example.com"
```

### Database Errors
```logql
{service="ai-orchestrator"} |~ "database|connection" | json | level="ERROR"
```

### Vault Issues
```logql
{service="ai-orchestrator"} |~ "vault" | json | level=~"WARN|ERROR"
```

## Log Retention

| Category | Retention | Label |
|----------|-----------|-------|
| INFO logs | 7 days | `retention="info"` |
| WARN/ERROR | 30 days | `retention="error_warn"` |
| Security | 90 days | `retention="security"` |

## Alerts

Log-based alerts are configured in:
- `config/loki-rules.yml` (Loki ruler)
- `../../monitoring/alerts.yml` (Prometheus alerts)

### Critical Alerts

- **DatabaseConnectionFailuresHigh**: >5 DB errors/sec for 2min
- **VaultConnectivityLoss**: Any Vault errors for 2min
- **UnhandledExceptionsHigh**: >10 exceptions/sec for 3min

### Security Alerts

- **AuthenticationFailuresSpike**: >2 auth failures/sec for 5min
- **RateLimitViolationsHigh**: >5 rate limit violations/sec for 5min

## Troubleshooting

### No Logs in Loki

```bash
# 1. Check Promtail logs
docker logs promtail-prod --tail 50

# 2. Verify Promtail → Loki connectivity
docker exec promtail-prod wget -O- http://loki-prod:3100/ready

# 3. Check app is producing JSON
docker logs ai-orchestrator | head -1 | jq .

# 4. Restart Promtail
docker-compose -f docker-compose-monitoring.yml restart promtail
```

### Loki Out of Disk Space

```bash
# Check disk usage
docker exec loki-prod du -sh /loki/*

# Trigger manual compaction
curl -X POST http://localhost:3100/loki/api/v1/compact

# Reduce retention (edit config/loki-config.yml)
# retention_period: 30d  # from 90d
docker-compose -f docker-compose-monitoring.yml restart loki
```

### Slow Queries

- Always use label filters: `{service="ai-orchestrator"}`
- Limit time range: `[5m]` instead of `[24h]`
- Add filters early: `| json | level="ERROR"` before regex

## Configuration Files

- **Application**: `ai-orchestrator/src/main/resources/logback-spring.xml`
- **Loki**: `config/loki-config.yml`
- **Promtail**: `config/promtail-config.yml`
- **Grafana Datasource**: `config/datasources-loki.yml`
- **Dashboard**: `config/dashboard-logs-overview.json`
- **Alerts**: `config/loki-rules.yml`

## Performance Tips

### Promtail
- Batch size: 1MB (configured)
- Position sync: 10s (configured)

### Loki
- Max query lookback: 30 days
- Ingestion rate: 10MB/s
- Compaction: Every 10 minutes

### Query Optimization
```logql
# BAD - scans everything
{} |= "error"

# GOOD - uses label index
{service="ai-orchestrator"} | json | level="ERROR" [5m]
```

## Security

- No built-in auth - use reverse proxy
- TLS recommended for production
- Never log secrets, tokens, or passwords
- Logs may contain PII - implement retention

## Resources

- Full docs: `docs/OBSERVABILITY.md`
- Loki docs: https://grafana.com/docs/loki/latest/
- LogQL reference: https://grafana.com/docs/loki/latest/logql/

## Support

For issues or questions:
1. Check `docs/OBSERVABILITY.md`
2. View container logs: `docker logs <container>`
3. Check Loki health: `curl http://localhost:3100/ready`
