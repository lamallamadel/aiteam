# Production Monitoring Stack

Complete production monitoring setup for AI Orchestrator using Prometheus, Grafana, Loki, Promtail, Jaeger, Node Exporter, and PostgreSQL Exporter.

## Architecture

The monitoring stack includes:

- **Prometheus**: Time-series database and metrics collector
- **Grafana**: Unified visualization platform for metrics, logs, and traces
- **Loki**: Centralized log aggregation system
- **Promtail**: Log collection agent for Loki
- **Jaeger**: Distributed tracing backend
- **Node Exporter**: System-level metrics (CPU, memory, disk, network)
- **PostgreSQL Exporter**: Database metrics and connection pool statistics
- **Alertmanager**: Alert routing and notification management

## Quick Start

### Prerequisites

1. Ensure the main application stack is running (`docker-compose.prod.yml`)
2. Set required environment variables:
   ```bash
   export GRAFANA_ADMIN_PASSWORD=<secure-password>
   export DOMAIN=<your-domain.com>
   export DB_PASSWORD=<database-password>
   export DB_USER=aiteam
   export DB_NAME=aiteam
   ```

### Start Monitoring Stack

```bash
cd infra/deployments/prod
docker-compose -f docker-compose-monitoring.yml up -d
```

### Access Dashboards

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (or https://${DOMAIN}/grafana)
- **Loki**: http://localhost:3100 (API only)
- **Jaeger**: http://localhost:16686 (or https://${DOMAIN}/jaeger)
- **Alertmanager**: http://localhost:9093

Default Grafana credentials:
- Username: `admin`
- Password: `${GRAFANA_ADMIN_PASSWORD}`

## Grafana Dashboards

Five pre-configured production dashboards are automatically provisioned:

### 1. JVM Metrics Dashboard
**UID**: `jvm-metrics-prod`

Monitors Java Virtual Machine performance:
- Heap and non-heap memory usage with thresholds
- Garbage collection rates and pause durations
- Thread statistics (live, daemon, peak)
- Memory pool breakdown
- Class loading statistics

**Key Metrics**:
- Heap usage gauge with 75%/85%/95% thresholds
- GC pause duration by collection type
- Thread count trends
- Memory pool allocation

### 2. HTTP Metrics Dashboard
**UID**: `http-metrics-prod`

Monitors HTTP request performance:
- Request rates by endpoint
- Latency percentiles (P50, P95, P99)
- Error rates (4xx, 5xx) by endpoint
- Response status code distribution
- Total request counts

**Key Metrics**:
- P95 latency with 0.5s/1s/2s thresholds
- 5xx error rate with 1%/5%/10% thresholds
- Request rate by URI
- Error rate breakdown

### 3. Database Connection Pool Dashboard
**UID**: `database-pool-prod`

Monitors HikariCP and PostgreSQL performance:
- Connection pool usage (active, idle, pending)
- Connection acquisition times
- Database transaction rates
- Cache hit ratios
- Long-running transactions
- Deadlock detection

**Key Metrics**:
- Pool usage gauge with 75%/90%/95% thresholds
- P95 connection acquire time
- Transaction commit/rollback rates
- Database cache hit ratio

### 4. System Resources Dashboard
**UID**: `system-resources-prod`

Monitors infrastructure resources:
- CPU usage by mode (user, system, iowait)
- System load averages (1m, 5m, 15m)
- Memory usage (total, used, available, buffers, cached)
- Swap usage
- Disk space and I/O operations
- Network bandwidth and errors

**Key Metrics**:
- CPU usage gauge with 75%/85%/95% thresholds
- Memory usage gauge with same thresholds
- Disk usage gauge
- Network throughput by interface

### 5. Logs Overview Dashboard
**UID**: `logs-overview`

Centralized log visualization with Loki:
- Log volume by level (time series)
- Authentication failures
- AI agent execution errors
- Rate limit violations
- Database connectivity issues
- Vault connectivity problems
- Unhandled exceptions
- Security events (90-day retention)

**Key Features**:
- Structured JSON log parsing
- MDC context (correlationId, runId, userId)
- Tiered retention (7d INFO, 30d WARN/ERROR, 90d security)
- LogQL query examples in panel descriptions

**Common Queries**:
```logql
# All errors
{service="ai-orchestrator"} | json | level="ERROR"

# Track workflow
{service="ai-orchestrator"} | json | runId="<run-id>"

# User activity
{service="ai-orchestrator"} | json | userId="user@example.com"
```

## Alerting Rules

### High Error Rate (>5%)
- **Severity**: Critical
- **Duration**: 5 minutes
- **Action**: Investigate service logs and recent deployments

### Database Connection Pool Exhaustion (>90%)
- **Severity**: Critical
- **Duration**: 3 minutes
- **Action**: Scale connection pool or investigate connection leaks

### High Memory Usage (>85%)
- **Severity**: Warning
- **Duration**: 10 minutes
- **Action**: Review memory-intensive operations, consider scaling

### Critical Memory Usage (>95%)
- **Severity**: Critical
- **Duration**: 5 minutes
- **Action**: Immediate intervention required, may need restart

### Deployment Failure
- **Severity**: Critical
- **Duration**: 2 minutes
- **Action**: Check deployment logs, rollback if necessary

### JVM Heap Exhaustion (>85%)
- **Severity**: Warning at 85%, Critical at 95%
- **Action**: Review heap allocation, tune JVM settings, scale if needed

### Log-Based Alerts (Loki)

#### Database Connection Failures (>5/sec)
- **Severity**: Critical
- **Duration**: 2 minutes
- **Action**: Check database connectivity, connection pool settings

#### Vault Connectivity Loss
- **Severity**: Critical
- **Duration**: 2 minutes
- **Action**: Verify Vault is running, check network connectivity

#### Unhandled Exceptions (>10/sec)
- **Severity**: Critical
- **Duration**: 3 minutes
- **Action**: Review application logs, investigate exception root cause

#### Authentication Failures Spike (>2/sec)
- **Severity**: Warning
- **Duration**: 5 minutes
- **Action**: Possible brute force attack, review authentication logs

#### Rate Limit Violations (>5/sec)
- **Severity**: Warning
- **Duration**: 5 minutes
- **Action**: Identify clients hitting rate limits, adjust limits if needed

## Configuration Files

### Prometheus Configuration
**File**: `prometheus-prod.yml`

Scrape configurations:
- AI Orchestrator: every 10s from port 8080
- PostgreSQL Exporter: every 15s from port 9187
- Node Exporter: every 15s from port 9100
- Prometheus self-monitoring: every 15s

Retention: 90 days

### Alert Rules
**Files**: 
- `alerts-prod.yml` (production-specific)
- `../../monitoring/alerts.yml` (shared base alerts with log-based rules)

Alert groups:
- `production_critical_alerts`: High error rates, pool exhaustion, memory
- `production_jvm_alerts`: Heap usage, GC pressure
- `production_database_alerts`: Connection issues, slow queries
- `production_http_alerts`: Latency, error rates
- `production_system_alerts`: CPU, memory, disk
- `production_postgres_alerts`: Database health
- `orchestrator_logs`: Log-based alerts from Loki

### Loki Configuration
**File**: `config/loki-config.yml`

Features:
- BoltDB shipper storage backend
- 90-day retention with compaction
- Log retention policies by level
- Ruler for log-based alerting
- Query limits and rate limiting

Storage:
- Chunks directory: `/loki/chunks`
- Index directory: `/loki/boltdb-shipper-active`
- Rules directory: `/loki/rules`

### Promtail Configuration
**File**: `config/promtail-config.yml`

Scrape jobs:
- `ai-orchestrator`: Docker containers with JSON log parsing
- `system`: System syslog
- `docker`: General Docker container logs

Features:
- Automatic MDC context extraction (correlationId, runId, userId)
- Retention label assignment based on level/category
- JSON log parsing with structured field extraction

### Loki Alert Rules
**File**: `config/loki-rules.yml`

Log-based alert groups:
- `log_alerts_critical`: Database failures, Vault issues, exceptions
- `log_alerts_security`: Auth failures, rate limits
- `log_alerts_application`: Agent errors, memory pressure
- `log_alerts_infrastructure`: SSL/TLS, circuit breakers

### PostgreSQL Exporter Queries
**File**: `postgres-exporter-queries.yaml`

Custom queries for:
- Connection state tracking
- Transaction metrics
- Cache hit ratios
- Table statistics
- Long-running queries
- Deadlock detection

### Alertmanager Configuration
**File**: `alertmanager-prod.yml`

Routing:
- Critical alerts → immediate notification (10s group wait)
- Database alerts → DBA team (30s group wait)
- Performance alerts → 1m group wait
- Resource alerts → 2m group wait

Inhibition rules:
- Critical alerts suppress warnings
- Service down suppresses related alerts

## Monitoring Best Practices

### Threshold Tuning

Current thresholds are conservative defaults. Adjust based on your workload:

**Memory**:
- Warning: 85% (10 minute sustained)
- Critical: 95% (5 minute sustained)

**Error Rates**:
- HTTP 5xx: 5% (5 minute sustained)
- Connection pool: 90% (3 minute sustained)

**Latency**:
- P95: 2s warning, 5s critical
- P99: 5s warning, 10s critical

### Scaling Considerations

Monitor these metrics to determine when to scale:

1. **Connection Pool**: Consistently >75% → increase pool size
2. **Memory**: Frequently >85% → add more RAM or scale horizontally
3. **CPU**: Average >70% → scale horizontally
4. **Request Rate**: Sustained high rates → add replicas

### Alert Fatigue Prevention

1. Group related alerts with appropriate `group_wait` intervals
2. Use inhibition rules to suppress cascading alerts
3. Set appropriate `for` durations to avoid transient spikes
4. Review and tune alert thresholds based on actual patterns

## Troubleshooting

### Prometheus Not Scraping Targets

1. Check target health: http://localhost:9090/targets
2. Verify network connectivity between containers
3. Check service ports are exposed correctly
4. Review Prometheus logs: `docker logs orchestrator-prometheus-prod`

### No Logs in Loki

1. Check Promtail is running: `docker ps | grep promtail`
2. Check Promtail logs: `docker logs promtail-prod --tail 50`
3. Verify Loki connectivity: `docker exec promtail-prod wget -O- http://loki-prod:3100/ready`
4. Check application JSON logs: `docker logs ai-orchestrator | head -1 | jq .`
5. Verify Promtail targets: `curl http://localhost:9080/targets`
6. Check Loki ingestion: `curl http://localhost:3100/loki/api/v1/labels | jq`

### Loki Disk Space Issues

1. Check disk usage: `docker exec loki-prod du -sh /loki/*`
2. Trigger compaction: `curl -X POST http://localhost:3100/loki/api/v1/compact`
3. Reduce retention in `config/loki-config.yml` and restart
4. Check for stuck compaction: `docker logs loki-prod | grep compactor`

### Slow Log Queries

1. Always use label filters: `{service="ai-orchestrator"}`
2. Limit time ranges: `[5m]` instead of `[24h]`
3. Avoid unbounded queries
4. Check Loki metrics: `curl http://localhost:3100/metrics | grep loki_request_duration`

### Missing Metrics

1. Verify Spring Boot Actuator is enabled: `/actuator/prometheus`
2. Check HikariCP metrics are exposed
3. Ensure Node Exporter has host filesystem access
4. Verify PostgreSQL Exporter can connect to database

### Grafana Dashboards Not Loading

1. Check datasource configuration: Settings → Data Sources
2. Verify Prometheus URL: `http://prometheus:9090`
3. Test connection from Grafana
4. Check dashboard provisioning logs

### High Cardinality Issues

If Prometheus is consuming excessive memory:

1. Review metric cardinality: http://localhost:9090/api/v1/status/tsdb
2. Add `metric_relabel_configs` to drop high-cardinality labels
3. Reduce scrape intervals for high-volume metrics
4. Implement metric retention policies

## Maintenance

### Backup Grafana Dashboards

```bash
# Export dashboards
docker exec orchestrator-grafana-prod grafana-cli admin export-dashboard <dashboard-uid>

# Backup Grafana database
docker exec orchestrator-grafana-prod sqlite3 /var/lib/grafana/grafana.db ".backup /var/lib/grafana/backup.db"
```

### Backup Prometheus Data

```bash
# Stop Prometheus
docker-compose -f docker-compose-monitoring.yml stop prometheus

# Backup data directory
docker run --rm -v prometheus-prod-data:/data -v $(pwd):/backup alpine tar czf /backup/prometheus-backup.tar.gz /data

# Start Prometheus
docker-compose -f docker-compose-monitoring.yml start prometheus
```

### Backup Loki Data

```bash
# Stop Loki
docker-compose -f docker-compose-monitoring.yml stop loki

# Backup data directory
docker run --rm -v loki-prod-data:/data -v $(pwd):/backup alpine tar czf /backup/loki-backup.tar.gz /data

# Start Loki
docker-compose -f docker-compose-monitoring.yml start loki
```

## Further Reading

- **Complete Observability Guide**: `../../docs/OBSERVABILITY.md`
- **Logging Quick Start**: `LOGGING_QUICKSTART.md`
- **Prometheus Metrics**: `../../docs/PROMETHEUS_METRICS.md`
- **WebSocket Monitoring**: `../../docs/WEBSOCKET_MONITORING.md`
- **Loki Documentation**: https://grafana.com/docs/loki/latest/
- **LogQL Reference**: https://grafana.com/docs/loki/latest/logql/
- **Prometheus Documentation**: https://prometheus.io/docs/
- **Grafana Documentation**: https://grafana.com/docs/

### Update Alert Rules

1. Edit `alerts-prod.yml`
2. Reload Prometheus configuration:
   ```bash
   curl -X POST http://localhost:9090/-/reload
   ```

## Security Considerations

1. **Change default Grafana password immediately**
2. Enable HTTPS for all monitoring endpoints
3. Restrict access to monitoring stack (firewall rules)
4. Use read-only database credentials for PostgreSQL Exporter
5. Enable authentication for Prometheus and Alertmanager
6. Regularly update container images for security patches

## Integration with External Services

### Slack Notifications

Edit `alertmanager-prod.yml`:

```yaml
slack_configs:
  - api_url: '${SLACK_WEBHOOK_URL}'
    channel: '#alerts-production'
    title: '{{ .GroupLabels.alertname }}'
    text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

### PagerDuty Integration

```yaml
pagerduty_configs:
  - service_key: '${PAGERDUTY_SERVICE_KEY}'
    description: '{{ .GroupLabels.alertname }}: {{ .CommonAnnotations.summary }}'
```

### Email Notifications

Configure SMTP in `alertmanager-prod.yml` global section.

## Metrics Reference

### Spring Boot Metrics
- `jvm_memory_used_bytes`: JVM memory usage by area/pool
- `jvm_gc_pause_seconds_*`: GC pause metrics
- `jvm_threads_*`: Thread statistics
- `http_server_requests_seconds_*`: HTTP request metrics

### HikariCP Metrics
- `hikaricp_connections_active`: Active connections
- `hikaricp_connections_idle`: Idle connections
- `hikaricp_connections_pending`: Pending connections
- `hikaricp_connections_max`: Maximum pool size
- `hikaricp_connections_acquire_seconds`: Connection acquisition time

### Node Exporter Metrics
- `node_cpu_seconds_total`: CPU time by mode
- `node_memory_*`: Memory statistics
- `node_filesystem_*`: Filesystem statistics
- `node_network_*`: Network statistics

### PostgreSQL Metrics
- `pg_stat_database_*`: Database-level statistics
- `pg_stat_activity_*`: Connection activity
- `pg_stat_user_tables_*`: Table-level statistics

## Support

For issues or questions:
1. Check Prometheus query examples: http://localhost:9090/graph
2. Review Grafana documentation: https://grafana.com/docs/
3. Consult runbooks: https://docs.atlasia.ai/runbooks/
