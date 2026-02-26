# Production Monitoring Quick Start

## 1. Set Environment Variables

```bash
# Required variables
export GRAFANA_ADMIN_PASSWORD="<strong-password>"
export DB_PASSWORD="<database-password>"
export DOMAIN="your-domain.com"

# Optional (defaults shown)
export DB_USER="aiteam"
export DB_NAME="aiteam"
export GRAFANA_ADMIN_USER="admin"
```

## 2. Start the Monitoring Stack

```bash
cd infra/deployments/prod
docker-compose -f docker-compose-monitoring.yml up -d
```

## 3. Verify Services

```bash
# Check all services are running
docker-compose -f docker-compose-monitoring.yml ps

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# Check Grafana is up
curl -I http://localhost:3001
```

## 4. Access Dashboards

- **Grafana**: http://localhost:3001
  - Login: admin / ${GRAFANA_ADMIN_PASSWORD}
  - Navigate to Dashboards → AI Orchestrator Production folder
  
- **Prometheus**: http://localhost:9090
  - Query explorer and targets health
  
- **Alertmanager**: http://localhost:9093
  - Active alerts and silences

## 5. Pre-configured Dashboards

All dashboards auto-load on first start:

1. **JVM Metrics** - Heap, GC, threads
2. **HTTP Metrics** - Request rates, latencies, errors
3. **Database Pool** - Connection statistics, PostgreSQL health
4. **System Resources** - CPU, memory, disk, network

## 6. Test Alerting (Optional)

```bash
# Trigger a test alert by stopping the orchestrator
docker-compose -f docker-compose.prod.yml stop ai-orchestrator

# Wait 2 minutes, then check Alertmanager
curl http://localhost:9093/api/v2/alerts | jq '.[] | {alertname: .labels.alertname, status: .status.state}'

# Restart the orchestrator
docker-compose -f docker-compose.prod.yml start ai-orchestrator
```

## 7. Configure Notifications (Optional)

Edit `alertmanager-prod.yml` to add:

### Slack
```yaml
slack_configs:
  - api_url: '${SLACK_WEBHOOK_URL}'
    channel: '#alerts'
```

### Email
Update the `global` section:
```yaml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@example.com'
  smtp_auth_username: 'alerts@example.com'
  smtp_auth_password: '${SMTP_PASSWORD}'
```

Then reload:
```bash
docker-compose -f docker-compose-monitoring.yml restart alertmanager
```

## 8. Maintenance

### View Logs
```bash
docker-compose -f docker-compose-monitoring.yml logs -f prometheus
docker-compose -f docker-compose-monitoring.yml logs -f grafana
```

### Stop Monitoring
```bash
docker-compose -f docker-compose-monitoring.yml down
```

### Restart After Config Changes
```bash
# Reload Prometheus config without restart
curl -X POST http://localhost:9090/-/reload

# Or full restart
docker-compose -f docker-compose-monitoring.yml restart
```

## Troubleshooting

**Prometheus not scraping targets?**
- Check docker network: `docker network inspect ai_prod_network`
- Verify target ports are exposed: `docker ps`

**Grafana dashboards empty?**
- Verify Prometheus datasource: Grafana → Configuration → Data Sources
- Test query in Prometheus: http://localhost:9090/graph

**PostgreSQL exporter failing?**
- Check credentials match main database
- Verify database is accessible: `docker-compose -f docker-compose.prod.yml ps ai-db`

For complete documentation, see [README-MONITORING.md](./README-MONITORING.md)
